package com.ruoyi.project.home.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.home.cache.BoundedTtlCache;
import com.ruoyi.project.home.config.HomeConfig;
import com.ruoyi.project.home.config.HomeWeatherProperties;
import com.ruoyi.project.home.domain.HomeCity;
import com.ruoyi.project.home.domain.HomeWeather;
import com.ruoyi.project.home.domain.HomeWeatherResult;

/**
 * 高德 IP 定位与天气查询。
 *
 * <p>只接受后端从当前请求解析出的访问者 IP，不接收客户端 IP 参数；IP 定位失败、私网/IPv6、
 * 海外或未知地址、高德超时和响应异常都转为可展示状态，不影响首页其他模块。</p>
 *
 * @author weishanHub
 */
@Service
public class HomeWeatherService
{
    private static final Logger log = LoggerFactory.getLogger(HomeWeatherService.class);

    private static final String SOURCE = "高德地图";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AmapClient amapClient;

    private final HomeWeatherProperties properties;

    private final Clock clock;

    private final BoundedTtlCache<String, HomeCity> locationCache;

    private final BoundedTtlCache<String, HomeWeather> weatherCache;

    public HomeWeatherService(AmapClient amapClient, HomeWeatherProperties properties,
            @Qualifier("homeClock") Clock clock)
    {
        this.amapClient = amapClient;
        this.properties = properties;
        this.clock = clock;
        int maxEntries = properties.getCacheMaxEntries();
        this.locationCache = new BoundedTtlCache<>(maxEntries,
                minutesToMillis(properties.getLocationCacheMinutes()));
        this.weatherCache = new BoundedTtlCache<>(maxEntries,
                minutesToMillis(properties.getWeatherCacheMinutes()));
    }

    /**
     * 查询天气。adcode 为空时按访问者 IP 自动定位；手动选择时只信任服务端查到的城市名称。
     */
    public HomeWeatherResult getWeather(String clientIp, String adcode)
    {
        HomeWeatherResult result = new HomeWeatherResult();
        result.setConfigured(amapClient.isConfigured());
        result.setUpdatedAt(nowText());
        if (!amapClient.isConfigured())
        {
            result.setStatus(HomeWeatherResult.STATUS_NO_KEY);
            result.setMessage("天气服务未配置高德 Web 服务 Key，请联系管理员配置后使用。");
            return result;
        }
        if (StringUtils.isNotBlank(adcode))
        {
            return manualWeather(result, adcode.trim());
        }
        IpAddressPolicy.Kind kind = IpAddressPolicy.classify(clientIp);
        if (kind == IpAddressPolicy.Kind.IPV6)
        {
            return pickCity(result, HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                    "当前访问地址为 IPv6，暂不支持自动定位，请手动选择城市。");
        }
        if (kind == IpAddressPolicy.Kind.UNKNOWN)
        {
            return pickCity(result, HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                    "无法识别当前访问地址，请手动选择城市。");
        }
        if (kind == IpAddressPolicy.Kind.PRIVATE_IPV4)
        {
            return pickCity(result, HomeWeatherResult.STATUS_UNSUPPORTED_IP,
                    "当前访问 IP 为内网或本地地址，无法自动定位，请手动选择城市。");
        }
        HomeCity located = locateByIp(clientIp);
        if (located == null)
        {
            return pickCity(result, HomeWeatherResult.STATUS_LOCATE_FAILED,
                    "未能根据当前 IP 识别中国大陆城市，请手动选择城市。");
        }
        result.setLocationMode("IP");
        result.setCity(located);
        HomeWeather weather = loadWeather(located.getAdcode());
        if (weather == null)
        {
            result.setStatus(HomeWeatherResult.STATUS_WEATHER_FAILED);
            result.setMessage("天气服务暂时不可用，请稍后重试。");
            return result;
        }
        applyWeather(result, weather, located);
        return result;
    }

    private HomeWeatherResult manualWeather(HomeWeatherResult result, String adcode)
    {
        if (!ChinaRegion.isMainlandAdcode(adcode))
        {
            throw new ServiceException("城市编码无效，请重新选择中国大陆城市或区县", 400);
        }
        HomeWeather weather = loadWeather(adcode);
        result.setLocationMode("MANUAL");
        if (weather == null)
        {
            result.setStatus(HomeWeatherResult.STATUS_WEATHER_FAILED);
            result.setMessage("天气服务暂时不可用，请稍后重试。");
            return result;
        }
        applyWeather(result, weather, null);
        return result;
    }

    private void applyWeather(HomeWeatherResult result, HomeWeather weather, HomeCity located)
    {
        HomeCity city = located == null ? new HomeCity() : located;
        if (StringUtils.isNotBlank(weather.getCityName()))
        {
            city.setName(weather.getCityName());
            city.setLabel(weather.getCityName());
        }
        if (StringUtils.isNotBlank(weather.getAdcode()))
        {
            city.setAdcode(weather.getAdcode());
        }
        result.setCity(city);
        result.setWeather(weather);
        result.setSource(SOURCE);
        result.setStatus(HomeWeatherResult.STATUS_OK);
        result.setMessage(null);
        result.setUpdatedAt(nowText());
    }

