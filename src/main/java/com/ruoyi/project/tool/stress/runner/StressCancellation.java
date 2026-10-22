package com.ruoyi.project.tool.stress.runner;

/**
 * 当前 HTTP 请求的取消信号。
 *
 * <p>传输层注册取消动作（通常是断开连接），任务到期或手动停止时执行，
 * 以尽快解除阻塞中的网络读取。已发送到服务端的请求无法撤回。</p>
 *
 * @author weishanHub
 */
public interface StressCancellation
{
    /** 是否已经请求取消 */
    boolean isCancelled();

    /**
     * 注册取消动作。若当前已经取消，会立即同步执行该动作。
     */
    void onCancel(Runnable action);

    /**
     * 请求结束或失败后移除取消动作，避免长任务中持续持有已关闭的连接对象。
     */
    void removeCancelAction(Runnable action);
}
