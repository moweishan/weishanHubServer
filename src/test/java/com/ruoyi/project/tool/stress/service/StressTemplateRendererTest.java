package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.runner.StressHttpRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 手机号模板替换与编码测试。
 */
class StressTemplateRendererTest
{
    private ObjectMapper objectMapper;

    private StressTemplateRenderer renderer;

    @BeforeEach
    void setUp()
    {
        objectMapper = new ObjectMapper();
        renderer = new StressTemplateRenderer(objectMapper);
    }

    @Test
    void encodesUrlHeadersQueryAndJsonBody() throws Exception
    {
        StressRequestConfigSnapshot snapshot = snapshot();
        snapshot.setRequestUrl("https://example.com/check?phone={{phone}}&keep=1");
        snapshot.setHeadersJson("{\"X-Phone\":\"{{phone}}\",\"X-Static\":\"ok\"}");
        snapshot.setQueryParamsJson("{\"q\":\"a b\",\"plus\":\"+1\",\"tag\":[\"x\",\"y\"]}");
        snapshot.setBodyType("JSON");
        snapshot.setBodyContent("{\"phone\":\"{{phone}}\",\"nested\":{\"phone\":\"{{phone}}\"}}");

        StressHttpRequest request = renderer.render(snapshot, "+8613800138000");

        assertEquals(
                "https://example.com/check?phone=%2B8613800138000&keep=1&q=a+b&plus=%2B1&tag=x&tag=y",
                request.getUrl());
        assertEquals("+8613800138000", request.getHeaders().get("X-Phone"));
        assertEquals("ok", request.getHeaders().get("X-Static"));
        assertEquals("application/json;charset=UTF-8", request.getHeaders().get("Content-Type"));

        JsonNode body = objectMapper.readTree(new String(request.getBody(), StandardCharsets.UTF_8));
        assertEquals("+8613800138000", body.get("phone").asText());
        assertEquals("+8613800138000", body.get("nested").get("phone").asText());
    }

    @Test
    void jsonBodyEscapesPhoneSpecialCharacters()
    {
        StressRequestConfigSnapshot snapshot = snapshot();
        snapshot.setBodyType("JSON");
        snapshot.setBodyContent("{\"phone\":\"{{phone}}\"}");

        StressHttpRequest request = renderer.render(snapshot, "1\"2");

        String body = new String(request.getBody(), StandardCharsets.UTF_8);
        assertEquals("{\"phone\":\"1\\\"2\"}", body);
    }

    @Test
    void formBodyIsUrlEncoded()
    {
        StressRequestConfigSnapshot snapshot = snapshot();
        snapshot.setHttpMethod("POST");
        snapshot.setBodyType("FORM");
        snapshot.setBodyContent("{\"phone\":\"{{phone}}\",\"note\":\"a b\",\"tag\":[\"x\",\"y\"]}");

        StressHttpRequest request = renderer.render(snapshot, "+8613800138000");

        assertEquals("phone=%2B8613800138000&note=a+b&tag=x&tag=y",
                new String(request.getBody(), StandardCharsets.UTF_8));
        assertEquals("application/x-www-form-urlencoded;charset=UTF-8",
                request.getHeaders().get("Content-Type"));
    }

    @Test
    void getRequestHasNoBody()
    {
        StressRequestConfigSnapshot snapshot = snapshot();
        snapshot.setHttpMethod("GET");
        snapshot.setBodyType("RAW");
        snapshot.setBodyContent("should-not-send");

        StressHttpRequest request = renderer.render(snapshot, "13800138000");

        assertNull(request.getBody());
        assertFalse(request.getHeaders().containsKey("Content-Type"));
    }

    @Test
    void contentTypeOverrideAndExplicitHeaderWin()
    {
        StressRequestConfigSnapshot custom = snapshot();
        custom.setHttpMethod("POST");
        custom.setBodyType("RAW");
        custom.setBodyContent("hello");
        custom.setContentType("text/custom");
        StressHttpRequest customRequest = renderer.render(custom, "13800138000");
        assertEquals("text/custom", customRequest.getHeaders().get("Content-Type"));

        StressRequestConfigSnapshot explicit = snapshot();
        explicit.setHttpMethod("POST");
        explicit.setBodyType("RAW");
        explicit.setBodyContent("hello");
        explicit.setHeadersJson("{\"Content-Type\":\"application/octet-stream\"}");
        StressHttpRequest explicitRequest = renderer.render(explicit, "13800138000");
        assertEquals("application/octet-stream", explicitRequest.getHeaders().get("Content-Type"));
    }

    @Test
    void appendsQueryBeforeFragment()
    {
        StressRequestConfigSnapshot snapshot = snapshot();
        snapshot.setRequestUrl("https://example.com/a#frag");
        snapshot.setQueryParamsJson("{\"phone\":\"{{phone}}\"}");

        StressHttpRequest request = renderer.render(snapshot, "+8613800138000");

        assertTrue(request.getUrl().endsWith("#frag"));
        assertEquals("https://example.com/a?phone=%2B8613800138000#frag", request.getUrl());
    }

    private StressRequestConfigSnapshot snapshot()
    {
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(1L);
        snapshot.setConfigName("测试");
        snapshot.setRequestUrl("https://example.com/check");
        snapshot.setHttpMethod("POST");
        snapshot.setHeadersJson("{}");
        snapshot.setQueryParamsJson("{}");
        snapshot.setBodyType("RAW");
        snapshot.setTimeoutMs(10000);
        return snapshot;
    }
}
