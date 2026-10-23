package com.ruoyi.project.home.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.home.domain.HomeShortcutEntity;
import com.ruoyi.project.home.domain.HomeShortcutOption;
import com.ruoyi.project.home.mapper.HomeShortcutMapper;
import com.ruoyi.project.system.domain.SysMenu;
import com.ruoyi.project.system.service.ISysMenuService;

/**
 * 个人快捷入口。
 *
 * <p>读取与保存都基于当前用户实时菜单权限重新计算可用页面，数据库只保存 menuId 与顺序；
 * 菜单失权、删除、隐藏或改为外链后自动不再展示。userId 由 Controller 从登录身份取得。</p>
 *
 * @author weishanHub
 */
@Service
public class HomeShortcutService
{
    private static final int MAX_SHORTCUTS = 20;

    private final ISysMenuService menuService;

    private final HomeShortcutMapper shortcutMapper;

    public HomeShortcutService(ISysMenuService menuService, HomeShortcutMapper shortcutMapper)
    {
        this.menuService = menuService;
        this.shortcutMapper = shortcutMapper;
    }

    /**
     * 读取当前用户快捷入口，过滤已失权/删除/不可导航的菜单。
     */
    public List<HomeShortcutOption> getShortcuts(Long userId)
    {
        Map<Long, HomeShortcutOption> eligible = collectEligible(userId);
        List<HomeShortcutEntity> rows = shortcutMapper.selectByUserId(userId);
        List<HomeShortcutOption> result = new ArrayList<>();
        if (rows == null)
        {
            return result;
        }
        Set<Long> seen = new HashSet<>();
        for (HomeShortcutEntity row : rows)
        {
            if (row.getMenuId() == null || !seen.add(row.getMenuId()))
            {
                continue;
            }
            HomeShortcutOption option = eligible.get(row.getMenuId());
            if (option != null)
            {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * 当前用户所有可配置的站内页面。目录、按钮、外链、隐藏菜单不作为入口。
     */
    public List<HomeShortcutOption> getOptions(Long userId)
    {
        return new ArrayList<>(collectEligible(userId).values());
    }

    /**
     * 覆盖保存当前用户的快捷入口顺序。任一 menuId 无权限/不存在/不可导航时整体拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(Long userId, List<Long> menuIds)
    {
        if (menuIds == null)
        {
            throw new ServiceException("快捷入口参数不能为空", 400);
        }
        if (menuIds.size() > MAX_SHORTCUTS)
        {
            throw new ServiceException("快捷入口最多 " + MAX_SHORTCUTS + " 个", 400);
        }
        Map<Long, HomeShortcutOption> eligible = collectEligible(userId);
        Set<Long> seen = new HashSet<>();
        for (Long menuId : menuIds)
        {
            if (menuId == null || !seen.add(menuId))
            {
                throw new ServiceException("快捷入口包含重复或无效的菜单", 400);
            }
            if (!eligible.containsKey(menuId))
            {
                throw new ServiceException("快捷入口包含无权限、已删除或不可导航的页面", 400);
            }
        }
        shortcutMapper.deleteByUserId(userId);
        Date now = new Date();
        int order = 0;
        for (Long menuId : menuIds)
        {
            shortcutMapper.insertShortcut(userId, menuId, order++, now);
        }
    }

    private Map<Long, HomeShortcutOption> collectEligible(Long userId)
    {
        Map<Long, HomeShortcutOption> options = new LinkedHashMap<>();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        walk(menus, "", new ArrayList<>(), options);
        return options;
    }

    private void walk(List<SysMenu> menus, String parentPath, List<String> ancestors,
            Map<Long, HomeShortcutOption> options)
    {
        if (menus == null || menus.isEmpty())
        {
            return;
        }
        for (SysMenu menu : menus)
        {
            // 隐藏目录/菜单及其整棵子树不作为快捷入口
            if (!UserConstants.NORMAL.equals(menu.getStatus()) || !"0".equals(menu.getVisible()))
            {
                continue;
            }
            if (UserConstants.TYPE_DIR.equals(menu.getMenuType()))
            {
                String segment = pathSegment(menu.getPath());
                if (StringUtils.isBlank(segment) || StringUtils.ishttp(menu.getPath()))
                {
                    continue;
                }
                String dirPath = parentPath.isEmpty() ? "/" + segment : parentPath + "/" + segment;
                ancestors.add(menu.getMenuName());
                walk(menu.getChildren(), dirPath, ancestors, options);
                ancestors.remove(ancestors.size() - 1);
            }
            else if (UserConstants.TYPE_MENU.equals(menu.getMenuType()))
            {
                HomeShortcutOption option = toOption(menu, parentPath, ancestors);
                if (option != null)
                {
                    options.put(option.getMenuId(), option);
                }
            }
        }
    }

    private HomeShortcutOption toOption(SysMenu menu, String parentPath, List<String> ancestors)
    {
        if (!UserConstants.NO_FRAME.equals(menu.getIsFrame()))
        {
            return null;
        }
        String segment = pathSegment(menu.getPath());
        if (StringUtils.isBlank(segment) || StringUtils.ishttp(menu.getPath()))
        {
            return null;
        }
        String component = StringUtils.trim(menu.getComponent());
        if (StringUtils.isBlank(component) || UserConstants.LAYOUT.equals(component)
                || UserConstants.PARENT_VIEW.equals(component) || UserConstants.INNER_LINK.equals(component))
        {
            return null;
        }
        Map<String, Object> query = parseQuery(menu.getQuery());
        if (query == null)
        {
            // query 不是合法 JSON 对象时无法安全导航
            return null;
        }
        HomeShortcutOption option = new HomeShortcutOption();
        option.setMenuId(menu.getMenuId());
        option.setTitle(menu.getMenuName());
        option.setIcon(menu.getIcon());
        option.setPath(parentPath.isEmpty() ? "/" + segment : parentPath + "/" + segment);
        option.setQuery(query);
        option.setBreadcrumb(new ArrayList<>(ancestors));
        return option;
    }

    private String pathSegment(String path)
    {
        String value = StringUtils.trim(path);
        if (StringUtils.isBlank(value))
        {
            return "";
        }
        while (value.startsWith("/"))
        {
            value = value.substring(1);
        }
        while (value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Map<String, Object> parseQuery(String query)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(query))
        {
            return result;
        }
        try
        {
            JSONObject object = JSON.parseObject(query);
            if (object == null)
            {
                return null;
            }
            for (String key : object.keySet())
            {
                result.put(key, object.get(key));
            }
            return result;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }
}
