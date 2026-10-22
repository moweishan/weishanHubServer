package com.ruoyi.project.notification.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 只解析 Mapper XML，不连接数据库：防止 XML 语法/命名空间/语句 ID 与接口脱节。
 */
class NotificationMapperXmlTest
{
    private Configuration configuration;

    @BeforeEach
    void setUp()
    {
        configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.project.notification.domain");
    }

    @Test
    void allNotificationMapperXmlCanBeParsedAndContainsExpectedStatements() throws Exception
    {
        parse("mybatis/notification/NotificationChannelMapper.xml");
        parse("mybatis/notification/NotificationCredentialMapper.xml");
        parse("mybatis/notification/NotificationRecordMapper.xml");
        parse("mybatis/notification/NotificationRecordChannelMapper.xml");

        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.notification.mapper.NotificationChannelMapper.selectEnabledByRouteCodesForUpdate"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.notification.mapper.NotificationRecordMapper.markFirstSent"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper.claimChannel"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.notification.mapper.NotificationCredentialMapper.selectCredentialByKeyHash"));
    }

    private void parse(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder builder = new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments());
            builder.parse();
        }
    }
}
