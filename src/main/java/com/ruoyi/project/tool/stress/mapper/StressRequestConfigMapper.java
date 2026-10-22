package com.ruoyi.project.tool.stress.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;

/**
 * 压力测试请求配置数据层。
 *
 * @author weishanHub
 */
public interface StressRequestConfigMapper
{
    /** 查询配置列表 */
    List<StressRequestConfig> selectConfigList(StressRequestConfig config);

    /** 按ID查询配置 */
    StressRequestConfig selectConfigById(Long configId);

    /** 查询全部启用配置，按ID升序，用于生成任务快照 */
    List<StressRequestConfig> selectEnabledConfigs();

    /** 新增配置 */
    int insertConfig(StressRequestConfig config);

    /** 修改配置 */
    int updateConfig(StressRequestConfig config);

    /** 批量删除配置 */
    int deleteConfigByIds(Long[] configIds);

    /**
     * 查询试活目标：传 configIds 时只取指定接口，否则按查询条件过滤。
     */
    List<StressRequestConfig> selectProbeConfigs(@Param("query") StressRequestConfig query,
            @Param("configIds") Long[] configIds);

    /**
     * 回写单个接口的试活结果。
     */
    int updateProbeResult(@Param("configId") Long configId, @Param("probeStatus") String probeStatus,
            @Param("probeTime") Date probeTime, @Param("probeMessage") String probeMessage);

    /**
     * 停用全部试活失效且当前启用的配置，返回受影响行数。
     */
    int disableDeadConfigs(@Param("updateBy") String updateBy);

    /**
     * 查询全部配置的请求URL，用于导入时去重。
     */
    List<String> selectAllUrls();
}
