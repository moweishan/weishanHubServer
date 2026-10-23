package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.home.domain.HomeShortcutEntity;
import com.ruoyi.project.home.domain.HomeShortcutOption;
import com.ruoyi.project.home.mapper.HomeShortcutMapper;
import com.ruoyi.project.system.domain.SysMenu;
import com.ruoyi.project.system.service.ISysMenuService;

/**
 * 快捷入口：深层路由、ParentView、顶级 C 菜单、内部 query、目录/按钮/外链/隐藏过滤、排序保存与失权隐藏。
 */
class HomeShortcutServiceTest
{
    private ISysMenuService menuService;

    private HomeShortcutMapper shortcutMapper;

    private HomeShortcutService service;

    private SysMenu leaf;

    private SysMenu profile;

    private SysMenu hidden;

    @BeforeEach
    void setUp()
    {
        menuService = mock(ISysMenuService.class);
        shortcutMapper = mock(HomeShortcutMapper.class);
        service = new HomeShortcutService(menuService, shortcutMapper);

        SysMenu user = menu(10L, 0L, "C", "用户管理", "user", "system/user/index", "0", "1", "");
        hidden = menu(11L, 0L, "C", "隐藏页", "hidden", "system/hidden/index", "1", "1", "");
        SysMenu external = menu(12L, 0L, "C", "外部页面", "https://example.com", "x", "0", "0", "");
        SysMenu parentViewAsMenu = menu(13L, 0L, "C", "容器页", "container", "ParentView", "0", "1", "");
        SysMenu layoutAsMenu = menu(15L, 0L, "C", "布局页", "layout-page", "Layout", "0", "1", "");
        SysMenu button = menu(14L, 10L, "F", "新增用户", "", "", "0", "1", "");

        SysMenu system = menu(1L, 0L, "M", "系统管理", "system", "", "0", "1", "");
        system.setChildren(Arrays.asList(user, hidden, external, parentViewAsMenu, layoutAsMenu, button));

        SysMenu inner = menu(21L, 20L, "C", "菜单1-1", "menu1-1", "nested/menu1/menu1-1", "0", "1",
                "{\"id\":1,\"name\":\"ry\"}");
        leaf = inner;
        SysMenu parent = menu(20L, 2L, "M", "菜单1", "menu1", "ParentView", "0", "1", "");
        parent.setChildren(Collections.singletonList(inner));
        SysMenu nested = menu(2L, 0L, "M", "多级菜单", "nested", "", "0", "1", "");
        nested.setChildren(Collections.singletonList(parent));

        SysMenu invalidQuery = menu(30L, 0L, "C", "坏 query", "bad", "bad/index", "0", "1", "not-json");
        profile = menu(31L, 0L, "C", "个人中心", "profile", "system/user/profile/index", "0", "1", "");

        when(menuService.selectMenuTreeByUserId(7L))
                .thenReturn(Arrays.asList(system, nested, invalidQuery, profile));
    }

    @Test
    void optionsOnlyContainNavigableInternalMenusAndComputeFullPaths()
    {
        List<HomeShortcutOption> options = service.getOptions(7L);

        assertEquals(3, options.size());
        HomeShortcutOption user = find(options, "用户管理");
        assertEquals("/system/user", user.getPath());
        assertEquals(Arrays.asList("系统管理"), user.getBreadcrumb());

        HomeShortcutOption deep = find(options, "菜单1-1");
        assertEquals("/nested/menu1/menu1-1", deep.getPath());
        assertEquals(Arrays.asList("多级菜单", "菜单1"), deep.getBreadcrumb());
        assertEquals(Integer.valueOf(1), deep.getQuery().get("id"));

        HomeShortcutOption top = find(options, "个人中心");
        assertEquals("/profile", top.getPath());
        assertTrue(top.getBreadcrumb().isEmpty());

        assertFalse(options.stream().anyMatch(item -> "隐藏页".equals(item.getTitle())));
        assertFalse(options.stream().anyMatch(item -> "外部页面".equals(item.getTitle())));
        assertFalse(options.stream().anyMatch(item -> "容器页".equals(item.getTitle())));
        assertFalse(options.stream().anyMatch(item -> "布局页".equals(item.getTitle())));
        assertFalse(options.stream().anyMatch(item -> "新增用户".equals(item.getTitle())));
        assertFalse(options.stream().anyMatch(item -> "坏 query".equals(item.getTitle())));
    }

