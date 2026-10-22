package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.NotificationChannel;
import com.ruoyi.project.notification.domain.NotificationChannelRequest;
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationCredentialCreated;
import com.ruoyi.project.notification.domain.NotificationCredentialRequest;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.mapper.NotificationChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationCredentialMapper;
import com.ruoyi.project.system.service.ISysConfigService;

/**
 * 凭据泄漏、主密钥缺失、公网地址、API Key/票据摘要与限流边界。
 */
class NotificationSecurityTest
{
    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void sdkRawLogsAreDisabledToPreventTokenAndBodyLeakage()
    {
        // SDK 会在 WARN/DEBUG 输出完整请求 URL（含 access_token）和消息 JSON，必须保持关闭
        Logger sdkLogger = LoggerFactory.getLogger("me.chanjar.weixin.cp.api.impl.BaseWxCpServiceImpl");
        assertFalse(sdkLogger.isWarnEnabled());
        assertFalse(sdkLogger.isDebugEnabled());
        assertFalse(sdkLogger.isInfoEnabled());
    }

    @Test
    void cryptoRoundTripUsesRandomIvAndNeverReturnsPlaintext()
    {
        NotificationProperties properties = new NotificationProperties();
        properties.setMasterKey(MASTER_KEY);
        NotificationCryptoService crypto = new NotificationCryptoService(properties);

        String first = crypto.encrypt("wecom-secret-value");
        String second = crypto.encrypt("wecom-secret-value");

        assertNotEquals(first, second);
        assertFalse(first.contains("wecom-secret-value"));
        assertEquals("wecom-secret-value", crypto.decrypt(first));
        assertEquals("wecom-secret-value", crypto.decrypt(second));
    }

    @Test
    void missingOrWrongMasterKeyDisablesOperationsWithoutLeakingInput()
    {
        NotificationProperties properties = new NotificationProperties();
        NotificationCryptoService crypto = new NotificationCryptoService(properties);
        assertFalse(crypto.isAvailable());
        ServiceException missing = assertThrows(ServiceException.class, () -> crypto.encrypt("secret"));
        assertEquals(503, missing.getCode().intValue());
        assertFalse(missing.getMessage().contains("secret"));

        properties.setMasterKey(MASTER_KEY);
        String cipher = crypto.encrypt("top-secret");
        NotificationProperties other = new NotificationProperties();
        other.setMasterKey("ffffffffffffffffffffffffffffffff");
        NotificationCryptoService wrong = new NotificationCryptoService(other);
        assertThrows(ServiceException.class, () -> wrong.decrypt(cipher));
    }

    @Test
    void apiKeysAndTokensHaveEnoughEntropyAndOnlyHashesAreStored()
    {
        NotificationKeyService keyService = new NotificationKeyService();
        String first = keyService.generateApiKey();
        String second = keyService.generateApiKey();
        assertNotEquals(first, second);
        assertTrue(first.startsWith(NotificationKeyService.API_KEY_PREFIX));
        assertEquals(64, keyService.sha256Hex(first).length());
        assertNotEquals(first, keyService.sha256Hex(first));

        String token = keyService.generateDetailToken();
        assertTrue(keyService.isDetailTokenFormat(token));
        assertEquals(64, keyService.sha256Hex(token).length());
    }

    @Test
    void publicBaseUrlRequiresHttpsExceptLocalhost()
    {
        NotificationProperties properties = new NotificationProperties();
        properties.setPublicBaseUrl("https://hub.example.com/");
        ISysConfigService configService = org.mockito.Mockito.mock(ISysConfigService.class);
        NotificationUrlService service = new NotificationUrlService(properties, configService);
        assertEquals("https://hub.example.com", service.resolveBaseUrl());
        assertEquals("https://hub.example.com/notification/detail/token", service.buildDetailUrl("token"));

        properties.setPublicBaseUrl("http://localhost:8080");
        assertEquals("http://localhost:8080", service.resolveBaseUrl());

        properties.setPublicBaseUrl("http://hub.example.com");
        assertEquals(503, assertThrows(ServiceException.class, service::resolveBaseUrl).getCode().intValue());

        properties.setPublicBaseUrl("");
        assertEquals(503, assertThrows(ServiceException.class, service::resolveBaseUrl).getCode().intValue());
    }

