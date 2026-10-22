package com.ruoyi.project.tool.stress.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 手机压力测试任务 tool_stress_task
 *
 * @author weishanHub
 */
public class StressTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private Long taskId;

    /** 测试手机号 */
    private String phone;

    /** 持续时长（秒） */
    private Integer durationSeconds;

    /** 任务内并发执行线程数 */
    private Integer threadsPerTask;

    /** 任务状态：RUNNING / COMPLETED / STOPPED / INTERRUPTED / FAILED */
    private String status;

    /** 运行中手机号，仅 RUNNING 时非空，用于数据库唯一约束防止重复提交 */
    @JsonIgnore
    private String runningPhone;

    /** 开始时间（提交时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    /** 计划结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date planEndTime;

    /** 实际结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date actualEndTime;

    /** 成功次数（HTTP 200-299） */
    private Integer successCount;

    /** 失败次数 */
    private Integer failCount;

    /** 提交时的接口快照 JSON，不直接返回给前端 */
    @JsonIgnore
    private String snapshotJson;

    /** 任务所有者用户ID */
    private Long ownerId;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public Integer getDurationSeconds()
    {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds)
    {
        this.durationSeconds = durationSeconds;
    }

    public Integer getThreadsPerTask()
    {
        return threadsPerTask;
    }

    public void setThreadsPerTask(Integer threadsPerTask)
    {
        this.threadsPerTask = threadsPerTask;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRunningPhone()
    {
        return runningPhone;
    }

    public void setRunningPhone(String runningPhone)
    {
        this.runningPhone = runningPhone;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getPlanEndTime()
    {
        return planEndTime;
    }

    public void setPlanEndTime(Date planEndTime)
    {
        this.planEndTime = planEndTime;
    }

    public Date getActualEndTime()
    {
        return actualEndTime;
    }

    public void setActualEndTime(Date actualEndTime)
    {
        this.actualEndTime = actualEndTime;
    }

    public Integer getSuccessCount()
    {
        return successCount;
    }

    public void setSuccessCount(Integer successCount)
    {
        this.successCount = successCount;
    }

    public Integer getFailCount()
    {
        return failCount;
    }

    public void setFailCount(Integer failCount)
    {
        this.failCount = failCount;
    }

    public String getSnapshotJson()
    {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson)
    {
        this.snapshotJson = snapshotJson;
    }

    public Long getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(Long ownerId)
    {
        this.ownerId = ownerId;
    }
}
