package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.controller.NotificationChannelController;
import com.ruoyi.project.notification.domain.NotificationDetailUrlRequest;
import com.ruoyi.project.notification.domain.NotificationDetailUrlSettings;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.system.domain.SysConfig;
import com.ruoyi.project.system.service.ISysConfigService;

/**
 * 统一详情页地址：数据库优先/私密配置回退、地址规范化、仅保存固定 key、控制器权限与审计。
 */
class NotificationDetailUrlServiceTest
{
    private ISysConfigService configService;

    private NotificationProperties properties;

    private NotificationUrlService urlService;

    private NotificationDetailUrlService service;

    @BeforeEach
    void setUp()
    {
        configService = org.mockito.Mockito.mock(ISysConfigService.class);
        properties = new NotificationProperties();
        urlService = new NotificationUrlService(properties, configService);
        service = new NotificationDetailUrlService(configService, urlService, properties);
    }

    @Test
    void databaseValueWinsAndBlankFallsBackToPrivateConfig()
    {
        properties.setPublicBaseUrl("https://fallback.example.com/");
        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY))
                .thenReturn("https://db.example.com/sub/");

        assertEquals("https://db.example.com/sub", urlService.resolveBaseUrl());
        assertEquals("https://fallback.example.com", urlService.resolveLegacyBaseUrl());

        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY)).thenReturn("");
        assertEquals("https://fallback.example.com", urlService.resolveBaseUrl());
    }

    @Test
    void recordWithoutSnapshotOnlyUsesLegacyPrivateConfig()
    {
        properties.setPublicBaseUrl("https://legacy.example.com");
        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY))
                .thenReturn("https://new-db.example.com");

        NotificationRecord snapshot = new NotificationRecord();
        snapshot.setDetailBaseUrl("https://snap.example.com/");
        assertEquals("https://snap.example.com", urlService.resolveRecordBaseUrl(snapshot));

        NotificationRecord legacy = new NotificationRecord();
        assertEquals("https://legacy.example.com", urlService.resolveRecordBaseUrl(legacy));

        properties.setPublicBaseUrl("");
        assertNull(urlService.resolveRecordBaseUrlOrNull(legacy));
    }

    @Test
    void validUrlsAreNormalizedAndInvalidUrlsAreRejected()
    {
        assertEquals("https://hub.example.com", urlService.normalize("https://hub.example.com/"));
        assertEquals("https://hub.example.com/app", urlService.normalize("https://hub.example.com/app/"));
        assertEquals("http://localhost:8080", urlService.normalize("http://localhost:8080/"));
        assertEquals("http://127.0.0.1:8081/sub", urlService.normalize("http://127.0.0.1:8081/sub"));
        assertEquals("http://[::1]:8080", urlService.normalize("http://[::1]:8080"));

        assertInvalid("http://hub.example.com");
        assertInvalid("https://user:pass@hub.example.com");
        assertInvalid("https://hub.example.com?a=1");
        assertInvalid("https://hub.example.com#part");
        assertInvalid("https://hub.example.com/?");
        assertInvalid("ftp://hub.example.com");
        assertInvalid("https://hub.example.com/a b");
        assertInvalid("https://hub.example.com/\n");
        assertInvalid("");
        assertInvalid("https://hub.example.com/" + longPath(500));
    }

    @Test
    void invalidDatabaseValueDoesNotSilentlyFallBack()
    {
        properties.setPublicBaseUrl("https://fallback.example.com");
        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY))
                .thenReturn("http://hub.example.com");

        ServiceException exception = assertThrows(ServiceException.class, urlService::resolveBaseUrl);
        assertEquals(503, exception.getCode().intValue());
    }

    @Test
    void settingsExposeEffectiveValueAndSource()
    {
        properties.setPublicBaseUrl("https://fallback.example.com");
        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY)).thenReturn("");

        NotificationDetailUrlSettings fallback = service.getSettings();
        assertEquals("CONFIG", fallback.getSource());
        assertEquals("https://fallback.example.com", fallback.getEffectiveUrl());

        when(configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY))
                .thenReturn("https://db.example.com/");
        NotificationDetailUrlSettings database = service.getSettings();
        assertEquals("DATABASE", database.getSource());
        assertEquals("https://db.example.com", database.getEffectiveUrl());
        assertEquals("https://db.example.com/", database.getConfiguredValue());
    }

    @Test
    void saveUpdatesOnlyTheFixedConfigKey()
    {
        SysConfig exact = new SysConfig();
        exact.setConfigId(21L);
        exact.setConfigName("通知详情页地址");
        exact.setConfigKey(NotificationUrlService.CONFIG_KEY);
        exact.setConfigValue("");
        SysConfig decoy = new SysConfig();
        decoy.setConfigId(22L);
        decoy.setConfigKey(NotificationUrlService.CONFIG_KEY + "-backup");
        decoy.setConfigValue("https://wrong.example.com");
        when(configService.selectConfigList(any(SysConfig.class))).thenReturn(Arrays.asList(decoy, exact));
        when(configService.updateConfig(any(SysConfig.class))).thenReturn(1);

        service.save("https://hub.example.com/app/", "admin");

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configService).updateConfig(captor.capture());
        SysConfig saved = captor.getValue();
        assertEquals(Long.valueOf(21L), saved.getConfigId());
        assertEquals(NotificationUrlService.CONFIG_KEY, saved.getConfigKey());
        assertEquals("https://hub.example.com/app", saved.getConfigValue());
        assertEquals("admin", saved.getUpdateBy());
        assertNotNull(saved.getUpdateTime());
        verify(configService, never()).insertConfig(any(SysConfig.class));
    }

    @Test
    void saveInsertsSeedWhenKeyDoesNotExistAndRejectsInvalidUrl()
    {
        when(configService.selectConfigList(any(SysConfig.class))).thenReturn(Collections.emptyList());
        when(configService.insertConfig(any(SysConfig.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> service.save("http://hub.example.com", "admin"));
        verify(configService, never()).insertConfig(any(SysConfig.class));

        service.save("https://hub.example.com", "admin");

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configService).insertConfig(captor.capture());
        assertEquals(NotificationUrlService.CONFIG_KEY, captor.getValue().getConfigKey());
        assertEquals("https://hub.example.com", captor.getValue().getConfigValue());
        verify(configService, never()).updateConfig(any(SysConfig.class));
    }

    @Test
    void detailUrlEndpointsRequireListAndEditPermissionsWithAudit() throws Exception
    {
        Method get = NotificationChannelController.class.getMethod("getDetailUrl");
        assertEquals("@ss.hasPermi('notification:channel:list')",
                get.getAnnotation(PreAuthorize.class).value());

        Method put = NotificationChannelController.class.getMethod("updateDetailUrl",
                NotificationDetailUrlRequest.class);
        assertEquals("@ss.hasPermi('notification:channel:edit')",
                put.getAnnotation(PreAuthorize.class).value());
        Log log = put.getAnnotation(Log.class);
        assertNotNull(log);
        assertEquals("通知详情页地址", log.title());
        assertEquals(BusinessType.UPDATE, log.businessType());
    }

    private void assertInvalid(String url)
    {
        ServiceException exception = assertThrows(ServiceException.class, () -> urlService.normalize(url));
        assertEquals(400, exception.getCode().intValue());
    }

    private String longPath(int length)
    {
        char[] chars = new char[length];
        Arrays.fill(chars, 'a');
        return new String(chars);
    }
}
