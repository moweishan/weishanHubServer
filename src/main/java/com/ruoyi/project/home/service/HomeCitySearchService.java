package com.ruoyi.project.home.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.home.cache.BoundedTtlCache;
import com.ruoyi.project.home.config.HomeWeatherProperties;
import com.ruoyi.project.home.domain.HomeCity;
import com.ruoyi.project.home.domain.HomeCitySearchResult;

/**
 * 高德行政区划搜索：支持中国大陆城市与区县，不写死城市列表。
 *
 * @author weishanHub
 */
@Service
public class HomeCitySearchService
{
    private static final Logger log = LoggerFactory.getLogger(HomeCitySearchService.class);

    private static final int MAX_RESULTS = 20;

    /** 高德行政区划会把四个直辖市返回为 province，需要按 adcode 精确放行。 */
    private static final Set<String> MUNICIPALITY_ADCODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("110000", "120000", "310000", "500000")));

    private final AmapClient amapClient;

    private final BoundedTtlCache<String, List<HomeCity>> cache;

    public HomeCitySearchService(AmapClient amapClient, HomeWeatherProperties properties)
    {
        this.amapClient = amapClient;
        this.cache = new BoundedTtlCache<>(properties.getCacheMaxEntries(),
                Math.max(1, properties.getCityCacheMinutes()) * 60_000L);
    }

    public HomeCitySearchResult search(String keyword)
    {
        HomeCitySearchResult result = new HomeCitySearchResult();
        result.setConfigured(amapClient.isConfigured());
        if (!amapClient.isConfigured())
        {
            result.setMessage("未配置高德 Web 服务 Key，暂不能搜索城市，请联系管理员配置。");
            return result;
        }
        String value = keyword == null ? "" : keyword.trim();
        if (StringUtils.isBlank(value))
        {
            result.setMessage("请输入城市或区县名称");
            return result;
        }
        if (value.length() > 30)
        {
            result.setMessage("搜索关键词过长，请输入城市或区县名称");
            return result;
        }
        List<HomeCity> cached = cache.get(value);
        if (cached != null)
        {
            result.setCities(cached);
            return result;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("keywords", value);
        params.put("subdistrict", "0");
        params.put("extensions", "base");
        try
        {
            JSONObject json = amapClient.get("/v3/config/district", params);
            List<HomeCity> cities = parse(json);
            cache.put(value, cities);
            result.setCities(cities);
            if (cities.isEmpty())
            {
                result.setMessage("未找到匹配的城市或区县，请换个关键词");
            }
            return result;
        }
        catch (AmapException e)
        {
            log.warn("高德行政区划搜索失败：{}", e.getMessage());
            result.setMessage("城市搜索服务暂时不可用，请稍后重试。");
            return result;
        }
        catch (RuntimeException e)
        {
            log.warn("高德行政区划响应解析失败");
            result.setMessage("城市搜索服务暂时不可用，请稍后重试。");
            return result;
        }
    }

    private List<HomeCity> parse(JSONObject json)
    {
        JSONArray districts = json.getJSONArray("districts");
        if (districts == null || districts.isEmpty())
        {
            return Collections.emptyList();
        }
        List<HomeCity> cities = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < districts.size() && cities.size() < MAX_RESULTS; i++)
        {
            JSONObject district = districts.getJSONObject(i);
            if (district == null)
            {
                continue;
            }
            String level = text(district.get("level"));
            boolean municipality = "province".equals(level)
                    && MUNICIPALITY_ADCODES.contains(text(district.get("adcode")));
            if (!"city".equals(level) && !"district".equals(level) && !municipality)
            {
                continue;
            }
            String name = text(district.get("name"));
            String adcode = text(district.get("adcode"));
            if (StringUtils.isBlank(name) || !ChinaRegion.isMainlandAdcode(adcode) || !seen.add(adcode))
            {
                continue;
            }
            HomeCity city = new HomeCity();
            city.setName(name);
            city.setAdcode(adcode);
            city.setLevel(level);
            city.setProvince(text(district.get("province")));
            city.setCity(text(district.get("city")));
            city.setLabel(label(city));
            cities.add(city);
        }
        return cities;
    }

    private String label(HomeCity city)
    {
        String name = city.getName();
        String parent = "district".equals(city.getLevel()) ? city.getCity() : city.getProvince();
        if (StringUtils.isBlank(parent) || parent.equals(name))
        {
            if ("city".equals(city.getLevel()) && StringUtils.isNotBlank(city.getProvince()))
            {
                parent = city.getProvince();
            }
            else
            {
                return name;
            }
        }
        if (parent.equals(name))
        {
            return name;
        }
        return parent + " · " + name;
    }

    private String text(Object value)
    {
        if (value == null)
        {
            return "";
        }
        if (value instanceof String)
        {
            return ((String) value).trim();
        }
        if (value instanceof JSONArray)
        {
            JSONArray array = (JSONArray) value;
            return array.isEmpty() ? "" : text(array.get(0));
        }
        return String.valueOf(value).trim();
    }
}
