package com.ruoyi.project.home.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 通知统计必须服务端强制鉴权，不能只靠前端隐藏。
 */
class HomeNotificationControllerContractTest
{
    @Test
    void todayRequiresNotificationRecordQueryPermission() throws Exception
    {
        Method today = HomeNotificationController.class.getMethod("today");
        PreAuthorize authorize = today.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermi('notification:record:query')", authorize.value());
    }
}
