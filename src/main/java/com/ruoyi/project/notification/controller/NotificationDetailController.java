package com.ruoyi.project.notification.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.aspectj.lang.annotation.Anonymous;
import com.ruoyi.framework.security.LoginUser;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.notification.domain.NotificationAccessContext;
import com.ruoyi.project.notification.domain.NotificationDetailResult;
import com.ruoyi.project.notification.service.NotificationDetailService;

/**
 * 通知公共详情隐藏路由。
 *
 * <p>只有该 GET 方法允许匿名进入，是否真正可看由服务层按 UA/服务端时间/成功时间/撤销删除/登录权限逐次判定。
 * 响应禁止缓存、禁止索引、禁止 Referer 泄漏票据。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/notification/detail")
public class NotificationDetailController
{
    private final NotificationDetailService detailService;

    public NotificationDetailController(NotificationDetailService detailService)
    {
        this.detailService = detailService;
    }

    @Anonymous
    @GetMapping("/{token}")
    public AjaxResult detail(@PathVariable String token, HttpServletRequest request, HttpServletResponse response)
    {
        applySecurityHeaders(response);
        NotificationAccessContext context = buildContext(request);
        NotificationDetailResult result = detailService.load(token, context);
        if (!result.isSuccess())
        {
            return AjaxResult.error(result.getCode(), result.getMessage());
        }
        return AjaxResult.success(result.getData());
    }

    static NotificationAccessContext buildContext(HttpServletRequest request)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof LoginUser;
        boolean hasPermission = false;
        if (authenticated)
        {
            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            hasPermission = loginUser.getPermissions() != null
                    && SecurityUtils.hasPermi(loginUser.getPermissions(), "notification:record:query");
        }
        return new NotificationAccessContext(authenticated, hasPermission, request.getHeader("User-Agent"));
    }

    static void applySecurityHeaders(HttpServletResponse response)
    {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
