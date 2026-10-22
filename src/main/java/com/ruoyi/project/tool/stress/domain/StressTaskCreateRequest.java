package com.ruoyi.project.tool.stress.domain;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建手机压力测试任务的请求体。
 *
 * <p>duration 与 unit 组合为持续时长，最终换算为秒；只校验正整数和 int 存储范围，
 * 不设额外业务时长上限。threads 为任务内并发执行线程数，为空时使用
 * {@code stress.threads-per-task} 默认值，上限由 {@code stress.max-threads-per-task} 控制。</p>
 *
 * @author weishanHub
 */
public class StressTaskCreateRequest
{
    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 时长数值 */
    @NotNull(message = "持续时长不能为空")
    @Min(value = 1, message = "持续时长必须大于 0")
    private Integer duration;

    /** 时长单位：SECOND / MINUTE / HOUR */
    @NotBlank(message = "时长单位不能为空")
    private String unit;

    /** 任务内并发执行线程数，为空时取默认值 */
    @Min(value = 1, message = "执行线程数必须大于 0")
    private Integer threads;

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public Integer getThreads()
    {
        return threads;
    }

    public void setThreads(Integer threads)
    {
        this.threads = threads;
    }
}
