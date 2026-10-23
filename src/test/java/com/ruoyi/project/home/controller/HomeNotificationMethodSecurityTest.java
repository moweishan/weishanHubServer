package com.ruoyi.project.home.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.framework.security.LoginUser;
import com.ruoyi.framework.security.service.PermissionService;
import com.ruoyi.project.home.domain.HomeNotificationStat;
import com.ruoyi.project.home.service.HomeNotificationService;

/**
 * 走 Spring 方法安全代理实际调用 today：无权限抛 AccessDeniedException 且不进入 service，
 * 有 notification:record:query 或管理员 *:*:* 才可调用。不启动数据库、Redis 或完整 SpringBoot。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = HomeNotificationMethodSecurityTest.Config.class)
class HomeNotificationMethodSecurityTest
{
    @Autowired
    private HomeNotificationController controller;

    @Autowired
    private HomeNotificationService notificationService;

    @BeforeEach
    void setUp()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        reset(notificationService);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void withoutPermissionMethodSecurityThrowsAndServiceIsNotCalled()
    {
        login("notification:record:list");
        assertThrows(AccessDeniedException.class, () -> controller.today());
        verify(notificationService, org.mockito.Mockito.never()).getToday();
    }

    @Test
    void withQueryPermissionCanCallService()
    {
        when(notificationService.getToday()).thenReturn(new HomeNotificationStat());
        login("notification:record:query");
        assertNotNull(controller.today());
        verify(notificationService).getToday();
    }

    @Test
    void adminAllPermissionCanCallService()
    {
        when(notificationService.getToday()).thenReturn(new HomeNotificationStat());
        login("*:*:*");
        assertNotNull(controller.today());
        verify(notificationService).getToday();
    }

    private void login(String... permissions)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(9L);
        loginUser.setPermissions(new HashSet<>(Arrays.asList(permissions)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class Config
    {
        @Bean(name = "ss")
        public PermissionService permissionService()
        {
            return new PermissionService();
        }

        @Bean
        public HomeNotificationService homeNotificationService()
        {
            return mock(HomeNotificationService.class);
        }

        @Bean
        public HomeNotificationController homeNotificationController(HomeNotificationService service)
        {
            return new HomeNotificationController(service);
        }
    }
}
