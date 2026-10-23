package com.ruoyi.project.home.domain;

/**
 * 今日通知统计，仅返回父通知记录数与北京时间日期。
 *
 * @author weishanHub
 */
public class HomeNotificationStat
{
    private long count;

    /** 北京时间今日日期，yyyy-MM-dd。 */
    private String date;

    /** 固定为 Asia/Shanghai。 */
    private String timeZone;

    public long getCount()
    {
        return count;
    }

    public void setCount(long count)
    {
        this.count = count;
    }

    public String getDate()
    {
        return date;
    }

    public void setDate(String date)
    {
        this.date = date;
    }

    public String getTimeZone()
    {
        return timeZone;
    }

    public void setTimeZone(String timeZone)
    {
        this.timeZone = timeZone;
    }
}
