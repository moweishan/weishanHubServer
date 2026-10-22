package com.ruoyi.project.notification.domain;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 统一详情页地址保存请求。
 *
 * @author weishanHub
 */
public class NotificationDetailUrlRequest
{
    /** 前端站点基地址，例如 https://hub.example.com/sub */
    @NotBlank(message = "详情页地址不能为空")
    @Size(max = 500, message = "详情页地址不能超过 500 个字符")
    private String url;

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }
}
