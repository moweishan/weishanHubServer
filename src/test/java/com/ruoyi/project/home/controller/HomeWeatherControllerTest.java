package com.ruoyi.project.home.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.ruoyi.project.home.domain.HomeWeatherResult;
import com.ruoyi.project.home.service.HomeCitySearchService;
import com.ruoyi.project.home.service.HomeWeatherService;

/**
 * 天气接口按当前请求访问者 IP 查询，接口本身不接收 ip 参数。
 */
class HomeWeatherControllerTest
{
    @Test
    void weatherUsesForwardedVisitorIpInsteadOfServerIp()
    {
        HomeWeatherService weatherService = mock(HomeWeatherService.class);
        HomeCitySearchService citySearchService = mock(HomeCitySearchService.class);
        when(weatherService.getWeather("8.8.8.8", null)).thenReturn(new HomeWeatherResult());
        HomeWeatherController controller = new HomeWeatherController(weatherService, citySearchService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8, 10.0.0.1");
        controller.weather(null, request);

        verify(weatherService).getWeather("8.8.8.8", null);
    }

    @Test
    void controllerHasNoIpParameter() throws Exception
    {
        Method weather = HomeWeatherController.class.getMethod("weather", String.class,
                javax.servlet.http.HttpServletRequest.class);
        assertEquals(2, weather.getParameterCount());
        Method cities = HomeWeatherController.class.getMethod("cities", String.class);
        assertEquals(1, cities.getParameterCount());
    }
}
