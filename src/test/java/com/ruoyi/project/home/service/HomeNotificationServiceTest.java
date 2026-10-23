package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.project.home.domain.HomeNotificationStat;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 通知统计按北京时间自然日计算 [今日 00:00, 明日 00:00)，跨 UTC 日界不偏移。
 */
class HomeNotificationServiceTest
{
    @Test
    void beijingDaySwitchesAtUtc1600()
    {
        NotificationRecordMapper mapper = mock(NotificationRecordMapper.class);
        when(mapper.countFirstSentBetween(any(Date.class), any(Date.class))).thenReturn(5L);
        HomeNotificationService service = serviceAt("2026-01-01T15:59:59Z", mapper);

        HomeNotificationStat stat = service.getToday();

        assertEquals(5L, stat.getCount());
        assertEquals("2026-01-01", stat.getDate());
        assertEquals("Asia/Shanghai", stat.getTimeZone());
        ArgumentCaptor<Date> begin = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(mapper).countFirstSentBetween(begin.capture(), end.capture());
        assertEquals(Instant.parse("2025-12-31T16:00:00Z"), begin.getValue().toInstant());
        assertEquals(Instant.parse("2026-01-01T16:00:00Z"), end.getValue().toInstant());
    }

    @Test
    void exactBeijingMidnightStartsNewDay()
    {
        NotificationRecordMapper mapper = mock(NotificationRecordMapper.class);
        when(mapper.countFirstSentBetween(any(Date.class), any(Date.class))).thenReturn(0L);
        HomeNotificationService service = serviceAt("2026-01-01T16:00:00Z", mapper);

        HomeNotificationStat stat = service.getToday();

        assertEquals("2026-01-02", stat.getDate());
        ArgumentCaptor<Date> begin = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(mapper).countFirstSentBetween(begin.capture(), end.capture());
        assertEquals(Instant.parse("2026-01-01T16:00:00Z"), begin.getValue().toInstant());
        assertEquals(Instant.parse("2026-01-02T16:00:00Z"), end.getValue().toInstant());
    }

    @Test
    void beijingMorningStaysOnSameDay()
    {
        NotificationRecordMapper mapper = mock(NotificationRecordMapper.class);
        when(mapper.countFirstSentBetween(any(Date.class), any(Date.class))).thenReturn(1L);
        HomeNotificationService service = serviceAt("2026-01-01T00:00:00Z", mapper);

        HomeNotificationStat stat = service.getToday();

        assertEquals("2026-01-01", stat.getDate());
        ArgumentCaptor<Date> begin = ArgumentCaptor.forClass(Date.class);
        verify(mapper).countFirstSentBetween(begin.capture(), any(Date.class));
        assertEquals(Instant.parse("2025-12-31T16:00:00Z"), begin.getValue().toInstant());
    }

    private HomeNotificationService serviceAt(String instant, NotificationRecordMapper mapper)
    {
        return new HomeNotificationService(mapper,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
