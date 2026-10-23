package com.ruoyi.project.home.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 快捷入口选项。标题、图标、路径均由后端根据当前用户菜单实时计算，不信任客户端提交值。
 *
 * @author weishanHub
 */
public class HomeShortcutOption
{
    private Long menuId;

    private String title;

    private String icon;

    /** 完整站内路由路径，例如 /system/user。 */
    private String path;

    /** 菜单配置的默认 query。 */
    private Map<String, Object> query = new LinkedHashMap<>();

    /** 父级目录名称，用于展示和搜索。 */
    private List<String> breadcrumb;

    public Long getMenuId()
    {
        return menuId;
    }

    public void setMenuId(Long menuId)
    {
        this.menuId = menuId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public Map<String, Object> getQuery()
    {
        return query;
    }

    public void setQuery(Map<String, Object> query)
    {
        this.query = query;
    }

    public List<String> getBreadcrumb()
    {
        return breadcrumb;
    }

    public void setBreadcrumb(List<String> breadcrumb)
    {
        this.breadcrumb = breadcrumb;
    }
}
