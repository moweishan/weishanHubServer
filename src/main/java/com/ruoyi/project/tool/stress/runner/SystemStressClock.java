package com.ruoyi.project.tool.stress.runner;

import org.springframework.stereotype.Component;

/**
 * 生产环境时间源。
 *
 * @author weishanHub
 */
@Component
public class SystemStressClock implements StressClock
{
    @Override
    public long currentTimeMillis()
    {
        return System.currentTimeMillis();
    }

    @Override
    public long nanoTime()
    {
        return System.nanoTime();
    }

    @Override
    public void sleep(long millis) throws InterruptedException
    {
        Thread.sleep(millis);
    }
}
