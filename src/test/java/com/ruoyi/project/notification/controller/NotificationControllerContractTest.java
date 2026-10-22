package com.ruoyi.project.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import com.ruoyi.framework.aspectj.lang.annotation.Anonymous;
import com.ruoyi.project.notification.domain.NotificationAcceptRequest;

/**
 * 接口契约：开放接口只认 X-Notification-Key；公共详情单独匿名；后台管理方法都有权限注解。
 */
class NotificationControllerContractTest
{
    @Test
    void openEndpointUsesDedicatedHeaderAndIsTheOnlyAnonymousPost()
            throws Exception
    {
        Method send = NotificationOpenController.class.getMethod("send", String.class,
                NotificationAcceptRequest.class, HttpServletRequest.class);
        RequestHeader header = send.getParameters()[0].getAnnotation(RequestHeader.class);
        assertNotNull(header);
        assertEquals("X-Notification-Key", header.value());
        assertNotNull(send.getAnnotation(Anonymous.class));
    }

    @Test
    void publicDetailGetIsAnonymousAndManagementControllersAllRequirePermissions() throws Exception
    {
        Method detail = NotificationDetailController.class.getMethod("detail", String.class,
                HttpServletRequest.class, HttpServletResponse.class);
        assertNotNull(detail.getAnnotation(Anonymous.class));

        for (Class<?> controller : new Class<?>[] { NotificationChannelController.class,
                NotificationCredentialController.class, NotificationRecordController.class })
        {
            for (Method method : controller.getDeclaredMethods())
            {
                if (method.isSynthetic())
                {
                    continue;
                }
                assertNotNull(method.getAnnotation(PreAuthorize.class),
                        controller.getSimpleName() + "." + method.getName() + " 缺少 @PreAuthorize");
            }
        }
    }

    @Test
    void openControllerDoesNotHaveClassLevelAnonymous()
    {
        assertTrue(NotificationOpenController.class.getAnnotation(Anonymous.class) == null);
        assertTrue(NotificationDetailController.class.getAnnotation(Anonymous.class) == null);
    }
}
