package com.ruoyi.project.tool.stress.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTaskResponse;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.mapper.StressTaskResponseMapper;
import com.ruoyi.project.tool.stress.runner.StressHttpResult;

/**
 * 请求结果持久化服务。
 *
 * <p>响应文本在传输层已按 64KB 截断，这里再做字符数保护；插入结果与累加计数在同一事务内完成。</p>
 *
 * @author weishanHub
 */
@Service
public class StressTaskResponseService
{
    /** 单条响应最多保存的字符数 */
    public static final int MAX_RESPONSE_CHARS = 64 * 1024;

    /** 错误信息最多保存的字符数，与表字段长度一致 */
    public static final int MAX_ERROR_CHARS = 1000;

    private final StressTaskResponseMapper responseMapper;

    private final StressTaskMapper taskMapper;

    public StressTaskResponseService(StressTaskResponseMapper responseMapper, StressTaskMapper taskMapper)
    {
        this.responseMapper = responseMapper;
        this.taskMapper = taskMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordResult(Long taskId, StressRequestConfigSnapshot snapshot, StressHttpResult result,
            long durationMs)
    {
        String responseText = result.getResponseText();
        boolean truncated = result.isTruncated();
        if (responseText != null && responseText.length() > MAX_RESPONSE_CHARS)
        {
            responseText = responseText.substring(0, MAX_RESPONSE_CHARS);
            truncated = true;
        }

        StressTaskResponse response = new StressTaskResponse();
        response.setTaskId(taskId);
        response.setConfigId(snapshot.getConfigId());
        response.setConfigName(snapshot.getConfigName());
        response.setRequestUrl(snapshot.getRequestUrl());
        response.setHttpMethod(snapshot.getHttpMethod());
        response.setStatusCode(result.getStatusCode());
        response.setDurationMs(durationMs);
        response.setSuccess(result.isSuccess() ? "1" : "0");
        response.setResponseText(responseText);
        response.setErrorMessage(truncate(result.getErrorMessage(), MAX_ERROR_CHARS));
        response.setTruncated(truncated ? "1" : "0");
        if (responseMapper.insertResponse(response) == 0)
        {
            throw new ServiceException("请求结果保存失败");
        }

        boolean success = result.isSuccess();
        if (taskMapper.incrementCounters(taskId, success ? 1 : 0, success ? 0 : 1) == 0)
        {
            throw new ServiceException("任务不存在，请求结果未保存");
        }
    }

    public List<StressTaskResponse> selectResponsesByTaskId(Long taskId)
    {
        return responseMapper.selectResponseListByTaskId(taskId);
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
