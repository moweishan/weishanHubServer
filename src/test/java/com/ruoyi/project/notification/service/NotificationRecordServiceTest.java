package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 后台人工重试：只重置失败渠道、状态可跟踪、绝不改写 sentAt/expiresAt。
 */
class NotificationRecordServiceTest
{
    private NotificationRecordMapper recordMapper;

    private NotificationRecordChannelMapper channelMapper;

    private NotificationRecordService service;

    private NotificationCryptoService cryptoService;

    private NotificationUrlService urlService;

    private NotificationRecord record;

    @BeforeEach
    void setUp()
    {
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        channelMapper = org.mockito.Mockito.mock(NotificationRecordChannelMapper.class);
        cryptoService = org.mockito.Mockito.mock(NotificationCryptoService.class);
        when(cryptoService.isAvailable()).thenReturn(true);
        urlService = org.mockito.Mockito.mock(NotificationUrlService.class);
        service = new NotificationRecordService(recordMapper, channelMapper, cryptoService, urlService,
                Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC));
        record = new NotificationRecord();
        record.setRecordId(10L);
        record.setDeleted("0");
        record.setRevoked("0");
        record.setSentAt(Date.from(Instant.parse("2026-10-21T08:30:00Z")));
        record.setExpiresAt(Date.from(Instant.parse("2026-10-21T09:30:00Z")));
        when(recordMapper.selectRecordById(10L)).thenReturn(record);
        when(recordMapper.selectRecordForUpdate(10L)).thenReturn(record);
    }

    @Test
    void retryResetsOnlyFailedChannelsAndKeepsExpiry()
    {
        when(channelMapper.resetFailedToPending(eq(10L), any(Date.class))).thenReturn(2);

        int count = service.retryFailed(10L);

        assertEquals(2, count);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(recordMapper, channelMapper);
        order.verify(recordMapper).selectRecordForUpdate(10L);
        order.verify(channelMapper).resetFailedToPending(eq(10L), any(Date.class));
        verify(recordMapper).updateStatus(eq(10L), eq("SENDING"), any(Date.class));
        verify(recordMapper, never()).markFirstSent(any(), any(), any());
    }

    @Test
    void retryWithoutFailedChannelIsRejected()
    {
        when(channelMapper.resetFailedToPending(eq(10L), any(Date.class))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.retryFailed(10L));
        assertEquals(409, exception.getCode().intValue());
        verify(recordMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    void revokedRecordCannotRetry()
    {
        record.setRevoked("1");
        assertThrows(ServiceException.class, () -> service.retryFailed(10L));
        verify(channelMapper, never()).resetFailedToPending(any(), any());
    }

    @Test
    void detailContainsChannelResultsButNoSnapshotCredentials()
    {
        NotificationRecordChannel channel = new NotificationRecordChannel();
        channel.setChannelId(5L);
        channel.setRouteCode("order");
        channel.setChannelName("订单");
        channel.setProvider("WECOM_APP");
        channel.setCorpId("corp");
        channel.setAgentId(1001);
        channel.setToUser("u1");
        channel.setSecretCipher("secret-cipher");
        channel.setStatus("FAILED");
        channel.setAttemptCount(1);
        channel.setErrorMessage("用户不存在");
        when(channelMapper.selectChannelsByRecordId(10L)).thenReturn(Collections.singletonList(channel));
        record.setTokenCipher("token-cipher");
        record.setDetailBaseUrl("https://snap.example.com");
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.resolveRecordBaseUrlOrNull(record)).thenReturn("https://snap.example.com");
        when(urlService.buildDetailUrl("https://snap.example.com", "token"))
                .thenReturn("https://snap.example.com/notification/detail/token");

        assertEquals(1, service.selectRecordDetail(10L).getChannels().size());
        assertEquals("用户不存在", service.selectRecordDetail(10L).getChannels().get(0).getErrorMessage());
        assertEquals("https://snap.example.com/notification/detail/token",
                service.selectRecordDetail(10L).getDetailUrl());
    }
}
