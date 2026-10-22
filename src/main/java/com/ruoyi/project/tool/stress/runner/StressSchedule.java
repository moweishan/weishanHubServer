package com.ruoyi.project.tool.stress.runner;

import java.util.concurrent.TimeUnit;

/**
 * 单个任务内各接口的启动间隔计算。
 *
 * <p>生产环境固定为 61 秒。测试构造器允许传入更短周期，但线上默认值不会改变。</p>
 *
 * @author weishanHub
 */
public final class StressSchedule
{
    /** 生产环境固定间隔：61 秒 */
    public static final long PRODUCTION_INTERVAL_MILLIS = 61_000L;

    private final long intervalMillis;

    public StressSchedule()
    {
        this(PRODUCTION_INTERVAL_MILLIS);
    }

    public StressSchedule(long intervalMillis)
    {
        if (intervalMillis <= 0)
        {
            throw new IllegalArgumentException("接口启动间隔必须大于 0");
        }
        this.intervalMillis = intervalMillis;
    }

    public long getIntervalMillis()
    {
        return intervalMillis;
    }

    /**
     * 某接口本次开始后，下一次允许开始的时间（单调纳秒）。
     */
    public long nextStartNanos(long startNanos)
    {
        return startNanos + TimeUnit.MILLISECONDS.toNanos(intervalMillis);
    }

    /**
     * 判断某接口是否已到达下一次允许开始时间。
     */
    public boolean isDue(long nextStartNanos, long nowNanos)
    {
        return nextStartNanos <= nowNanos;
    }
}
