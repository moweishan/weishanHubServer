package com.ruoyi.project.home.domain;

import java.util.List;

/**
 * 保存快捷入口请求，只接受 menuId 与顺序。
 *
 * @author weishanHub
 */
public class HomeShortcutSaveRequest
{
    private List<Long> menuIds;

    public List<Long> getMenuIds()
    {
        return menuIds;
    }

    public void setMenuIds(List<Long> menuIds)
    {
        this.menuIds = menuIds;
    }
}
