package com.ruoyi.project.home.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ruoyi.project.home.config.HomeConfig;
import com.ruoyi.project.home.domain.HomeNotificationStat;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 今日通知统计。
 *
 * <p>统计北京时间 [今日 00:00, 明日 00:00) 内首个渠道成功 sent_at 的父通知记录数。
 * 父记录只有一个 sent_at，因此多渠道、部分成功、后续重试都只计 1；软删除不扣减历史数。</p>
 *
 * @author weishanHub
 */
@Service
public class HomeNotificationService
{
    private final NotificationRecordMapper recordMapper;

    private final Clock clock;

    public HomeNotificationService(NotificationRecordMapper recordMapper,
            @Qualifier("homeClock") Clock clock)
    {
        this.recordMapper = recordMapper;
        this.clock = clock;
    }

    public HomeNotificationStat getToday()
    {
        LocalDate today = LocalDate.now(clock.withZone(HomeConfig.BEIJING_ZONE));
        ZonedDateTime begin = today.atStartOfDay(HomeConfig.BEIJING_ZONE);
        ZonedDateTime end = today.plusDays(1).atStartOfDay(HomeConfig.BEIJING_ZONE);
        long count = recordMapper.countFirstSentBetween(Date.from(begin.toInstant()), Date.from(end.toInstant()));
        HomeNotificationStat stat = new HomeNotificationStat();
        stat.setCount(count);
        stat.setDate(today.toString());
        stat.setTimeZone(HomeConfig.BEIJING_ZONE.getId());
        return stat;
    }
}
