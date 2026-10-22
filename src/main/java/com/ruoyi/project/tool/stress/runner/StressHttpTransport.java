package com.ruoyi.project.tool.stress.runner;

/**
 * HTTP 传输层抽象，便于测试中替换为可控的阻塞实现。
 *
 * @author weishanHub
 */
public interface StressHttpTransport
{
    /**
     * 执行一次请求。实现必须响应 {@link StressCancellation}，以支持到期/手动停止。
     */
    StressHttpResult execute(StressHttpRequest request, StressCancellation cancellation) throws Exception;
}
