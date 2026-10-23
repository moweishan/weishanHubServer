package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.home.config.HomeWeatherProperties;
import com.ruoyi.project.home.domain.HomeWeatherResult;

/**
 * 天气服务：按高德真实契约分开请求 base 实况与 all 预报，缺 Key、私网/IPv6、定位失败、
 * 手动选城、超时/第三方异常、预报失败降级、缓存和敏感信息不回传。
 */
class HomeWeatherServiceTest
{
    private AmapClient amapClient;

    private HomeWeatherProperties properties;

    private HomeWeatherService service;

    @BeforeEach
    void setUp()
    {
        properties = new HomeWeatherProperties();
        properties.setAmapKey("super-secret-key");
        properties.setCacheMaxEntries(20);
        properties.setLocationCacheMinutes(5);
        properties.setWeatherCacheMinutes(10);
        amapClient = mock(AmapClient.class);
        when(amapClient.isConfigured()).thenReturn(true);
        service = new HomeWeatherService(amapClient, properties,
                Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void missingKeyReturnsExplicitStatusWithoutCallingAmap()
    {
        properties.setAmapKey("");
        when(amapClient.isConfigured()).thenReturn(false);
        HomeWeatherResult result = service.getWeather("8.8.8.8", null);
        assertEquals(HomeWeatherResult.STATUS_NO_KEY, result.getStatus());
        assertFalse(result.isConfigured());
        assertTrue(result.getMessage().contains("Key"));
        verify(amapClient, never()).get(anyString(), anyMap());
    }

    @Test
    void privateAndIpv6AddressesAskForManualCityWithoutCallingAmap()
    {
        assertEquals(HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                service.getWeather("127.0.0.1", null).getStatus());
        assertEquals(HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                service.getWeather("192.168.1.20", null).getStatus());
        assertEquals(HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                service.getWeather("2408:8207:1234::1", null).getStatus());
        assertEquals(HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                service.getWeather("unknown", null).getStatus());
        verify(amapClient, never()).get(anyString(), anyMap());
    }

    @Test
    void locateFailureAsksForManualCity()
    {
        when(amapClient.get(eq("/v3/ip"), anyMap())).thenReturn(emptyIpJson());
        HomeWeatherResult result = service.getWeather("8.8.8.8", null);
        assertEquals(HomeWeatherResult.STATUS_LOCATE_FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("手动选择城市"));
        assertNull(result.getWeather());
    }

    @Test
    void amapTimeoutDuringLocateIsShownAsLocateFailure()
    {
        when(amapClient.get(eq("/v3/ip"), anyMap())).thenThrow(new AmapException("高德接口请求超时或网络异常"));
        HomeWeatherResult result = service.getWeather("8.8.8.8", null);
        assertEquals(HomeWeatherResult.STATUS_LOCATE_FAILED, result.getStatus());
        assertFalse(result.getMessage().contains("super-secret-key"));
    }

    @Test
    void autoLocateCallsBaseAndAllSeparatelyAndReturnsAllDisplayFields()
    {
        when(amapClient.get(eq("/v3/ip"), anyMap())).thenReturn(ipJson());
        stubLiveAndForecast(liveJson("2026-01-01 10:00:00"), forecastJson("2026-01-01", "26", "17"));
        HomeWeatherResult result = service.getWeather("8.8.8.8", null);

        assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
        assertEquals("IP", result.getLocationMode());
        assertEquals("杭州市", result.getCity().getName());
        assertEquals("330100", result.getCity().getAdcode());
        assertEquals("多云", result.getWeather().getWeather());
        assertEquals("23", result.getWeather().getTemperature());
        assertEquals("26", result.getWeather().getMaxTemperature());
        assertEquals("17", result.getWeather().getMinTemperature());
        assertEquals("61", result.getWeather().getHumidity());
        assertEquals("东南风", result.getWeather().getWindDirection());
        assertEquals("3", result.getWeather().getWindPower());
        assertEquals("2026-01-01 10:00:00", result.getWeather().getReportTime());
        assertEquals("高德地图", result.getSource());
        assertNotNull(result.getUpdatedAt());

        verify(amapClient).get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions"))));
        verify(amapClient).get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions"))));

        String json = JSON.toJSONString(result);
        assertFalse(json.contains("super-secret-key"));
        assertFalse(json.contains("restapi.amap.com"));
    }

    @Test
    void repeatedIpAndCityCallsUseBoundedCaches()
    {
        when(amapClient.get(eq("/v3/ip"), anyMap())).thenReturn(ipJson());
        stubLiveAndForecast(liveJson("2026-01-01 10:00:00"), forecastJson("2026-01-01", "26", "17"));

        service.getWeather("8.8.8.8", null);
        service.getWeather("8.8.8.8", null);

        verify(amapClient, times(1)).get(eq("/v3/ip"), anyMap());
        verify(amapClient, times(1)).get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions"))));
        verify(amapClient, times(1)).get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions"))));
    }

    @Test
    void liveFailureFailsWeatherAndKeepsLocatedCity()
    {
        when(amapClient.get(eq("/v3/ip"), anyMap())).thenReturn(ipJson());
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions")))))
                .thenThrow(new AmapException("高德接口返回失败（10001）"));

        HomeWeatherResult result = service.getWeather("8.8.8.8", null);
        assertEquals(HomeWeatherResult.STATUS_WEATHER_FAILED, result.getStatus());
        assertEquals("IP", result.getLocationMode());
        assertEquals("杭州市", result.getCity().getName());
        assertNull(result.getWeather());
        assertTrue(result.getMessage().contains("稍后重试"));
        verify(amapClient, never()).get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions"))));
    }

    @Test
    void forecastFailureKeepsLiveWeatherWithoutFakeTemperatures()
    {
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions")))))
                .thenReturn(liveJson("2026-01-01 10:00:00"));
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions")))))
                .thenThrow(new AmapException("高德接口请求超时或网络异常"));

        HomeWeatherResult result = service.getWeather("8.8.8.8", "330100");

        assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
        assertEquals("多云", result.getWeather().getWeather());
        assertEquals("23", result.getWeather().getTemperature());
        assertNull(result.getWeather().getMaxTemperature());
        assertNull(result.getWeather().getMinTemperature());
    }

    @Test
    void todayCastUsesBeijingClockNotYesterdayReportTimeAndNotFirstCast()
    {
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions")))))
                .thenReturn(liveJson("2025-12-31 23:00:00"));
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions")))))
                .thenReturn(JSON.parseObject("{\"status\":\"1\",\"forecasts\":[{\"casts\":["
                        + "{\"date\":\"2025-12-31\",\"daytemp\":\"99\",\"nighttemp\":\"98\"},"
                        + "{\"date\":\"2026-01-01\",\"daytemp\":\"26\",\"nighttemp\":\"17\"}]}]}"));

        HomeWeatherResult result = service.getWeather("8.8.8.8", "330100");

        assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
        assertEquals("26", result.getWeather().getMaxTemperature());
        assertEquals("17", result.getWeather().getMinTemperature());
    }

    @Test
    void forecastWithoutTodayCastLeavesTemperaturesMissing()
    {
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions")))))
                .thenReturn(liveJson("2025-12-31 23:00:00"));
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions")))))
                .thenReturn(forecastJson("2025-12-31", "99", "98"));

        HomeWeatherResult result = service.getWeather("8.8.8.8", "330100");

        assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
        assertEquals("23", result.getWeather().getTemperature());
        assertNull(result.getWeather().getMaxTemperature());
        assertNull(result.getWeather().getMinTemperature());
    }

    @Test
    void manualCityUsesAdcodeAndDoesNotLocateByIp()
    {
        stubLiveAndForecast(liveJson("2026-01-01 10:00:00"), forecastJson("2026-01-01", "26", "17"));
        HomeWeatherResult result = service.getWeather("127.0.0.1", "330106");

        assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
        assertEquals("MANUAL", result.getLocationMode());
        assertEquals("杭州市", result.getCity().getName());
        verify(amapClient, never()).get(eq("/v3/ip"), anyMap());
    }

    @Test
    void malformedManualAdcodeIsRejected()
    {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getWeather("8.8.8.8", "abc"));
        assertEquals(400, error.getCode().intValue());
    }

    @Test
    void emptyLivesIsWeatherFailureNotFakeWeather()
    {
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions")))))
                .thenReturn(JSON.parseObject("{\"status\":\"1\",\"lives\":[]}"));
        HomeWeatherResult result = service.getWeather("8.8.8.8", "330100");
        assertEquals(HomeWeatherResult.STATUS_WEATHER_FAILED, result.getStatus());
        assertNull(result.getWeather());
    }

    private void stubLiveAndForecast(JSONObject live, JSONObject forecast)
    {
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "base".equals(params.get("extensions"))))).thenReturn(live);
        when(amapClient.get(eq("/v3/weather/weatherInfo"),
                argThat(params -> "all".equals(params.get("extensions"))))).thenReturn(forecast);
    }

    private JSONObject ipJson()
    {
        return JSON.parseObject("{\"status\":\"1\",\"province\":\"浙江省\",\"city\":\"杭州市\",\"adcode\":\"330100\"}");
    }

    private JSONObject emptyIpJson()
    {
        return JSON.parseObject("{\"status\":\"1\",\"province\":\"\",\"city\":\"\",\"adcode\":\"\"}");
    }

    private JSONObject liveJson(String reportTime)
    {
        return JSON.parseObject("{\"status\":\"1\",\"lives\":[{"
                + "\"province\":\"浙江省\",\"city\":\"杭州市\",\"adcode\":\"330100\","
                + "\"weather\":\"多云\",\"temperature\":\"23\",\"humidity\":\"61\","
                + "\"winddirection\":\"东南风\",\"windpower\":\"3\","
                + "\"reporttime\":\"" + reportTime + "\"}]}");
    }

    private JSONObject forecastJson(String date, String dayTemp, String nightTemp)
    {
        return JSON.parseObject("{\"status\":\"1\",\"forecasts\":[{\"casts\":[{"
                + "\"date\":\"" + date + "\",\"dayweather\":\"多云\",\"nightweather\":\"晴\","
                + "\"daytemp\":\"" + dayTemp + "\",\"nighttemp\":\"" + nightTemp + "\"}]}]}");
    }
}