    @Test
    void jsonResponsesNeverExposeSecretCipherHashOrTokenCipher() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();

        NotificationChannel channel = new NotificationChannel();
        channel.setRouteCode("order");
        channel.setSecretCipher("v1:super-secret-cipher");
        String channelJson = mapper.writeValueAsString(channel);
        assertFalse(channelJson.contains("super-secret-cipher"));
        assertFalse(channelJson.contains("secretCipher"));
        assertTrue(channelJson.contains("\"secretConfigured\":true"));

        NotificationCredential credential = new NotificationCredential();
        credential.setCredentialName("生产");
        credential.setKeyHash("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");
        String credentialJson = mapper.writeValueAsString(credential);
        assertFalse(credentialJson.contains("abcdefabcdefabcdefabcdefabcdefabcdef"));
        assertFalse(credentialJson.contains("keyHash"));

        NotificationRecord record = new NotificationRecord();
        record.setTitle("标题");
        record.setTokenHash("token-hash");
        record.setTokenCipher("v1:token-cipher");
        record.setContentHash("content-hash");
        String recordJson = mapper.writeValueAsString(record);
        assertFalse(recordJson.contains("token-hash"));
        assertFalse(recordJson.contains("token-cipher"));
        assertFalse(recordJson.contains("content-hash"));
    }

    @Test
    void perIpRateLimitRejectsThirdRequestInWindow()
    {
        NotificationProperties properties = new NotificationProperties();
        properties.getRateLimit().setPerIp(2);
        properties.getRateLimit().setWindowSeconds(60);
        NotificationRateLimitService service = new NotificationRateLimitService(properties);

        service.checkIp("127.0.0.1");
        service.checkIp("127.0.0.1");
        ServiceException exception = assertThrows(ServiceException.class, () -> service.checkIp("127.0.0.1"));
        assertEquals(429, exception.getCode().intValue());
        service.checkIp("127.0.0.2");
    }

    @Test
    void channelSaveIsDisabledWhenMasterKeyMissing()
    {
        NotificationChannelMapper mapper = org.mockito.Mockito.mock(NotificationChannelMapper.class);
        NotificationProperties properties = new NotificationProperties();
        NotificationCryptoService crypto = new NotificationCryptoService(properties);
        NotificationSenderFactory senderFactory = org.mockito.Mockito.mock(NotificationSenderFactory.class);
        when(senderFactory.supports("WECOM_APP")).thenReturn(true);
        NotificationChannelService service = new NotificationChannelService(mapper, crypto, senderFactory);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.createChannel(channelRequest(), "admin"));
        assertEquals(503, exception.getCode().intValue());
        verify(mapper, never()).insertChannel(any(NotificationChannel.class));
    }

    @Test
    void validWeComSecretWithoutMasterKeyReportsConfigurationHint()
    {
        String wecomSecret = "valid-enterprise-secret-fixture";
        NotificationProperties properties = new NotificationProperties();
        NotificationCryptoService crypto = new NotificationCryptoService(properties);

        ServiceException exception = assertThrows(ServiceException.class, () -> crypto.encrypt(wecomSecret));
        assertEquals(503, exception.getCode().intValue());
        assertTrue(exception.getMessage().contains("notification.master-key"));
        assertTrue(exception.getMessage().contains("NOTIFICATION_MASTER_KEY"));
        assertTrue(exception.getMessage().contains("企业微信应用 Secret"));
        assertTrue(exception.getMessage().contains("16"));
        assertTrue(exception.getMessage().contains("重启"));
        assertFalse(exception.getMessage().contains(wecomSecret));

        properties.setMasterKey("regression-only-random-sample-key-0123456789");
        String cipher = crypto.encrypt(wecomSecret);
        assertTrue(cipher.startsWith("v1:"));
        assertEquals(wecomSecret, crypto.decrypt(cipher));
        assertFalse(cipher.contains(wecomSecret));
    }

    @Test
    void channelSaveEncryptsSecretAndNormalizesRecipients()
    {
        NotificationChannelMapper mapper = org.mockito.Mockito.mock(NotificationChannelMapper.class);
        NotificationProperties properties = new NotificationProperties();
        properties.setMasterKey(MASTER_KEY);
        NotificationCryptoService crypto = new NotificationCryptoService(properties);
        NotificationSenderFactory senderFactory = org.mockito.Mockito.mock(NotificationSenderFactory.class);
        when(senderFactory.supports("WECOM_APP")).thenReturn(true);
        when(mapper.insertChannel(any(NotificationChannel.class))).thenAnswer(invocation -> {
            NotificationChannel channel = invocation.getArgument(0);
            channel.setChannelId(5L);
            return 1;
        });
        NotificationChannelService service = new NotificationChannelService(mapper, crypto, senderFactory);
        NotificationChannelRequest request = channelRequest();
        request.setToUser("u1, u2；u1");

        assertEquals(5L, service.createChannel(request, "admin").longValue());
        verify(mapper).insertChannel(org.mockito.ArgumentMatchers.argThat(channel ->
                channel.getSecretCipher() != null
                        && channel.getSecretCipher().startsWith("v1:")
                        && "secret".equals(crypto.decrypt(channel.getSecretCipher()))
                        && !"secret".equals(channel.getSecretCipher())
                        && "u1|u2".equals(channel.getToUser())));
    }

    @Test
    void credentialCreateStoresHashOnlyAndResetRotatesKey()
    {
        NotificationCredentialMapper mapper = org.mockito.Mockito.mock(NotificationCredentialMapper.class);
        NotificationKeyService keyService = new NotificationKeyService();
        NotificationCredentialService service = new NotificationCredentialService(mapper, keyService);
        when(mapper.insertCredential(any(NotificationCredential.class))).thenAnswer(invocation -> {
            NotificationCredential credential = invocation.getArgument(0);
            credential.setCredentialId(9L);
            return 1;
        });

        NotificationCredentialRequest request = new NotificationCredentialRequest();
        request.setCredentialName("生产调用方");
        request.setEnabled("0");
        NotificationCredentialCreated created = service.createCredential(request, "admin");

        assertNotNull(created.getApiKey());
        assertTrue(created.getApiKey().startsWith("ntf_"));
        verify(mapper).insertCredential(org.mockito.ArgumentMatchers.argThat(credential ->
                credential.getKeyHash() != null
                        && credential.getKeyHash().length() == 64
                        && !created.getApiKey().equals(credential.getKeyHash())));

        NotificationCredential existing = new NotificationCredential();
        existing.setCredentialId(9L);
        existing.setCredentialName("生产调用方");
        existing.setEnabled("0");
        when(mapper.selectCredentialById(9L)).thenReturn(existing);
        NotificationCredentialCreated reset = service.resetCredential(9L, "admin");
        assertNotEquals(created.getApiKey(), reset.getApiKey());
        verify(mapper).updateKey(org.mockito.ArgumentMatchers.eq(9L), any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq("admin"), any(Date.class));
    }

    @Test
    void disabledCredentialIsRejected()
    {
        NotificationCredentialMapper mapper = org.mockito.Mockito.mock(NotificationCredentialMapper.class);
        NotificationCredentialService service = new NotificationCredentialService(mapper, new NotificationKeyService());
        NotificationCredential disabled = new NotificationCredential();
        disabled.setCredentialId(3L);
        disabled.setEnabled("1");
        when(mapper.selectCredentialByKeyHash(any(String.class))).thenReturn(disabled);
        String key = new NotificationKeyService().generateApiKey();
        ServiceException exception = assertThrows(ServiceException.class, () -> service.authenticate(key));
        assertEquals(401, exception.getCode().intValue());
    }

    private NotificationChannelRequest channelRequest()
    {
        NotificationChannelRequest request = new NotificationChannelRequest();
        request.setRouteCode("order");
        request.setChannelName("订单");
        request.setCorpId("corp");
        request.setAgentId(1001);
        request.setToUser("user");
        request.setSecret("secret");
        request.setEnabled("0");
        return request;
    }
}
