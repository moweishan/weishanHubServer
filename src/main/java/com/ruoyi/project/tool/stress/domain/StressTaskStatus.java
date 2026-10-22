package com.ruoyi.project.tool.stress.domain;

/**
 * 压力测试任务状态常量。
 *
 * @author weishanHub
 */
public final class StressTaskStatus
{
    /** 运行中 */
    public static final String RUNNING = "RUNNING";

    /** 按计划时长正常结束 */
    public static final String COMPLETED = "COMPLETED";

    /** 用户手动停止 */
    public static final String STOPPED = "STOPPED";

    /** 应用重启或关闭导致中断 */
    public static final String INTERRUPTED = "INTERRUPTED";

    /** 执行器内部错误 */
    public static final String FAILED = "FAILED";

    private StressTaskStatus()
    {
    }
}
