package com.ruoyi.project.home.service;

/**
 * 高德 Web 服务调用异常。消息只包含安全的失败类别，不携带 Key、完整 URL 或原始响应。
 *
 * @author weishanHub
 */
public class AmapException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AmapException(String message)
    {
        super(message);
    }
}
