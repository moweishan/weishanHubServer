package com.ruoyi.project.tool.stress.mapper;

import java.util.List;
import com.ruoyi.project.tool.stress.domain.StressTaskResponse;

/**
 * 压力测试请求结果数据层。
 *
 * @author weishanHub
 */
public interface StressTaskResponseMapper
{
    /** 新增请求结果 */
    int insertResponse(StressTaskResponse response);

    /** 分页查询某任务的请求结果 */
    List<StressTaskResponse> selectResponseListByTaskId(Long taskId);

    /** 删除某任务的全部结果 */
    int deleteResponseByTaskId(Long taskId);
}
