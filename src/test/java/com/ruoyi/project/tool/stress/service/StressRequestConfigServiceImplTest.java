package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressConfigImportResult;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 请求配置服务测试：停用失效、JSON 导入导出。
 */
class StressRequestConfigServiceImplTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disableDeadConfigsDelegatesToMapper()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        when(mapper.disableDeadConfigs("tester")).thenReturn(37);
        StressRequestConfigServiceImpl service =
                new StressRequestConfigServiceImpl(mapper, mock(StressRequestValidator.class));

        assertEquals(37, service.disableDeadConfigs("tester"));
        verify(mapper).disableDeadConfigs("tester");
    }

    @Test
    void disableDeadConfigsReturnsZeroWhenNothingToDisable()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        when(mapper.disableDeadConfigs("tester")).thenReturn(0);
        StressRequestConfigServiceImpl service =
                new StressRequestConfigServiceImpl(mapper, mock(StressRequestValidator.class));

        assertEquals(0, service.disableDeadConfigs("tester"));
    }

    @Test
    void exportKeepsOnlyRoundTripFields()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        StressRequestConfig config = new StressRequestConfig();
        config.setConfigId(9L);
        config.setConfigName("接口A");
        config.setRequestUrl("https://example.test/check?phone={{phone}}");
        config.setHttpMethod("GET");
        config.setHeadersJson("{\"X-Phone\":\"{{phone}}\"}");
        config.setQueryParamsJson("{}");
        config.setBodyType("RAW");
        config.setBodyContent("");
        config.setContentType(null);
        config.setTimeoutMs(3000);
        config.setEnabled("0");
        config.setProbeStatus(StressRequestConfig.PROBE_DEAD);
        config.setRemark("备注");
        when(mapper.selectProbeConfigs(any(), any())).thenReturn(Collections.singletonList(config));
        StressRequestConfigServiceImpl service = newService(mapper);

        List<Map<String, Object>> rows = service.exportConfigs(new StressRequestConfig());

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("接口A", row.get("configName"));
        assertEquals("https://example.test/check?phone={{phone}}", row.get("requestUrl"));
        assertEquals(3000, row.get("timeoutMs"));
        assertFalse(row.containsKey("configId"), "导出不应包含 configId");
        assertFalse(row.containsKey("probeStatus"), "导出不应包含试活状态");
    }

    @Test
    void importInsertsValidRowsAndSkipsDuplicates()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        when(mapper.selectAllUrls()).thenReturn(Collections.singletonList("https://dup.test/a"));
        when(mapper.insertConfig(any(StressRequestConfig.class))).thenReturn(1);
        StressRequestConfigServiceImpl service = newService(mapper);

        String json = "[" + row("新接口", "https://new.test/a", "GET") + ","
                + row("重复接口", "https://dup.test/a", "GET") + ","
                + row("无效接口", "ftp://bad.test/a", "GET") + "]";

        StressConfigImportResult result = service.importConfigs(json, "tester");

        assertEquals(3, result.getTotal());
        assertEquals(1, result.getInserted());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getFailed());
        assertTrue(result.getErrors().get(0).contains("无效接口"));
        verify(mapper, times(1)).insertConfig(any(StressRequestConfig.class));
    }

    @Test
    void importSkipsDuplicatesInsideSameFile()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        when(mapper.selectAllUrls()).thenReturn(Collections.emptyList());
        when(mapper.insertConfig(any(StressRequestConfig.class))).thenReturn(1);
        StressRequestConfigServiceImpl service = newService(mapper);

        String json = "[" + row("A", "https://same.test/x", "GET") + ","
                + row("A2", "https://same.test/x", "POST") + "]";

        StressConfigImportResult result = service.importConfigs(json, "tester");

        assertEquals(1, result.getInserted());
        assertEquals(1, result.getSkipped());
    }

    @Test
    void importAcceptsConfigsEnvelope()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        when(mapper.selectAllUrls()).thenReturn(Collections.emptyList());
        when(mapper.insertConfig(any(StressRequestConfig.class))).thenReturn(1);
        StressRequestConfigServiceImpl service = newService(mapper);

        String json = "{\"configs\":[" + row("A", "https://env.test/x", "GET") + "]}";

        assertEquals(1, service.importConfigs(json, "tester").getInserted());
    }

    @Test
    void importRejectsInvalidPayloads()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        StressRequestConfigServiceImpl service = newService(mapper);

        assertThrows(ServiceException.class, () -> service.importConfigs("", "tester"));
        assertThrows(ServiceException.class, () -> service.importConfigs("not-json", "tester"));
        assertThrows(ServiceException.class, () -> service.importConfigs("{\"a\":1}", "tester"));
        assertThrows(ServiceException.class, () -> service.importConfigs("[]", "tester"));
        verify(mapper, never()).insertConfig(any(StressRequestConfig.class));
    }

    @Test
    void importRejectsOversizedFile()
    {
        StressRequestConfigMapper mapper = mock(StressRequestConfigMapper.class);
        StressRequestConfigServiceImpl service = newService(mapper);
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < StressRequestConfigServiceImpl.MAX_IMPORT_ITEMS + 1; i++)
        {
            if (i > 0)
            {
                builder.append(',');
            }
            builder.append(row("接口" + i, "https://bulk.test/" + i, "GET"));
        }
        builder.append(']');

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.importConfigs(builder.toString(), "tester"));
        assertTrue(error.getMessage().contains("最多导入"));
    }

    private StressRequestConfigServiceImpl newService(StressRequestConfigMapper mapper)
    {
        return new StressRequestConfigServiceImpl(mapper, new StressRequestValidator(objectMapper), objectMapper);
    }

    private String row(String name, String url, String method)
    {
        return "{\"configName\":\"" + name + "\",\"requestUrl\":\"" + url + "\",\"httpMethod\":\"" + method + "\""
                + ",\"headersJson\":\"{}\",\"bodyType\":\"RAW\",\"enabled\":\"0\",\"timeoutMs\":3000}";
    }
}
