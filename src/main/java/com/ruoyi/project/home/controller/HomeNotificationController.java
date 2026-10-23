package com.ruoyi.project.home.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.home.domain.HomeNotificationStat;
import com.ruoyi.project.home.service.HomeNotificationService;

/**
 * 个人工作台通知统计接口。
 *
 * <p>只允许拥有 notification:record:query 的账号读取；前端隐藏不能替代服务端鉴权。
 * 管理员走既有 *:*:* 权限规则。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/home/notification")
public class HomeNotificationController
{
    private final HomeNotificationService notificationService;

    public HomeNotificationController(HomeNotificationService notificationService)
    {
        this.notificationService = notificationService;
    }

    @PreAuthorize("@ss.hasPermi('notification:record:query')")
    @GetMapping("/today")
    public AjaxResult today()
    {
        HomeNotificationStat stat = notificationService.getToday();
        return AjaxResult.success(stat);
    }
}
