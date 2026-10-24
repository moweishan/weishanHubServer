package com.ruoyi.project.notification.service;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.project.notification.controller.NotificationDetailController;
import com.ruoyi.project.notification.controller.NotificationQuestionController;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/** 测试专用回环 HTTP 适配器；所有数据库调用排队回到父测试事务线程，结束时整体回滚。 */
final class NotificationQuestionBrowserProbe
{
    private NotificationQuestionBrowserProbe() { }

    static void run(NotificationQuestionService service, NotificationDetailService detail,
            NotificationRateLimitService limits, NotificationRecordMapper records, ObjectMapper json,
            String apiKey, String routeCode) throws Exception
    {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new NotificationQuestionController(service, limits),
                new NotificationDetailController(detail)).setControllerAdvice(new GlobalExceptionHandler()).build();
        BlockingQueue<Runnable> jobs = new LinkedBlockingQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectNode latest = json.createObjectNode();
        server.createContext("/", exchange -> {
            CompletableFuture<byte[]> done = new CompletableFuture<>();
            final byte[] input = org.springframework.util.StreamUtils.copyToByteArray(exchange.getRequestBody());
            jobs.add(() -> {
                try
                {
                    String path = exchange.getRequestURI().getPath();
                    if ("/__test/complete".equals(path))
                    {
                        latest.put("completed", true);
                        done.complete("{}".getBytes(StandardCharsets.UTF_8));
                        return;
                    }
                    if ("/__test/latest".equals(path))
                    {
                        if (latest.has("requestId")) latest.setAll(service.poll(apiKey, latest.path("requestId").asText()));
                        done.complete(json.writeValueAsBytes(latest));
                        return;
                    }
                    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(exchange.getRequestMethod()), path).content(input);
                    exchange.getRequestHeaders().forEach((name, values) -> request.header(name, values.toArray()));
                    byte[] output = mvc.perform(request).andReturn().getResponse().getContentAsByteArray();
                    JsonNode result = json.readTree(output);
                    if ("/open/notification-questions".equals(path) && result.path("code").asInt() == 200)
                    {
                        JsonNode data = result.path("data");
                        latest.put("requestId", data.path("requestId").asText());
                        latest.put("detailUrl", data.path("detailUrl").asText());
                        // 只在此隔离测试模拟首次发送成功，不启动发送器、不对外发送消息。
                        Date sent = new Date();
                        records.markFirstSent(data.path("recordId").asLong(), sent, new Date(sent.getTime() + 3600000));
                    }
                    done.complete(output);
                }
                catch (Exception e) { done.completeExceptionally(e); }
            });
            try
            {
                byte[] output = done.get(30, TimeUnit.SECONDS);
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, output.length);
                exchange.getResponseBody().write(output);
            }
            catch (Exception e)
            {
                byte[] output = "{\"code\":500,\"msg\":\"probe failed\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, output.length);
                exchange.getResponseBody().write(output);
            }
            finally { exchange.close(); }
        });
        server.start();
        Process child = null;
        try
        {
            ProcessBuilder builder = new ProcessBuilder("D:/deepSeekHarnessDesktop/DeepSeek Harness.exe", "--expose-internals", "--experimental-vm-modules",
                    new File("../plugins/deepseek/scripts/local-closed-loop.cjs").getCanonicalPath());
            builder.environment().put("ELECTRON_RUN_AS_NODE", "1");
            builder.environment().put("WH_PROBE_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort());
            builder.environment().put("WH_PROBE_KEY", apiKey);
            builder.environment().put("WH_PROBE_ROUTE", routeCode);
            builder.redirectErrorStream(true);
            builder.redirectOutput(new File("target/notification-question-browser-probe.log"));
            child = builder.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75);
            while (child.isAlive() && System.nanoTime() < deadline)
            {
                Runnable job = jobs.poll(100, TimeUnit.MILLISECONDS);
                if (job != null) job.run();
            }
            if (child.isAlive()) throw new IllegalStateException("本地闭环验证超时");
            if (child.exitValue() != 0) throw new IllegalStateException("本地闭环验证进程失败");
            if (!"APPLIED".equals(latest.path("state").asText())) throw new IllegalStateException("缺少应用回执");
            if (!latest.path("completed").asBoolean()) throw new IllegalStateException("浏览器与原生工具完整断言尚未完成");
            System.out.println("PASS_LOCAL_CLOSED_LOOP: native tool -> HTTP controllers -> MySQL -> Vue form -> native answer -> APPLIED; business rows rolled back");
        }
        finally
        {
            if (child != null && child.isAlive()) child.destroyForcibly();
            server.stop(0);
        }
    }
}
