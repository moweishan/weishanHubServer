package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.project.home.config.HomeWeatherProperties;
import com.ruoyi.project.home.domain.HomeCitySearchResult;

/**
 * 城市搜索：未配置提示、行政区划解析、排除省份/非法项、缓存和第三方异常。
 */
class HomeCitySearchServiceTest
{
    private AmapClient amapClient;

    private HomeWeatherProperties properties;

    private HomeCitySearchService service;

    @BeforeEach
    void setUp()
    {
        properties = new HomeWeatherProperties();
        properties.setAmapKey("test-key");
        amapClient = mock(AmapClient.class);
        when(amapClient.isConfigured()).thenReturn(true);
        service = new HomeCitySearchService(amapClient, properties);
    }

    @Test
    void missingKeyReturnsExplicitMessageWithoutNetwork()
    {
        when(amapClient.isConfigured()).thenReturn(false);
        HomeCitySearchResult result = service.search("杭州");
        assertFalse(result.isConfigured());
        assertTrue(result.getMessage().contains("Key"));
        assertTrue(result.getCities().isEmpty());
        verify(amapClient, never()).get(anyString(), anyMap());
    }

    @Test
    void searchParsesCitiesAndDistrictsWithCanonicalNames()
    {
        when(amapClient.get(eq("/v3/config/district"), anyMap())).thenReturn(JSON.parseObject(
                "{\"status\":\"1\",\"districts\":["
                + "{\"level\":\"province\",\"name\":\"浙江省\",\"adcode\":\"330000\"},"
                + "{\"level\":\"city\",\"name\":\"杭州市\",\"adcode\":\"330100\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\"},"
                + "{\"level\":\"district\",\"name\":\"西湖区\",\"adcode\":\"330106\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\"},"
                + "{\"level\":\"district\",\"name\":\"西湖区\",\"adcode\":\"330106\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\"},"
                + "{\"level\":\"district\",\"name\":\"坏数据\",\"adcode\":\"abc\"}"
                + "]}"));
        HomeCitySearchResult result = service.search(" 杭州 ");

        assertTrue(result.isConfigured());
        assertEquals(2, result.getCities().size());
        assertEquals("浙江省 · 杭州市", result.getCities().get(0).getLabel());
        assertEquals("杭州市 · 西湖区", result.getCities().get(1).getLabel());
        assertEquals("330106", result.getCities().get(1).getAdcode());
        assertEquals("district", result.getCities().get(1).getLevel());
    }

    @Test
    void municipalityProvinceLevelIsSelectable()
    {
        when(amapClient.get(eq("/v3/config/district"), anyMap())).thenReturn(JSON.parseObject(
                "{\"status\":\"1\",\"districts\":["
                + "{\"level\":\"province\",\"name\":\"北京市\",\"adcode\":\"110000\","
                + "\"province\":\"北京市\",\"city\":\"北京市\"},"
                + "{\"level\":\"province\",\"name\":\"天津市\",\"adcode\":\"120000\","
                + "\"province\":\"天津市\",\"city\":\"天津市\"},"
                + "{\"level\":\"province\",\"name\":\"上海市\",\"adcode\":\"310000\","
                + "\"province\":\"上海市\",\"city\":\"上海市\"},"
                + "{\"level\":\"province\",\"name\":\"重庆市\",\"adcode\":\"500000\","
                + "\"province\":\"重庆市\",\"city\":\"重庆市\"},"
                + "{\"level\":\"district\",\"name\":\"朝阳区\",\"adcode\":\"110105\","
                + "\"province\":\"北京市\",\"city\":\"北京市\"}"
                + "]}"));
        HomeCitySearchResult result = service.search("北京");

        assertEquals(5, result.getCities().size());
        assertEquals("北京市", result.getCities().get(0).getName());
        assertEquals("110000", result.getCities().get(0).getAdcode());
        assertEquals("province", result.getCities().get(0).getLevel());
        assertEquals("北京市", result.getCities().get(0).getLabel());
        assertEquals("120000", result.getCities().get(1).getAdcode());
        assertEquals("310000", result.getCities().get(2).getAdcode());
        assertEquals("500000", result.getCities().get(3).getAdcode());
    }

    @Test
    void nonMunicipalityProvinceAndSpecialRegionsAreExcluded()
    {
        when(amapClient.get(eq("/v3/config/district"), anyMap())).thenReturn(JSON.parseObject(
                "{\"status\":\"1\",\"districts\":["
                + "{\"level\":\"province\",\"name\":\"浙江省\",\"adcode\":\"330000\"},"
                + "{\"level\":\"province\",\"name\":\"香港特别行政区\",\"adcode\":\"810000\"},"
                + "{\"level\":\"province\",\"name\":\"澳门特别行政区\",\"adcode\":\"820000\"},"
                + "{\"level\":\"province\",\"name\":\"台湾省\",\"adcode\":\"710000\"},"
                + "{\"level\":\"city\",\"name\":\"杭州市\",\"adcode\":\"330100\","
                + "\"province\":\"浙江省\",\"city\":\"杭州市\"}"
                + "]}"));
        HomeCitySearchResult result = service.search("中国");

        assertEquals(1, result.getCities().size());
        assertEquals("杭州市", result.getCities().get(0).getName());
        assertEquals("330100", result.getCities().get(0).getAdcode());
    }

    @Test
    void sameKeywordIsCached()
    {
        when(amapClient.get(eq("/v3/config/district"), anyMap())).thenReturn(JSON.parseObject(
                "{\"status\":\"1\",\"districts\":[{\"level\":\"city\",\"name\":\"杭州市\","
                + "\"adcode\":\"330100\",\"province\":\"浙江省\",\"city\":\"杭州市\"}]}"));
        service.search("杭州");
        service.search("杭州");
        verify(amapClient, times(1)).get(eq("/v3/config/district"), anyMap());
    }

    @Test
    void blankKeywordDoesNotCallAmap()
    {
        HomeCitySearchResult result = service.search("  ");
        assertTrue(result.getCities().isEmpty());
        assertTrue(result.getMessage().contains("请输入"));
        verify(amapClient, never()).get(anyString(), anyMap());
    }

    @Test
    void thirdPartyFailureReturnsFriendlyMessage()
    {
        when(amapClient.get(eq("/v3/config/district"), anyMap()))
                .thenThrow(new AmapException("高德接口请求超时或网络异常"));
        HomeCitySearchResult result = service.search("杭州");
        assertTrue(result.getCities().isEmpty());
        assertTrue(result.getMessage().contains("暂时不可用"));
        assertFalse(result.getMessage().contains("test-key"));
    }
}
