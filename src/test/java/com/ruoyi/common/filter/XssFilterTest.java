package com.ruoyi.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

/**
 * XSS 过滤器回归：仅请求配置路径保留原始 JSON 字符，其余 /tool 路径仍执行过滤。
 */
class XssFilterTest
{
    private static final String EXCLUDES =
            "/system/notice,/tool/stress/config,/tool/stress/config/**";

    @Test
    void configPathBodyAndUrlArePreserved() throws Exception
    {
        String json = "{\"requestUrl\":\"https://example.com/a?x=1&y=<tag>\","
                + "\"bodyContent\":\"<xml>a&b</xml>\"}";

        assertPreserved("/tool/stress/config", "POST", json);
        assertPreserved("/tool/stress/config/1", "PUT", json);
    }

    @Test
    void otherToolPathStillUsesXssFilter() throws Exception
    {
        String json = "{\"remark\":\"<script>alert(1)</script>\"}";
        MockHttpServletRequest request = jsonRequest("POST", "/tool/stress/task", json);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> received = new AtomicReference<ServletRequest>();
        AtomicReference<String> body = new AtomicReference<String>();
        FilterChain chain = (req, res) -> {
            received.set(req);
            body.set(StreamUtils.copyToString(req.getInputStream(), StandardCharsets.UTF_8));
        };

        XssFilter filter = filter();
        filter.doFilter(request, response, chain);

        assertNotSame(request, received.get());
        assertFalse(body.get().contains("<script>"));
        assertTrue(body.get().contains("alert(1)"));
    }

    private void assertPreserved(String path, String method, String json) throws Exception
    {
        MockHttpServletRequest request = jsonRequest(method, path, json);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> received = new AtomicReference<ServletRequest>();
        AtomicReference<String> body = new AtomicReference<String>();
        FilterChain chain = (req, res) -> {
            received.set(req);
            body.set(StreamUtils.copyToString(req.getInputStream(), StandardCharsets.UTF_8));
        };

        XssFilter filter = filter();
        filter.doFilter(request, response, chain);

        assertSame(request, received.get());
        assertEquals(json, body.get());
    }

    private MockHttpServletRequest jsonRequest(String method, String path, String json)
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setContentType("application/json");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private XssFilter filter() throws Exception
    {
        XssFilter filter = new XssFilter();
        MockFilterConfig filterConfig = new MockFilterConfig();
        filterConfig.addInitParameter("excludes", EXCLUDES);
        filter.init(filterConfig);
        return filter;
    }
}
