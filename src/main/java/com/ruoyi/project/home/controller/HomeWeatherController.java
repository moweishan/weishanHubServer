package com.ruoyi.project.home.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.home.domain.HomeCitySearchResult;
import com.ruoyi.project.home.domain.HomeWeatherResult;
import com.ruoyi.project.home.service.HomeCitySearchService;
import com.ruoyi.project.home.service.HomeWeatherService;

/**
 * 个人工作台天气接口。
 *
 * <p>自动定位只使用当前 HTTP 请求解析出的访问者 IP，接口不接受 ip 参数；
 * adcode 只用于用户手动选择城市。所有接口均要求登录，不新增匿名路径。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/home/weather")
public class HomeWeatherController
{
    private final HomeWeatherService weatherService;

    private final HomeCitySearchService citySearchService;

    public HomeWeatherController(HomeWeatherService weatherService, HomeCitySearchService citySearchService)
    {
        this.weatherService = weatherService;
        this.citySearchService = citySearchService;
    }

    @GetMapping
    public AjaxResult weather(@RequestParam(value = "adcode", required = false) String adcode,
            HttpServletRequest request)
    {
        String clientIp = IpUtils.getIpAddr(request);
        HomeWeatherResult result = weatherService.getWeather(clientIp, adcode);
        return AjaxResult.success(result);
    }

    @GetMapping("/cities")
    public AjaxResult cities(@RequestParam(value = "keyword", required = false) String keyword)
    {
        HomeCitySearchResult result = citySearchService.search(keyword);
        return AjaxResult.success(result);
    }
}
