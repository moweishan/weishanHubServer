package com.ruoyi.project.tool.stress.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTaskResponse;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskResponseMapper;
import com.ruoyi.project.tool.stress.runner.StressHttpResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 响应记录测试：超长响应截断并标记，成功/失败计数分别累加。
 */
class StressTaskResponseServiceTest
{
    private StressTaskResponseMapper responseMapper;

    private StressTaskMapper taskMapper;

    private StressTaskResponseService service;

    @BeforeEach
    void setUp()
    {
        responseMapper = mock(StressTaskResponseMapper.class);
        taskMapper = mock(StressTaskMapper.class);
        service = new StressTaskResponseService(responseMapper, taskMapper);
        when(responseMapper.insertResponse(any(StressTaskResponse.class))).thenReturn(1);
        when(taskMapper.incrementCounters(anyLong(), anyInt(), anyInt())).thenReturn(1);
    }

    @Test
    void truncatesResponseAndMarksIt()
    {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < StressTaskResponseService.MAX_RESPONSE_CHARS + 100; i++)
        {
            huge.append('a');
        }
        StressHttpResult result = StressHttpResult.response(200, huge.toString(), false);

        service.recordResult(1L, snapshot(), result, 12L);

        ArgumentCaptor<StressTaskResponse> captor = ArgumentCaptor.forClass(StressTaskResponse.class);
        verify(responseMapper).insertResponse(captor.capture());
        assertEquals(StressTaskResponseService.MAX_RESPONSE_CHARS, captor.getValue().getResponseText().length());
        assertEquals("1", captor.getValue().getTruncated());
        assertEquals("1", captor.getValue().getSuccess());
        verify(taskMapper).incrementCounters(eq(1L), eq(1), eq(0));
    }

    @Test
    void recordsFailureAndTruncatesError()
    {
        StringBuilder error = new StringBuilder();
        for (int i = 0; i < StressTaskResponseService.MAX_ERROR_CHARS + 50; i++)
        {
            error.append('e');
        }
        StressHttpResult result = StressHttpResult.failure(error.toString());

        service.recordResult(2L, snapshot(), result, 3L);

        ArgumentCaptor<StressTaskResponse> captor = ArgumentCaptor.forClass(StressTaskResponse.class);
        verify(responseMapper).insertResponse(captor.capture());
        assertEquals(StressTaskResponseService.MAX_ERROR_CHARS, captor.getValue().getErrorMessage().length());
        assertEquals("0", captor.getValue().getSuccess());
        assertEquals("0", captor.getValue().getTruncated());
        verify(taskMapper).incrementCounters(eq(2L), eq(0), eq(1));
    }

    private StressRequestConfigSnapshot snapshot()
    {
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(5L);
        snapshot.setConfigName("接口");
        snapshot.setRequestUrl("https://example.com");
        snapshot.setHttpMethod("GET");
        return snapshot;
    }
}
