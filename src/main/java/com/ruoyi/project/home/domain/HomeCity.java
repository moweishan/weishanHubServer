package com.ruoyi.project.home.domain;

/**
 * 城市/区县信息。
 *
 * @author weishanHub
 */
public class HomeCity
{
    private String name;

    private String adcode;

    private String province;

    private String city;

    /** city / district，高德行政区划级别。 */
    private String level;

    /** 面向用户的完整名称，例如“杭州市 · 西湖区”。 */
    private String label;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getAdcode()
    {
        return adcode;
    }

    public void setAdcode(String adcode)
    {
        this.adcode = adcode;
    }

    public String getProvince()
    {
        return province;
    }

    public void setProvince(String province)
    {
        this.province = province;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getLevel()
    {
        return level;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }
}
