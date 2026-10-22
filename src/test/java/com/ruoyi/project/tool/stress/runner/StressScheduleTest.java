package com.ruoyi.project.tool.stress.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 61 秒接口启动间隔测试。
 */
class StressScheduleTest
{
    @Test
    void productionIntervalMustBeSixtyOneSeconds()
    {
        assertEquals(61_000L, StressSchedule.PRODUCTION_INTERVAL_MILLIS);
        assertEquals(61_000L, new StressSchedule().getIntervalMillis());
    }

    @Test
    void sameInterfaceNotDueBeforeSixtyOneSeconds()
    {
        StressSchedule schedule = new StressSchedule();
        long startNanos = 1_000_000_000L;
        long nextStart = schedule.nextStartNanos(startNanos);

        assertFalse(schedule.isDue(nextStart, startNanos + TimeUnit.MILLISECONDS.toNanos(60_999L)));
        assertTrue(schedule.isDue(nextStart, startNanos + TimeUnit.MILLISECONDS.toNanos(61_000L)));
    }

    @Test
    void shortIntervalOnlyForTests()
    {
        StressSchedule schedule = new StressSchedule(200L);
        assertEquals(200L, schedule.getIntervalMillis());
        assertFalse(schedule.isDue(schedule.nextStartNanos(0L), TimeUnit.MILLISECONDS.toNanos(199L)));
        assertTrue(schedule.isDue(schedule.nextStartNanos(0L), TimeUnit.MILLISECONDS.toNanos(200L)));
    }
}
