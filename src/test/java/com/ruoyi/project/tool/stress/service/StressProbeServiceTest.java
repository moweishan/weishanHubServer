package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressProbeProgress;
import com.ruoyi.project.tool.stress.domain.StressProbeRequest;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;
import com.ruoyi.project.tool.stress.runner.StressHttpResult;
import com.ruoyi.project.tool.stress.runner.StressHttpTransport;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 批量试活测试：2xx 记有效、非 2xx 与网络异常记失效并回写状态。
 */
class StressProbeServiceTest
{
    private StressRequestConfigMapper configMapper;

    private ExecutorService probeWorkers;

    private ExecutorService dispatcher;

    private StressProbeService service;

    @BeforeEach
    void setUp()
    {
        configMapper = mock(StressRequestConfigMapper.class);
        probeWorkers = Executors.newFixedThreadPool(2);
        dispatcher = Executors.newSingleThreadExecutor();
        when(configMapper.updateProbeResult(any(), any(), any(Date.class), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown()
    {
        if (service != null)
        {
            service.shutdown();
        }
        probeWorkers.shutdownNow();
        dispatcher.shutdownNow();
    }

    @Test
    void http2xxMarksAlive() throws Exception
    {
        service = newService(transportOf(StressHttpResult.response(200, "ok", false)));
        when(configMapper.selectProbeConfigs(any(), any()))
                .thenReturn(Collections.singletonList(config(1L, "有效接口")));

        StressProbeProgress progress = startAndWait();

        assertEquals(1, progress.getTotal());
        assertEquals(1, progress.getAlive());
        assertEquals(0, progress.getDead());
        assertFalse(progress.isRunning());
        verify(configMapper, timeout(2000)).updateProbeResult(eq(1L), eq(StressRequestConfig.PROBE_ALIVE),
                any(Date.class), any());
    }

    @Test
    void non2xxMarksDeadWithStatusCode() throws Exception
    {
        service = newService(transportOf(StressHttpResult.response(404, "not found", false)));
        when(configMapper.selectProbeConfigs(any(), any()))
                .thenReturn(Collections.singletonList(config(2L, "失效接口")));

        StressProbeProgress progress = startAndWait();

        assertEquals(1, progress.getDead());
        assertEquals(0, progress.getAlive());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(configMapper, timeout(2000)).updateProbeResult(eq(2L), eq(StressRequestConfig.PROBE_DEAD),
                any(Date.class), message.capture());
        assertTrue(message.getValue().contains("404"), "失效说明应包含状态码，实际=" + message.getValue());
    }

    @Test
    void networkErrorMarksDead() throws Exception
    {
        service = newService((request, cancellation) -> {
            throw new IOException("连接超时");
        });
        when(configMapper.selectProbeConfigs(any(), any()))
                .thenReturn(Collections.singletonList(config(3L, "异常接口")));

        StressProbeProgress progress = startAndWait();

        assertEquals(1, progress.getDead());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(configMapper, timeout(2000)).updateProbeResult(eq(3L), eq(StressRequestConfig.PROBE_DEAD),
                any(Date.class), message.capture());
        assertTrue(message.getValue().contains("连接超时"), "失效说明应包含异常信息，实际=" + message.getValue());
    }

    @Test
    void multipleTargetsAreAllProbed() throws Exception
    {
        service = newService(transportOf(StressHttpResult.response(200, "ok", false)));
        List<StressRequestConfig> targets = new ArrayList<StressRequestConfig>();
        targets.add(config(11L, "接口A"));
        targets.add(config(12L, "接口B"));
        when(configMapper.selectProbeConfigs(any(), any())).thenReturn(targets);

        StressProbeProgress progress = startAndWait();

        assertEquals(2, progress.getTotal());
        assertEquals(2, progress.getAlive());
        verify(configMapper, timeout(2000)).updateProbeResult(eq(11L), eq(StressRequestConfig.PROBE_ALIVE),
                any(Date.class), any());
        verify(configMapper, timeout(2000)).updateProbeResult(eq(12L), eq(StressRequestConfig.PROBE_ALIVE),
                any(Date.class), any());
    }

    @Test
    void defaultProbePhoneIsUndeliverable()
    {
        // 默认试活号码首位为 10，号码规划中不可能分配给个人用户，保证不会真的投递短信
        assertEquals("10000000000", StressProbeService.DEFAULT_PROBE_PHONE);
        assertFalse(StressProbeService.DEFAULT_PROBE_PHONE.matches("^1[3-9][0-9]{9}$"));
    }

    @Test
    void probeUsesUndeliverablePhoneByDefault() throws Exception
    {
        final AtomicReference<String> seen = new AtomicReference<String>();
        service = newService((request, cancellation) -> {
            seen.set(request.getUrl());
            return StressHttpResult.response(200, "ok", false);
        });
        when(configMapper.selectProbeConfigs(any(), any()))
                .thenReturn(Collections.singletonList(config(21L, "占位符接口")));

        StressProbeProgress progress = startAndWait();

        assertEquals(1, progress.getAlive());
        assertEquals(StressProbeService.DEFAULT_PROBE_PHONE, progress.getSamplePhone());
        assertTrue(seen.get() != null && seen.get().contains(StressProbeService.DEFAULT_PROBE_PHONE),
                "试活应使用不可投递号码替换占位符，实际=" + seen.get());
    }

    @Test
    void livePhoneIsRejectedUnlessExplicitlyAllowed()
    {
        service = new StressProbeService(configMapper, new StressTemplateRenderer(new ObjectMapper()),
                transportOf(StressHttpResult.response(200, "ok", false)), 2, probeWorkers, dispatcher,
                "13800138000", false);
        when(configMapper.selectProbeConfigs(any(), any()))
                .thenReturn(Collections.singletonList(config(31L, "接口")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.start(new StressProbeRequest()));
        assertTrue(error.getMessage().contains("真实可投递"), "异常信息应说明号码会真实投递，实际=" + error.getMessage());
        verify(configMapper, never()).updateProbeResult(any(), any(), any(), any());
    }

    private StressProbeProgress startAndWait() throws Exception
    {
        service.start(new StressProbeRequest());
        long deadline = System.currentTimeMillis() + 5000L;
        StressProbeProgress progress = service.progress();
        while (progress.isRunning() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(20L);
            progress = service.progress();
        }
        assertFalse(progress.isRunning(), "试活应在超时内结束");
        return progress;
    }

    private StressProbeService newService(StressHttpTransport transport)
    {
        return new StressProbeService(configMapper, new StressTemplateRenderer(new ObjectMapper()), transport, 2,
                probeWorkers, dispatcher);
    }

    private StressHttpTransport transportOf(StressHttpResult result)
    {
        return (request, cancellation) -> result;
    }

    private StressRequestConfig config(long configId, String name)
    {
        StressRequestConfig config = new StressRequestConfig();
        config.setConfigId(configId);
        config.setConfigName(name);
        config.setRequestUrl("https://example.test/check?phone={{phone}}");
        config.setHttpMethod("GET");
        config.setHeadersJson("{}");
        config.setQueryParamsJson("{}");
        config.setBodyType("RAW");
        config.setTimeoutMs(3000);
        config.setEnabled("0");
        config.setProbeStatus(StressRequestConfig.PROBE_UNTESTED);
        return config;
    }
}
