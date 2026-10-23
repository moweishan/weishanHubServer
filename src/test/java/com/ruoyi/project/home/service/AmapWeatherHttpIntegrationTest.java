package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.ruoyi.project.home.config.HomeWeatherProperties;
import com.ruoyi.project.home.domain.HomeWeatherResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 真实 AmapClient 的本地契约集成测试：JDK HttpServer 按 extensions 分流，
 * 只访问 127.0.0.1，不请求高德外网、不使用真实 Key。
 */
class AmapWeatherHttpIntegrationTest
{
    @Test
    void realClientRequestsBaseAndAllSeparately() throws Exception
    {
        AtomicInteger baseCalls = new AtomicInteger();
        AtomicInteger allCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v3/weather/weatherInfo", new HttpHandler()
        {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException
            {
                String query = exchange.getRequestURI().getQuery();
                String body;
                if (query != null && query.contains("extensions=base"))
                {
                    baseCalls.incrementAndGet();
                    body = "{\"status\":\"1\",\"lives\":[{"
                            + "\"province\":\"浙江省\",\"city\":\"杭州市\",\"adcode\":\"330100\","
                            + "\"weather\":\"多云\",\"temperature\":\"23\",\"humidity\":\"61\","
                            + "\"winddirection\":\"东南风\",\"windpower\":\"3\","
                            + "\"reporttime\":\"2026-01-01 10:00:00\"}]}";
                }
                else if (query != null && query.contains("extensions=all"))
                {
                    allCalls.incrementAndGet();
                    body = "{\"status\":\"1\",\"forecasts\":[{\"casts\":[{"
                            + "\"date\":\"2026-01-01\",\"daytemp\":\"26\",\"nighttemp\":\"17\"}]}]}";
                }
                else
                {
                    exchange.sendResponseHeaders(400, -1);
                    exchange.close();
                    return;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody())
                {
                    output.write(bytes);
                }
            }
        });
        server.start();
        try
        {
            HomeWeatherProperties properties = new HomeWeatherProperties();
            properties.setAmapKey("local-test-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setConnectTimeoutMs(1000);
            properties.setReadTimeoutMs(1000);
            AmapClient client = new AmapClient(properties);
            HomeWeatherService service = new HomeWeatherService(client, properties,
                    Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), ZoneOffset.UTC));

            HomeWeatherResult result = service.getWeather("127.0.0.1", "330100");

            assertEquals(HomeWeatherResult.STATUS_OK, result.getStatus());
            assertEquals("多云", result.getWeather().getWeather());
            assertEquals("23", result.getWeather().getTemperature());
            assertEquals("26", result.getWeather().getMaxTemperature());
            assertEquals("17", result.getWeather().getMinTemperature());
            assertNotNull(result.getUpdatedAt());
            assertEquals(1, baseCalls.get());
            assertEquals(1, allCalls.get());
        }
        finally
        {
            server.stop(0);
        }
    }
}