    private HomeWeatherResult pickCity(HomeWeatherResult result, String status, String message)
    {
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    private HomeCity locateByIp(String clientIp)
    {
        HomeCity cached = locationCache.get(clientIp);
        if (cached != null)
        {
            return cached;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ip", clientIp);
        try
        {
            JSONObject json = amapClient.get("/v3/ip", params);
            String province = text(json.get("province"));
            String cityName = text(json.get("city"));
            String adcode = text(json.get("adcode"));
            if (!ChinaRegion.isMainlandAdcode(adcode)
                    || (StringUtils.isBlank(cityName) && StringUtils.isBlank(province)))
            {
                return null;
            }
            String displayName = StringUtils.isNotBlank(cityName) ? cityName : province;
            HomeCity city = new HomeCity();
            city.setName(displayName);
            city.setLabel(displayName);
            city.setCity(cityName);
            city.setProvince(province);
            city.setAdcode(adcode);
            city.setLevel("city");
            locationCache.put(clientIp, city);
            return city;
        }
        catch (AmapException e)
        {
            log.warn("高德 IP 定位失败：{}", e.getMessage());
            return null;
        }
    }

    private HomeWeather loadWeather(String adcode)
    {
        HomeWeather cached = weatherCache.get(adcode);
        if (cached != null)
        {
            return cached;
        }
        // 高德 base 只返回 lives 实况，all 只返回 forecasts 预报，必须分两次请求。
        HomeWeather weather = loadLive(adcode);
        if (weather == null)
        {
            return null;
        }
        // 预报失败或没有今日 cast 时保留实况，高低温留空，不伪造天气。
        TodayTemperatures temperatures = loadTodayTemperatures(adcode);
        if (temperatures != null)
        {
            weather.setMaxTemperature(temperatures.max);
            weather.setMinTemperature(temperatures.min);
        }
        weatherCache.put(adcode, weather);
        return weather;
    }

    private HomeWeather loadLive(String adcode)
    {
        try
        {
            JSONObject json = amapClient.get("/v3/weather/weatherInfo", weatherParams(adcode, "base"));
            return parseLive(json, adcode);
        }
        catch (AmapException e)
        {
            log.warn("高德天气实况查询失败：{}", e.getMessage());
            return null;
        }
        catch (RuntimeException e)
        {
            log.warn("高德天气实况响应解析失败");
            return null;
        }
    }

    private TodayTemperatures loadTodayTemperatures(String adcode)
    {
        try
        {
            JSONObject json = amapClient.get("/v3/weather/weatherInfo", weatherParams(adcode, "all"));
            return parseTodayTemperatures(json);
        }
        catch (AmapException e)
        {
            log.warn("高德天气预报查询失败：{}", e.getMessage());
            return null;
        }
        catch (RuntimeException e)
        {
            log.warn("高德天气预报响应解析失败");
            return null;
        }
    }

    private Map<String, String> weatherParams(String adcode, String extensions)
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("city", adcode);
        params.put("extensions", extensions);
        return params;
    }

    private HomeWeather parseLive(JSONObject json, String requestedAdcode)
    {
        JSONArray lives = json.getJSONArray("lives");
        if (lives == null || lives.isEmpty())
        {
            return null;
        }
        JSONObject live = lives.getJSONObject(0);
        if (live == null)
        {
            return null;
        }
        String weatherText = text(live.get("weather"));
        if (StringUtils.isBlank(weatherText))
        {
            return null;
        }
        HomeWeather weather = new HomeWeather();
        weather.setCityName(firstNonBlank(text(live.get("city")), text(live.get("province"))));
        weather.setAdcode(firstNonBlank(text(live.get("adcode")), requestedAdcode));
        weather.setWeather(weatherText);
        weather.setTemperature(text(live.get("temperature")));
        weather.setHumidity(text(live.get("humidity")));
        weather.setWindDirection(text(live.get("winddirection")));
        weather.setWindPower(text(live.get("windpower")));
        weather.setReportTime(text(live.get("reporttime")));
        weather.setFetchedAt(nowText());
        return weather;
    }

    /**
     * 只取北京时间的“今日”cast；不以昨日 reporttime 为今日，也不在无匹配时回退第一条预报。
     */
    private TodayTemperatures parseTodayTemperatures(JSONObject json)
    {
        JSONArray forecasts = json.getJSONArray("forecasts");
        if (forecasts == null || forecasts.isEmpty())
        {
            return null;
        }
        JSONObject forecast = forecasts.getJSONObject(0);
        JSONArray casts = forecast == null ? null : forecast.getJSONArray("casts");
        if (casts == null || casts.isEmpty())
        {
            return null;
        }
        String today = LocalDate.now(clock.withZone(HomeConfig.BEIJING_ZONE)).toString();
        for (int i = 0; i < casts.size(); i++)
        {
            JSONObject cast = casts.getJSONObject(i);
            if (cast == null || !today.equals(text(cast.get("date"))))
            {
                continue;
            }
            Integer day = integer(cast.get("daytemp"));
            Integer night = integer(cast.get("nighttemp"));
            if (day == null || night == null)
            {
                return null;
            }
            return new TodayTemperatures(String.valueOf(Math.max(day, night)),
                    String.valueOf(Math.min(day, night)));
        }
        return null;
    }

    private static final class TodayTemperatures
    {
        private final String max;

        private final String min;

        private TodayTemperatures(String max, String min)
        {
            this.max = max;
            this.min = min;
        }
    }

    private String nowText()
    {
        return LocalDateTime.now(clock.withZone(HomeConfig.BEIJING_ZONE)).format(TIME_FORMAT);
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

    private Integer integer(Object value)
    {
        String text = text(value);
        if (StringUtils.isBlank(text))
        {
            return null;
        }
        try
        {
            return Integer.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String firstNonBlank(String first, String second)
    {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    private long minutesToMillis(int minutes)
    {
        return Math.max(1, minutes) * 60_000L;
    }
}
