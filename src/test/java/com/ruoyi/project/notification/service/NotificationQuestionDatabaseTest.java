package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.*;
import com.ruoyi.project.notification.mapper.*;
import com.ruoyi.project.system.service.ISysConfigService;

/** 显式启用的本地 MySQL 验证。只提交增量建表，所有合成业务记录均回滚，不启动发送器。 */
@EnabledIfSystemProperty(named = "weishanhub.integration", matches = "true")
class NotificationQuestionDatabaseTest
{
    @Test
    void realMappersCreateQuestionSubmitAndApplyWithRollback() throws Exception
    {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("config/application-local.yml"));
        Properties local = yaml.getObject();
        String root = "spring.datasource.druid.master.";
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(local.getProperty(root + "url", "jdbc:mysql://localhost:3306/weishanhub?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai"));
        ds.setUsername(local.getProperty(root + "username", System.getenv("DB_USERNAME")));
        ds.setPassword(local.getProperty(root + "password", System.getenv("DB_PASSWORD")));
        new ResourceDatabasePopulator(new FileSystemResource("sql/20261024_add_notification_question.sql")).execute(ds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setTypeAliasesPackage("com.ruoyi.project.notification.domain");
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mybatis/notification/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        factory.setConfiguration(configuration);
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory.getObject());
        NotificationRecordMapper records = sessions.getMapper(NotificationRecordMapper.class);
        NotificationQuestionMapper questions = sessions.getMapper(NotificationQuestionMapper.class);
        NotificationCredentialMapper credentials = sessions.getMapper(NotificationCredentialMapper.class);
        NotificationChannelMapper channels = sessions.getMapper(NotificationChannelMapper.class);
        NotificationRecordChannelMapper children = sessions.getMapper(NotificationRecordChannelMapper.class);
        NotificationKeyService keys = new NotificationKeyService();
        NotificationProperties properties = new NotificationProperties();
        properties.setMasterKey(UUID.randomUUID().toString());
        properties.setPublicBaseUrl("http://localhost");
        NotificationCryptoService crypto = new NotificationCryptoService(properties);
        NotificationCredentialService auth = new NotificationCredentialService(credentials, keys);
        NotificationReceiveService receiver = new NotificationReceiveService(auth, records,
                new NotificationRecordWriter(channels, records, children), crypto, keys,
                new NotificationUrlService(properties, mock(ISysConfigService.class)), new NotificationRateLimitService(properties));
        ObjectMapper json = new ObjectMapper();
        NotificationDetailService detail = new NotificationDetailService(records, keys, Clock.systemUTC());
        NotificationQuestionService service = new NotificationQuestionService(questions, records, auth, receiver,
                detail, keys, json);
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction.execute(status -> {
            status.setRollbackOnly();
            try
            {
                String apiKey = keys.generateApiKey();
                NotificationCredential credential = new NotificationCredential();
                credential.setCredentialName("WH_SYNTHETIC_ROLLBACK");
                credential.setKeyHash(keys.sha256Hex(apiKey));
                credential.setKeyPrefix("test");
                credential.setEnabled("0");
                credentials.insertCredential(credential);
                String route = "test_" + UUID.randomUUID().toString().replace("-", "");
                NotificationChannel channel = new NotificationChannel();
                channel.setRouteCode(route);
                channel.setChannelName("WH_SYNTHETIC_ROLLBACK");
                channel.setProvider("WECOM_APP");
                channel.setCorpId("synthetic");
                channel.setAgentId(1);
                channel.setToUser("synthetic");
                channel.setSecretCipher(crypto.encrypt("synthetic"));
                channel.setEnabled("0");
                channels.insertChannel(channel);
                NotificationQuestionRequest request = new NotificationQuestionRequest();
                request.setRequestId(UUID.randomUUID().toString());
                request.setMessageType("CARD");
                request.setTitle("合成题，不发送");
                request.setSummary("本地数据库验证");
                request.setDetail("# 原生选择");
                request.setInstanceId("synthetic");
                request.setRouteCodes(Collections.singletonList(route));
                request.setQuestions(json.readTree("[{\"id\":\"q\",\"question\":\"choose\",\"options\":[{\"label\":\"B\"}]}]"));
                NotificationAcceptResponse accepted = service.create(apiKey, request);
                assertTrue(service.create(apiKey, request).isDuplicate());
                assertEquals("WAITING", service.poll(apiKey, request.getRequestId()).path("state").asText());
                String token = crypto.decrypt(records.selectRecordById(accepted.getRecordId()).getTokenCipher());
                Date sent = new Date();
                records.markFirstSent(accepted.getRecordId(), sent, new Date(sent.getTime() + 3600000));
                JsonNode answer = json.readTree("{\"answers\":[{\"id\":\"q\",\"selected\":[\"B\"]}]}");
                assertEquals("ANSWERED", service.publicQuestion(token, new NotificationAccessContext(false, false, "MicroMessenger"), answer).path("state").asText());
                JsonNode receipt = json.createObjectNode().put("state", "APPLIED").put("source", "REMOTE").set("answer", answer);
                assertEquals("APPLIED", service.settle(apiKey, request.getRequestId(), receipt).path("state").asText());
                assertEquals("B", service.poll(apiKey, request.getRequestId()).path("answer").path("answers").get(0).path("selected").get(0).asText());
                if (Boolean.getBoolean("weishanhub.browserIntegration"))
                    NotificationQuestionBrowserProbe.run(service, detail, new NotificationRateLimitService(properties), records, json, apiKey, route);
            }
            catch (Exception e) { throw new IllegalStateException("本地问题闭环验证失败", e); }
            return null;
        });
    }
}
