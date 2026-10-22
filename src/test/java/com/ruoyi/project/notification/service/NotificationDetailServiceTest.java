package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.project.notification.domain.NotificationAccessContext;
import com.ruoyi.project.notification.domain.NotificationDetailResult;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 公共详情授权：四类微信 UA、精确过期边界、无成功/撤销/删除、登录权限与过期后查看。
 */
class NotificationDetailServiceTest
{
    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    private NotificationRecordMapper recordMapper;

    private NotificationKeyService keyService;

    private NotificationDetailService service;

    private NotificationRecord record;

    private String token;

    @BeforeEach
    void setUp()
    {
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        keyService = new NotificationKeyService();
        service = serviceAt(NOW);
        token = keyService.generateDetailToken();
        record = new NotificationRecord();
        record.setRecordId(1L);
        record.setTitle("标题");
        record.setSummary("摘要");
        record.setDetail("# 正文");
        record.setMessageType("CARD");
        record.setSentAt(Date.from(NOW.minusSeconds(1800)));
        record.setExpiresAt(Date.from(NOW.plusSeconds(1800)));
        record.setRevoked("0");
        record.setDeleted("0");
        when(recordMapper.selectRecordByTokenHash(keyService.sha256Hex(token))).thenReturn(record);
    }

    @Test
    void fourWeChatUserAgentsCanViewWithinWindow()
    {
        String[] userAgents = {
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 MicroMessenger/8.0.47",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36 wxwork/4.1.20 MicroMessenger/7.0.1",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 MicroMessenger/3.9.8 WindowsWechat",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 wxwork/4.1.20"
        };
        for (String userAgent : userAgents)
        {
            NotificationDetailResult result = service.load(token,
                    new NotificationAccessContext(false, false, userAgent));
            assertTrue(result.isSuccess(), userAgent);
            assertEquals("WECHAT", result.getData().getAccessMode());
            assertFalse(result.getData().isExpired());
            assertEquals("标题", result.getData().getTitle());
        }
    }

    @Test
    void ordinaryBrowserWithoutLoginNeedsLogin()
    {
        NotificationDetailResult result = service.load(token,
                new NotificationAccessContext(false, false, "Mozilla/5.0 Chrome/130.0 Safari/537.36"));
        assertEquals(401, result.getCode());
    }

    @Test
    void exactExpiryMomentIsAlreadyExpired()
    {
        Instant expiry = NOW.plusSeconds(1800);
        NotificationDetailService atExpiry = serviceAt(expiry);
        NotificationDetailResult result = atExpiry.load(token,
                new NotificationAccessContext(false, false, "MicroMessenger/8.0"));
        assertEquals(401, result.getCode());
    }

    @Test
    void noSuccessfulChannelCannotBypassLogin()
    {
        record.setSentAt(null);
        record.setExpiresAt(null);
        NotificationDetailResult result = service.load(token,
                new NotificationAccessContext(false, false, "MicroMessenger/8.0"));
        assertEquals(401, result.getCode());
    }

    @Test
    void revokedOrDeletedLinkIsNotFoundEvenForAdmin()
    {
        record.setRevoked("1");
        assertEquals(404, service.load(token,
                new NotificationAccessContext(true, true, "Chrome")).getCode());

        record.setRevoked("0");
        record.setDeleted("1");
        assertEquals(404, service.load(token,
                new NotificationAccessContext(true, true, "Chrome")).getCode());
    }

    @Test
    void authenticatedUserWithPermissionCanViewAfterExpiry()
    {
        NotificationDetailService afterExpiry = serviceAt(NOW.plusSeconds(3600));
        NotificationDetailResult result = afterExpiry.load(token,
                new NotificationAccessContext(true, true, "Chrome"));
        assertTrue(result.isSuccess());
        assertEquals("LOGIN", result.getData().getAccessMode());
        assertTrue(result.getData().isExpired());
        assertEquals("# 正文", result.getData().getDetail());
    }

    @Test
    void authenticatedUserWithoutPermissionIsForbidden()
    {
        NotificationDetailResult result = service.load(token,
                new NotificationAccessContext(true, false, "Chrome"));
        assertEquals(403, result.getCode());
    }

    @Test
    void wechatHolderStillUsesPublicWindowEvenIfLoggedInWithoutPermission()
    {
        NotificationDetailResult result = service.load(token,
                new NotificationAccessContext(true, false, "MicroMessenger/8.0"));
        assertTrue(result.isSuccess());
        assertEquals("WECHAT", result.getData().getAccessMode());
    }

    @Test
    void invalidTokenIsNotFound()
    {
        assertEquals(404, service.load("not-a-valid-token", new NotificationAccessContext(false, false, "Chrome")).getCode());
    }

    private NotificationDetailService serviceAt(Instant instant)
    {
        return new NotificationDetailService(recordMapper, keyService, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
