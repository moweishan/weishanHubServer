package com.ruoyi.project.home.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.framework.security.LoginUser;
import com.ruoyi.project.home.domain.HomeShortcutSaveRequest;
import com.ruoyi.project.home.service.HomeShortcutService;

/**
 * 快捷入口接口只从登录身份取 userId，不接受客户端 userId。
 */
class HomeShortcutControllerTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAndSaveUseLoginUserId()
    {
        HomeShortcutService service = mock(HomeShortcutService.class);
        when(service.getShortcuts(42L)).thenReturn(Collections.emptyList());
        HomeShortcutController controller = new HomeShortcutController(service);
        loginAs(42L);

        controller.list();
        verify(service).getShortcuts(42L);

        HomeShortcutSaveRequest request = new HomeShortcutSaveRequest();
        request.setMenuIds(Arrays.asList(3L, 5L));
        controller.save(request);
        verify(service).save(42L, Arrays.asList(3L, 5L));
    }

    @Test
    void optionsAlsoUseLoginUserId()
    {
        HomeShortcutService service = mock(HomeShortcutService.class);
        when(service.getOptions(8L)).thenReturn(Collections.emptyList());
        HomeShortcutController controller = new HomeShortcutController(service);
        loginAs(8L);
        controller.options();
        verify(service).getOptions(8L);
    }

    private void loginAs(Long userId)
    {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setPermissions(Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }
}
