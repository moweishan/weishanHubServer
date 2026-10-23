package com.ruoyi.project.home.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 城市搜索响应。未配置 Key 时 configured=false，cities 为空。
 *
 * @author weishanHub
 */
public class HomeCitySearchResult
{
    private boolean configured;

    private String message;

    private List<HomeCity> cities = new ArrayList<>();

    public boolean isConfigured()
    {
        return configured;
    }

    public void setConfigured(boolean configured)
    {
        this.configured = configured;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public List<HomeCity> getCities()
    {
        return cities;
    }

    public void setCities(List<HomeCity> cities)
    {
        this.cities = cities;
    }
}
