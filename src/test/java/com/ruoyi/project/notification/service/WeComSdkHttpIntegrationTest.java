package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;

/**
 * 纯本地 SDK HTTP 集成测试：JDK HttpServer 回环 + 真实 WxJava，核对实际序列化 JSON。
 */
class WeComSdkHttpIntegrationTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> sendBodies = new ArrayList<>();

    private HttpServer server;

    private volatile String sendResponse = "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"msg-1\"}";

    private WeComNotificationSender sender;

    private NotificationRecordChannel channel;

    private DefaultWxCpClientFactory realFactory;

    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cgi-bin/gettoken", exchange -> respond(exchange,
                "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"local-token\",\"expires_in\":7200}"));
        server.createContext("/cgi-bin/message/send", exchange -> {
            sendBodies.add(IOUtils.toString(exchange.getRequestBody(), StandardCharsets.UTF_8));
            respond(exchange, sendResponse);
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        realFactory = new DefaultWxCpClientFactory();
        sender = new WeComNotificationSender(baseUrlFactory(realFactory));

        channel = new NotificationRecordChannel();
        channel.setCorpId("corp");
        channel.setAgentId(1001);
        channel.setToUser("u1|u2");
        channel.setProvider("WECOM_APP");
    }

    @AfterEach
    void tearDown()
    {
        if (realFactory != null)
        {
            realFactory.shutdown();
        }
        if (server != null)
        {
            server.stop(0);
        }
    }

    @Test
    void actualTextCardJsonContainsOnlySummaryUrlAndRouting() throws Exception
    {
        NotificationRecord record = record("CARD", "订单告警", "订单 123 支付超时",
                "DETAIL-MARKER-不能进入卡片消息");

        NotificationSendResult result = sender.send(record, channel, "secret",
                "https://hub.example.com/notification/detail/token");

        assertTrue(result.isSuccess());
        assertEquals(1, sendBodies.size());
        String body = sendBodies.get(0);
        assertFalse(body.contains("DETAIL-MARKER"));
        JsonNode json = objectMapper.readTree(body);
        assertEquals("textcard", json.path("msgtype").asText());
        assertEquals(1001, json.path("agentid").asInt());
        assertEquals("u1|u2", json.path("touser").asText());
        assertEquals("订单告警", json.path("textcard").path("title").asText());
        assertEquals("订单 123 支付超时", json.path("textcard").path("description").asText());
        assertEquals("https://hub.example.com/notification/detail/token", json.path("textcard").path("url").asText());
        assertEquals("查看详情", json.path("textcard").path("btntxt").asText());
    }

    @Test
    void actualTextJsonUsesTextContent() throws Exception
    {
        NotificationRecord record = record("TEXT", "标题", "摘要", "TEXT-BODY-MARKER");

        NotificationSendResult result = sender.send(record, channel, "secret",
                "https://hub.example.com/notification/detail/token");

        assertTrue(result.isSuccess());
        JsonNode json = objectMapper.readTree(sendBodies.get(0));
        assertEquals("text", json.path("msgtype").asText());
        assertTrue(json.path("text").path("content").asText().contains("TEXT-BODY-MARKER"));
        assertTrue(json.path("text").path("content").asText().contains("https://hub.example.com/notification/detail/token"));
    }

    @Test
    void errorAndAllInvalidUsersAreNotFalseSuccess() throws Exception
    {
        sendResponse = "{\"errcode\":81013,\"errmsg\":\"invalid user\"}";
        assertFalse(sender.send(record("CARD", "标题", "摘要", "详情"), channel, "secret",
                "https://hub/detail/t").isSuccess());

        sendResponse = "{\"errcode\":0,\"errmsg\":\"ok\",\"msgid\":\"msg-2\",\"invaliduser\":\"u1|u2\"}";
        assertFalse(sender.send(record("CARD", "标题", "摘要", "详情"), channel, "secret",
                "https://hub/detail/t").isSuccess());
    }

    @Test
    void newFactoryRemainsUsableAfterOldFactoryShutdown() throws Exception
    {
        NotificationRecord record = record("TEXT", "标题", "摘要", "LIFECYCLE-BODY");
        assertTrue(sender.send(record, channel, "secret", "https://hub/detail/t").isSuccess());
        CloseableHttpClient clientA = realFactory.currentClient();
        assertNotNull(clientA);
        realFactory.shutdown();

        DefaultWxCpClientFactory factoryB = new DefaultWxCpClientFactory();
        try
        {
            WeComNotificationSender senderB = new WeComNotificationSender(baseUrlFactory(factoryB));
            assertTrue(senderB.send(record, channel, "secret", "https://hub/detail/t").isSuccess());
            assertNotNull(factoryB.currentClient());
            assertNotSame(clientA, factoryB.currentClient());
            assertThrows(IllegalStateException.class, () -> realFactory.create("corp", 1001, "secret"));
        }
        finally
        {
            factoryB.shutdown();
        }
    }

    private WxCpClientFactory baseUrlFactory(DefaultWxCpClientFactory factory)
    {
        return (corpId, agentId, secret) -> {
            WxCpService service = factory.create(corpId, agentId, secret);
            ((WxCpDefaultConfigImpl) service.getWxCpConfigStorage()).setBaseApiUrl(baseUrl);
            return service;
        };
    }

    private NotificationRecord record(String type, String title, String summary, String detail)
    {
        NotificationRecord record = new NotificationRecord();
        record.setMessageType(type);
        record.setTitle(title);
        record.setSummary(summary);
        record.setDetail(detail);
        return record;
    }

    private void respond(HttpExchange exchange, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(bytes);
        }
    }
}
