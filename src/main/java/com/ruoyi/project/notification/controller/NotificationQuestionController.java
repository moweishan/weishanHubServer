package com.ruoyi.project.notification.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.aspectj.lang.annotation.Anonymous;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.notification.domain.NotificationQuestionRequest;
import com.ruoyi.project.notification.service.NotificationQuestionService;
import com.ruoyi.project.notification.service.NotificationRateLimitService;

/** 插件接口使用通知凭证，网页接口逐次复用通知详情授权。 */
@RestController
public class NotificationQuestionController
{
    private final NotificationQuestionService service;
    private final NotificationRateLimitService limits;

    public NotificationQuestionController(NotificationQuestionService service, NotificationRateLimitService limits)
    {
        this.service = service;
        this.limits = limits;
    }

    @Anonymous
    @PostMapping("/open/notification-questions")
    public AjaxResult create(@RequestHeader(value = "X-Notification-Key", required = false) String key,
            @Validated @RequestBody NotificationQuestionRequest body, HttpServletRequest request, HttpServletResponse response)
    {
        NotificationDetailController.applySecurityHeaders(response);
        limits.checkIp(IpUtils.getIpAddr(request));
        return AjaxResult.success(service.create(key, body));
    }

    @Anonymous
    @GetMapping("/open/notification-questions/{requestId}")
    public AjaxResult poll(@RequestHeader(value = "X-Notification-Key", required = false) String key,
            @PathVariable String requestId, HttpServletResponse response)
    {
        NotificationDetailController.applySecurityHeaders(response);
        return AjaxResult.success(service.poll(key, requestId));
    }

    @Anonymous
    @PostMapping("/open/notification-questions/{requestId}/settle")
    public AjaxResult settle(@RequestHeader(value = "X-Notification-Key", required = false) String key,
            @PathVariable String requestId, @RequestBody JsonNode body, HttpServletResponse response)
    {
        NotificationDetailController.applySecurityHeaders(response);
        return AjaxResult.success(service.settle(key, requestId, body));
    }

    @Anonymous
    @GetMapping("/notification/detail/{token}/question")
    public AjaxResult question(@PathVariable String token, HttpServletRequest request, HttpServletResponse response)
    {
        NotificationDetailController.applySecurityHeaders(response);
        return AjaxResult.success(service.publicQuestion(token, NotificationDetailController.buildContext(request), null));
    }

    @Anonymous
    @PostMapping("/notification/detail/{token}/answer")
    public AjaxResult answer(@PathVariable String token, @RequestBody JsonNode body,
            HttpServletRequest request, HttpServletResponse response)
    {
        NotificationDetailController.applySecurityHeaders(response);
        return AjaxResult.success(service.publicQuestion(token, NotificationDetailController.buildContext(request), body));
    }
}
