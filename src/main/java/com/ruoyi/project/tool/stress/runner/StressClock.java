package com.ruoyi.project.tool.stress.runner;

/**
 * 压力测试执行器使用的时间源，便于测试中注入可控时钟。
 *
 * <p>任务截止使用墙钟 currentTimeMillis，同一接口 61 秒间隔使用单调时钟 nanoTime，
 * 避免系统时间回拨导致间隔被缩短。</p>
 *
 * @author weishanHub
 */
public interface StressClock
{
    /** 当前墙钟时间（毫秒） */
    long currentTimeMillis();

    /** 单调纳秒时间 */
    long nanoTime();

    /** 休眠指定毫秒，允许被中断 */
    void sleep(long millis) throws InterruptedException;
}
