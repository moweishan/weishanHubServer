package com.ruoyi.project.tool.stress.runner;

/**
 * 任务停止原因。
 *
 * @author weishanHub
 */
public enum StressStopReason
{
    /** 到达计划结束时间 */
    EXPIRED,

    /** 用户手动停止 */
    MANUAL,

    /** 应用关闭 */
    SHUTDOWN,

    /** 执行线程被意外中断 */
    INTERRUPTED,

    /** 执行器内部错误 */
    ERROR
}
