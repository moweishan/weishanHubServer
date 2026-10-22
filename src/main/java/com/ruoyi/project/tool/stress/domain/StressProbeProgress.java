package com.ruoyi.project.tool.stress.domain;

import java.util.Date;

/**
 * 批量试活进度快照。
 *
 * @author weishanHub
 */
public class StressProbeProgress
{
    /** 是否有试活任务在执行 */
    private boolean running;

    /** 是否已请求停止 */
    private boolean stopRequested;

    /** 目标接口总数 */
    private int total;

    /** 已完成数量（含跳过） */
    private int done;

    /** 判定为有效的数量 */
    private int alive;

    /** 判定为失效的数量 */
    private int dead;

    /** 因停止请求被跳过的数量 */
    private int skipped;

    /** 当前正在试活的接口名称 */
    private String current;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间，运行中为空 */
    private Date finishTime;

    /** 本次试活替换 {{phone}} 使用的手机号 */
    private String samplePhone;

    public boolean isRunning()
    {
        return running;
    }

    public void setRunning(boolean running)
    {
        this.running = running;
    }

    public boolean isStopRequested()
    {
        return stopRequested;
    }

    public void setStopRequested(boolean stopRequested)
    {
        this.stopRequested = stopRequested;
    }

    public int getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public int getDone()
    {
        return done;
    }

    public void setDone(int done)
    {
        this.done = done;
    }

    public int getAlive()
    {
        return alive;
    }

    public void setAlive(int alive)
    {
        this.alive = alive;
    }

    public int getDead()
    {
        return dead;
    }

    public void setDead(int dead)
    {
        this.dead = dead;
    }

    public int getSkipped()
    {
        return skipped;
    }

    public void setSkipped(int skipped)
    {
        this.skipped = skipped;
    }

    public String getCurrent()
    {
        return current;
    }

    public void setCurrent(String current)
    {
        this.current = current;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getFinishTime()
    {
        return finishTime;
    }

    public void setFinishTime(Date finishTime)
    {
        this.finishTime = finishTime;
    }

    public String getSamplePhone()
    {
        return samplePhone;
    }

    public void setSamplePhone(String samplePhone)
    {
        this.samplePhone = samplePhone;
    }
}
