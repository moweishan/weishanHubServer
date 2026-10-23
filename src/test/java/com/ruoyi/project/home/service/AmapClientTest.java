package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.ruoyi.project.home.config.HomeWeatherProperties;

/**
 * 高德客户端显式超时，异常信息不携带 Key/URL/原始响应。
 */
class AmapClientTest
{
    @Test
    void missingKeyFailsBeforeNetworkAccess()
    {
        HomeWeatherProperties properties = new HomeWeatherProperties();
        properties.setAmapKey(" ");
        AmapClient client = new AmapClient(properties);
        AmapException error = assertThrows(AmapException.class,
                () -> client.get("/v3/ip", Collections.singletonMap("ip", "8.8.8.8")));
        assertTrue(error.getMessage().contains("未配置"));
    }

    @Test
    void readTimeoutIsEnforcedAndKeyIsNotExposed() throws Exception
    {
        HomeWeatherProperties properties = new HomeWeatherProperties();
        properties.setAmapKey("super-secret-key");
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(150);

        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            Thread slowServer = new Thread(() -> {
                try (Socket socket = server.accept())
                {
                    Thread.sleep(800L);
                }
                catch (IOException | InterruptedException ignored)
                {
                    Thread.currentThread().interrupt();
                }
            }, "amap-slow-test-server");
            slowServer.setDaemon(true);
            slowServer.start();

            properties.setBaseUrl("http://127.0.0.1:" + server.getLocalPort());
            AmapClient client = new AmapClient(properties);
            long start = System.currentTimeMillis();
            AmapException error = assertThrows(AmapException.class,
                    () -> client.get("/v3/ip", Collections.singletonMap("ip", "8.8.8.8")));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 800L, "读取超时应远早于慢响应返回，实际 " + elapsed + "ms");
            assertFalse(error.getMessage().contains("super-secret-key"));
            assertFalse(error.getMessage().contains("http://"));
        }
    }
}
