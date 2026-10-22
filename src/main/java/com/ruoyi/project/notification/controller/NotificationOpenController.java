package com.ruoyi.project.notification.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.aspectj.lang.annotation.Anonymous;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.notification.domain.NotificationAcceptRequest;
import com.ruoyi.project.notification.domain.NotificationAcceptResponse;
import com.ruoyi.project.notification.service.NotificationRateLimitService;
import com.ruoyi.project.notification.service.NotificationReceiveService;

/**
 * 对外开放的通知受理接口。
 *
 * <p>不使用后台登录态，但必须携带独立调用凭证 Header X-Notification-Key；
 * 只对这一个 POST 方法使用 @Anonymous，不扩大 /open 或 /notification 其他路由的匿名权限。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/open")
public class NotificationOpenController extends BaseController
{
    private final NotificationReceiveService receiveService;

    private final NotificationRateLimitService rateLimitService;

    public NotificationOpenController(NotificationReceiveService receiveService,
            NotificationRateLimitService rateLimitService)
    {
        this.receiveService = receiveService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * 受理一条通知。X-Notification-Key 与系统 Authorization 完全独立。
     */
    @Anonymous
    @PostMapping("/notifications")
    public AjaxResult send(@RequestHeader(value = "X-Notification-Key", required = false) String notificationKey,
            @Validated @RequestBody NotificationAcceptRequest request, HttpServletRequest servletRequest)
    {
        // 先做来源 IP 限流，再校验凭证并做凭证级限流
        rateLimitService.checkIp(IpUtils.getIpAddr(servletRequest));
        NotificationAcceptResponse response = receiveService.accept(notificationKey, request);
        return AjaxResult.success(response);
    }
}
