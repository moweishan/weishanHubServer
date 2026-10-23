package com.ruoyi.project.home.service;

/**
 * 中国大陆行政区划编码判断。只按省级行政编码范围过滤，不维护城市列表。
 *
 * <p>用于避免 IP 定位/城市搜索结果落到港澳台或国家编码，天气需求仅覆盖中国大陆。</p>
 *
 * @author weishanHub
 */
public final class ChinaRegion
{
    private ChinaRegion()
    {
    }

    public static boolean isMainlandAdcode(String adcode)
    {
        if (adcode == null || !adcode.matches("\\d{6}"))
        {
            return false;
        }
        int province = Integer.parseInt(adcode.substring(0, 2));
        return (province >= 11 && province <= 15)
                || (province >= 21 && province <= 23)
                || (province >= 31 && province <= 37)
                || (province >= 41 && province <= 46)
                || (province >= 50 && province <= 54)
                || (province >= 61 && province <= 65);
    }
}
