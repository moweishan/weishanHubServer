package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 请求配置校验测试，覆盖 URL 协议、方法、header/query/body 和后端限制。
 */
class StressRequestValidatorTest
{
    private StressRequestValidator validator;

    @BeforeEach
    void setUp()
    {
        validator = new StressRequestValidator(new ObjectMapper());
    }

    @Test
    void validConfigIsNormalized()
    {
        StressRequestConfig config = validConfig();
        config.setHttpMethod("post");
        config.setHeadersJson("{\"X-Phone\":\"{{phone}}\"}");
        config.setQueryParamsJson("{\"q\":\"a b\"}");
        config.setEnabled(null);
        config.setTimeoutMs(null);

        StressRequestConfig result = validator.validateAndNormalize(config);

        assertEquals("POST", result.getHttpMethod());
        assertEquals("0", result.getEnabled());
        assertEquals(3000, result.getTimeoutMs().intValue());
        assertEquals("{\"X-Phone\":\"{{phone}}\"}", result.getHeadersJson());
    }

    @Test
    void rejectsNonHttpProtocols()
    {
        StressRequestConfig ftp = validConfig();
        ftp.setRequestUrl("ftp://example.com/file");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(ftp));

        StressRequestConfig javascript = validConfig();
        javascript.setRequestUrl("javascript:alert(1)");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(javascript));

        StressRequestConfig noHost = validConfig();
        noHost.setRequestUrl("http:///missing-host");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(noHost));
    }

    @Test
    void rejectsInvalidMethod()
    {
        StressRequestConfig config = validConfig();
        config.setHttpMethod("PATCH");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(config));
    }

    @Test
    void rejectsInvalidHeaders()
    {
        StressRequestConfig badJson = validConfig();
        badJson.setHeadersJson("[1,2]");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(badJson));

        StressRequestConfig badName = validConfig();
        badName.setHeadersJson("{\"Bad Header\":\"v\"}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(badName));

        StressRequestConfig crlf = validConfig();
        crlf.setHeadersJson("{\"X-Test\":\"a\\r\\nInjected: 1\"}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(crlf));

        StressRequestConfig restricted = validConfig();
        restricted.setHeadersJson("{\"Host\":\"evil.example.com\"}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(restricted));

        StressRequestConfig nonAscii = validConfig();
        nonAscii.setHeadersJson("{\"X-Test\":\"中文\"}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(nonAscii));
    }

    @Test
    void rejectsInvalidQueryValues()
    {
        StressRequestConfig nested = validConfig();
        nested.setQueryParamsJson("{\"a\":{\"b\":1}}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(nested));

        StressRequestConfig nullValue = validConfig();
        nullValue.setQueryParamsJson("{\"a\":null}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(nullValue));
    }

    @Test
    void validatesJsonAndFormBodies()
    {
        StressRequestConfig invalidJson = validConfig();
        invalidJson.setBodyType("JSON");
        invalidJson.setBodyContent("{\"phone\": }");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(invalidJson));

        StressRequestConfig invalidForm = validConfig();
        invalidForm.setBodyType("FORM");
        invalidForm.setBodyContent("{\"a\":{\"b\":1}}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(invalidForm));

        StressRequestConfig form = validConfig();
        form.setBodyType("FORM");
        form.setBodyContent("{\"phone\":\"{{phone}}\",\"tags\":[\"a\",\"b\"]}");
        validator.validateAndNormalize(form);

        StressRequestConfig json = validConfig();
        json.setBodyType("JSON");
        json.setBodyContent("{\"phone\":\"{{phone}}\",\"nested\":{\"ok\":true}}");
        validator.validateAndNormalize(json);

        StressRequestConfig unquoted = validConfig();
        unquoted.setBodyType("JSON");
        unquoted.setBodyContent("{\"phone\":{{phone}}}");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(unquoted));
    }

    @Test
    void getAndHeadCannotHaveBody()
    {
        StressRequestConfig config = validConfig();
        config.setHttpMethod("GET");
        config.setBodyType("RAW");
        config.setBodyContent("hello");
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(config));
    }

    @Test
    void timeoutMustBeInRange()
    {
        StressRequestConfig tooSmall = validConfig();
        tooSmall.setTimeoutMs(999);
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(tooSmall));

        StressRequestConfig tooLarge = validConfig();
        tooLarge.setTimeoutMs(600001);
        assertThrows(ServiceException.class, () -> validator.validateAndNormalize(tooLarge));
    }

    private StressRequestConfig validConfig()
    {
        StressRequestConfig config = new StressRequestConfig();
        config.setConfigName("测试接口");
        config.setRequestUrl("https://example.com/check?phone={{phone}}");
        config.setHttpMethod("POST");
        config.setHeadersJson("{\"X-Phone\":\"{{phone}}\"}");
        config.setQueryParamsJson("{\"q\":\"a b\"}");
        config.setBodyType("JSON");
        config.setBodyContent("{\"phone\":\"{{phone}}\"}");
        config.setTimeoutMs(10000);
        config.setEnabled("0");
        return config;
    }
}
