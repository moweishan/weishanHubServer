package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.project.notification.domain.NotificationChannelCounts;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 发送完成事务：父行锁 -> 子行成功/失败 -> sentAt CAS -> 汇总的原子边界与锁顺序。
 */
class NotificationSendCompletionServiceTest
{
    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

    private NotificationRecordMapper recordMapper;

    private NotificationRecordChannelMapper channelMapper;

    private NotificationSendCompletionService service;

    private NotificationRecord record;

    @BeforeEach
    void setUp()
    {
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        channelMapper = org.mockito.Mockito.mock(NotificationRecordChannelMapper.class);
        service = new NotificationSendCompletionService(recordMapper, channelMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        record = new NotificationRecord();
        record.setRecordId(10L);
        when(recordMapper.selectRecordForUpdate(10L)).thenReturn(record);
    }

    @Test
    void successLocksParentThenChildCasAndAggregatesInOneTransactionalMethod() throws Exception
    {
        when(channelMapper.markChannelSuccess(eq(5L), anyString(), eq("msg-1"), any(Date.class))).thenReturn(1);
        when(recordMapper.markFirstSent(eq(10L), any(Date.class), any(Date.class))).thenReturn(1);
        when(channelMapper.selectCounts(10L)).thenReturn(counts(1, 0));

        assertTrue(service.completeSuccess(10L, 5L, "claim", "msg-1"));

        InOrder order = inOrder(recordMapper, channelMapper);
        order.verify(recordMapper).selectRecordForUpdate(10L);
        order.verify(channelMapper).markChannelSuccess(eq(5L), eq("claim"), eq("msg-1"), any(Date.class));
        order.verify(recordMapper).markFirstSent(eq(10L), any(Date.class), any(Date.class));
        order.verify(channelMapper).selectCounts(10L);
        order.verify(recordMapper).updateAggregate(eq(10L), eq("SUCCESS"), eq(1), eq(0), eq(1),
                any(Date.class));

        ArgumentCaptor<Date> sent = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> expire = ArgumentCaptor.forClass(Date.class);
        verify(recordMapper).markFirstSent(eq(10L), sent.capture(), expire.capture());
        assertEquals(3600000L, expire.getValue().getTime() - sent.getValue().getTime());
        assertNotNull(NotificationSendCompletionService.class
                .getMethod("completeSuccess", Long.class, Long.class, String.class, String.class)
                .getAnnotation(Transactional.class));
    }

    @Test
    void laterSuccessDoesNotTouchSentAtAgain()
    {
        record.setSentAt(Date.from(NOW.minusSeconds(600)));
        record.setExpiresAt(Date.from(NOW.plusSeconds(3000)));
        when(channelMapper.markChannelSuccess(eq(5L), anyString(), anyString(), any(Date.class))).thenReturn(1);
        when(channelMapper.selectCounts(10L)).thenReturn(counts(2, 0));

        assertTrue(service.completeSuccess(10L, 5L, "claim", "msg-2"));

        verify(recordMapper, never()).markFirstSent(any(), any(), any());
        verify(recordMapper).updateAggregate(eq(10L), eq("SUCCESS"), eq(2), eq(0), eq(2),
                any(Date.class));
    }

    @Test
    void failureAlsoLocksParentBeforeChildAndNeverSetsSentAt()
    {
        when(channelMapper.markChannelFailed(eq(5L), anyString(), anyString(), any(Date.class))).thenReturn(1);
        when(channelMapper.selectCounts(10L)).thenReturn(counts(0, 1));

        assertTrue(service.completeFailure(10L, 5L, "claim", "发送失败"));

        InOrder order = inOrder(recordMapper, channelMapper);
        order.verify(recordMapper).selectRecordForUpdate(10L);
        order.verify(channelMapper).markChannelFailed(eq(5L), eq("claim"), eq("发送失败"), any(Date.class));
        order.verify(channelMapper).selectCounts(10L);
        order.verify(recordMapper).updateAggregate(eq(10L), eq("FAILED"), eq(0), eq(1), eq(1),
                any(Date.class));
        verify(recordMapper, never()).markFirstSent(any(), any(), any());
    }

    @Test
    void lostClaimDoesNotAggregateOrReportCompletion()
    {
        when(channelMapper.markChannelSuccess(eq(5L), anyString(), anyString(), any(Date.class))).thenReturn(0);

        assertFalse(service.completeSuccess(10L, 5L, "claim", "msg"));

        verify(channelMapper, never()).selectCounts(any());
        verify(recordMapper, never()).updateAggregate(any(), anyString(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void recoveryLocksParentsInAscendingOrderBeforeUpdatingChildren()
    {
        when(channelMapper.selectRecordIdsByChannelStatus("SENDING")).thenReturn(Arrays.asList(10L, 11L));
        record.setRecordId(11L);
        when(recordMapper.selectRecordForUpdate(11L)).thenReturn(record);
        when(channelMapper.recoverSendingByRecord(eq(10L), anyString(), any(Date.class))).thenReturn(1);
        when(channelMapper.recoverSendingByRecord(eq(11L), anyString(), any(Date.class))).thenReturn(1);
        when(channelMapper.selectCounts(10L)).thenReturn(counts(0, 1));
        when(channelMapper.selectCounts(11L)).thenReturn(counts(0, 1));

        assertEquals(2, service.recoverUnknownSending("服务重启，发送结果未知"));

        InOrder order = inOrder(recordMapper, channelMapper);
        order.verify(recordMapper).selectRecordForUpdate(10L);
        order.verify(channelMapper).recoverSendingByRecord(eq(10L), anyString(), any(Date.class));
        order.verify(recordMapper).selectRecordForUpdate(11L);
        order.verify(channelMapper).recoverSendingByRecord(eq(11L), anyString(), any(Date.class));
    }

    private NotificationChannelCounts counts(int success, int failed)
    {
        NotificationChannelCounts counts = new NotificationChannelCounts();
        counts.setSuccessCount(success);
        counts.setFailedCount(failed);
        return counts;
    }
}
