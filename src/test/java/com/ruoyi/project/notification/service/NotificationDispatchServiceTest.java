package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 异步发送：SDK 调用在事务外，成功/失败全部交给独立事务完成器；claim 防重与扫描/恢复。
 */
class NotificationDispatchServiceTest
{
    private NotificationRecordMapper recordMapper;

    private NotificationRecordChannelMapper channelMapper;

    private NotificationCryptoService cryptoService;

    private NotificationSenderFactory senderFactory;

    private NotificationSender sender;

    private NotificationUrlService urlService;

    private NotificationSendCompletionService completionService;

    private NotificationDispatchService service;

    private NotificationRecord record;

    private NotificationRecordChannel channel;

    @BeforeEach
    void setUp()
    {
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        channelMapper = org.mockito.Mockito.mock(NotificationRecordChannelMapper.class);
        cryptoService = org.mockito.Mockito.mock(NotificationCryptoService.class);
        senderFactory = org.mockito.Mockito.mock(NotificationSenderFactory.class);
        sender = org.mockito.Mockito.mock(NotificationSender.class);
        urlService = org.mockito.Mockito.mock(NotificationUrlService.class);
        completionService = org.mockito.Mockito.mock(NotificationSendCompletionService.class);
        TaskExecutor executor = Runnable::run;
        service = new NotificationDispatchService(recordMapper, channelMapper, cryptoService,
                new NotificationKeyService(), senderFactory, completionService, urlService,
                new NotificationProperties(), Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC),
                executor);

        record = new NotificationRecord();
        record.setRecordId(10L);
        record.setMessageType("TEXT");
        record.setTitle("标题");
        record.setDetail("正文");
        record.setTokenCipher("token-cipher");
        record.setDetailBaseUrl("https://snap.example.com");
        record.setDeleted("0");
        record.setRevoked("0");

        channel = new NotificationRecordChannel();
        channel.setChannelId(5L);
        channel.setRecordId(10L);
        channel.setProvider("WECOM_APP");
        channel.setCorpId("corp");
        channel.setAgentId(1001);
        channel.setToUser("user");
        channel.setSecretCipher("secret-cipher");
        channel.setStatus("PENDING");
        when(urlService.resolveRecordBaseUrl(record)).thenReturn("https://snap.example.com");
    }

    @Test
    void successfulSdkSendDelegatesAtomicCompletion()
    {
        claimed();
        when(cryptoService.isAvailable()).thenReturn(true);
        when(cryptoService.decrypt("secret-cipher")).thenReturn("plain-secret");
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.buildDetailUrl("https://snap.example.com", "token"))
                .thenReturn("https://snap.example.com/notification/detail/token");
        when(senderFactory.get("WECOM_APP")).thenReturn(sender);
        when(sender.send(any(), any(), anyString(), anyString())).thenReturn(NotificationSendResult.success("msg-1"));

        service.dispatchChannel(5L);

        verify(completionService).completeSuccess(eq(10L), eq(5L), anyString(), eq("msg-1"));
        verify(completionService, never()).completeFailure(any(), any(), anyString(), anyString());
        verify(recordMapper, never()).markFirstSent(any(), any(), any());
    }

    @Test
    void oldRecordWithoutSnapshotUsesLegacyPrivateBaseUrl()
    {
        record.setDetailBaseUrl(null);
        claimed();
        when(cryptoService.isAvailable()).thenReturn(true);
        when(cryptoService.decrypt("secret-cipher")).thenReturn("plain-secret");
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.resolveRecordBaseUrl(record)).thenReturn("https://legacy.example.com");
        when(urlService.buildDetailUrl("https://legacy.example.com", "token"))
                .thenReturn("https://legacy.example.com/notification/detail/token");
        when(senderFactory.get("WECOM_APP")).thenReturn(sender);
        when(sender.send(any(), any(), anyString(), anyString())).thenReturn(NotificationSendResult.success("msg-2"));

        service.dispatchChannel(5L);

        verify(urlService).resolveRecordBaseUrl(record);
        verify(sender).send(eq(record), eq(channel), eq("plain-secret"),
                eq("https://legacy.example.com/notification/detail/token"));
        verify(completionService).completeSuccess(eq(10L), eq(5L), anyString(), eq("msg-2"));
    }

    @Test
    void sdkFailureDelegatesAtomicFailureCompletion()
    {
        claimed();
        when(cryptoService.isAvailable()).thenReturn(true);
        when(cryptoService.decrypt("secret-cipher")).thenReturn("plain-secret");
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.buildDetailUrl("https://snap.example.com", "token"))
                .thenReturn("https://snap.example.com/notification/detail/token");
        when(senderFactory.get("WECOM_APP")).thenReturn(sender);
        when(sender.send(any(), any(), anyString(), anyString()))
                .thenReturn(NotificationSendResult.failure("企业微信返回错误 81013（平台拒绝发送）"));

        service.dispatchChannel(5L);

        verify(completionService).completeFailure(eq(10L), eq(5L), anyString(),
                eq("企业微信返回错误 81013（平台拒绝发送）"));
        verify(recordMapper, never()).markFirstSent(any(), any(), any());
    }

    @Test
    void claimConflictSkipsSending()
    {
        when(cryptoService.isAvailable()).thenReturn(true);
        when(channelMapper.claimChannel(eq(5L), anyString(), any(Date.class))).thenReturn(0);

        service.dispatchChannel(5L);

        verify(channelMapper, never()).selectChannelById(5L);
        verify(sender, never()).send(any(), any(), anyString(), anyString());
        verify(completionService, never()).completeSuccess(any(), any(), anyString(), anyString());
    }

    @Test
    void revokedRecordIsNotSent()
    {
        record.setRevoked("1");
        claimed();
        when(cryptoService.isAvailable()).thenReturn(true);

        service.dispatchChannel(5L);

        verify(sender, never()).send(any(), any(), anyString(), anyString());
        verify(completionService).completeFailure(eq(10L), eq(5L), anyString(), eq("通知链接已撤销，取消发送"));
    }

    @Test
    void scanPendingSubmitsPendingChannels()
    {
        when(channelMapper.selectPendingChannelIds(20)).thenReturn(Arrays.asList(5L));
        claimed();
        when(cryptoService.isAvailable()).thenReturn(true);
        when(cryptoService.decrypt("secret-cipher")).thenReturn("plain-secret");
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.buildDetailUrl("https://snap.example.com", "token"))
                .thenReturn("https://snap.example.com/notification/detail/token");
        when(senderFactory.get("WECOM_APP")).thenReturn(sender);
        when(sender.send(any(), any(), anyString(), anyString())).thenReturn(NotificationSendResult.success("msg"));

        service.scanPending();

        verify(sender).send(any(), any(), anyString(), anyString());
        verify(completionService).completeSuccess(eq(10L), eq(5L), anyString(), eq("msg"));
    }

    @Test
    void startupRecoveryDelegatesToTransactionalCompletion()
    {
        when(completionService.recoverUnknownSending(anyString())).thenReturn(2);

        assertEquals(2, service.recoverUnknownSending());

        verify(completionService).recoverUnknownSending(anyString());
    }

    private void claimed()
    {
        when(channelMapper.claimChannel(eq(5L), anyString(), any(Date.class))).thenReturn(1);
        when(channelMapper.selectChannelById(5L)).thenReturn(channel);
        when(recordMapper.selectRecordById(10L)).thenReturn(record);
    }
}