    @Test
    void readHidesRevokedDeletedAndUnknownMenusButKeepsSavedOrder()
    {
        when(shortcutMapper.selectByUserId(7L)).thenReturn(Arrays.asList(
                shortcut(1L, 7L, leaf.getMenuId(), 0),
                shortcut(2L, 7L, hidden.getMenuId(), 1),
                shortcut(3L, 7L, 999L, 2),
                shortcut(4L, 7L, profile.getMenuId(), 3),
                shortcut(5L, 7L, leaf.getMenuId(), 4)));

        List<HomeShortcutOption> shortcuts = service.getShortcuts(7L);
        assertEquals(2, shortcuts.size());
        assertEquals("菜单1-1", shortcuts.get(0).getTitle());
        assertEquals("个人中心", shortcuts.get(1).getTitle());
        verify(menuService).selectMenuTreeByUserId(7L);
    }

    @Test
    void saveReplacesCurrentUserRowsWithValidatedOrder()
    {
        service.save(7L, Arrays.asList(leaf.getMenuId(), profile.getMenuId()));
        InOrder order = inOrder(shortcutMapper);
        order.verify(shortcutMapper).deleteByUserId(7L);
        order.verify(shortcutMapper).insertShortcut(eq(7L), eq(leaf.getMenuId()), eq(0), any(Date.class));
        order.verify(shortcutMapper).insertShortcut(eq(7L), eq(profile.getMenuId()), eq(1), any(Date.class));
    }

    @Test
    void saveRejectsMenuWithoutPermissionAndDoesNotDelete()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.save(7L, Arrays.asList(leaf.getMenuId(), hidden.getMenuId())));
        assertEquals(400, error.getCode().intValue());
        verify(shortcutMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    void saveRejectsDuplicatesAndTooMany()
    {
        assertThrows(ServiceException.class,
                () -> service.save(7L, Arrays.asList(leaf.getMenuId(), leaf.getMenuId())));
        List<Long> tooMany = new ArrayList<>();
        for (long i = 1; i <= 21; i++)
        {
            tooMany.add(i);
        }
        assertThrows(ServiceException.class, () -> service.save(7L, tooMany));
    }

    @Test
    void emptyListClearsShortcuts()
    {
        service.save(7L, Collections.emptyList());
        verify(shortcutMapper).deleteByUserId(7L);
        verify(shortcutMapper, never()).insertShortcut(anyLong(), anyLong(), any(Integer.class), any(Date.class));
    }

    private HomeShortcutOption find(List<HomeShortcutOption> options, String title)
    {
        return options.stream().filter(item -> title.equals(item.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("缺少选项：" + title));
    }

    private SysMenu menu(Long id, Long parentId, String type, String name, String path, String component,
            String visible, String isFrame, String query)
    {
        SysMenu menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setVisible(visible);
        menu.setStatus("0");
        menu.setIsFrame(isFrame);
        menu.setIsCache("0");
        menu.setOrderNum(0);
        menu.setIcon("link");
        menu.setQuery(query);
        return menu;
    }

    private HomeShortcutEntity shortcut(Long id, Long userId, Long menuId, int order)
    {
        HomeShortcutEntity entity = new HomeShortcutEntity();
        entity.setShortcutId(id);
        entity.setUserId(userId);
        entity.setMenuId(menuId);
        entity.setOrderNum(order);
        return entity;
    }
}
