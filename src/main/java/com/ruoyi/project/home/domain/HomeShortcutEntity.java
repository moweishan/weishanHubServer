package com.ruoyi.project.home.domain;

import java.util.Date;

/**
 * 用户快捷入口持久化对象。
 *
 * @author weishanHub
 */
public class HomeShortcutEntity
{
    private Long shortcutId;

    private Long userId;

    private Long menuId;

    private Integer orderNum;

    private Date createTime;

    private Date updateTime;

    public Long getShortcutId()
    {
        return shortcutId;
    }

    public void setShortcutId(Long shortcutId)
    {
        this.shortcutId = shortcutId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getMenuId()
    {
        return menuId;
    }

    public void setMenuId(Long menuId)
    {
        this.menuId = menuId;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
