package com.ruoyi.project.tool.stress.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.project.tool.stress.domain.StressConfigImportResult;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;

/**
 * 压力测试请求配置服务。
 *
 * @author weishanHub
 */
public interface IStressRequestConfigService
{
    List<StressRequestConfig> selectConfigList(StressRequestConfig config);

    StressRequestConfig selectConfigById(Long configId);

    int insertConfig(StressRequestConfig config);

    int updateConfig(StressRequestConfig config);

    int deleteConfigByIds(Long[] configIds);

    List<StressRequestConfig> selectEnabledConfigs();

    /**
     * 一键停用全部试活失效且当前启用的配置。
     *
     * @param updateBy 操作人
     * @return 停用条数
     */
    int disableDeadConfigs(String updateBy);

    /**
     * 按查询条件导出配置，返回只包含可回导字段的有序 Map 列表。
     */
    List<Map<String, Object>> exportConfigs(StressRequestConfig query);

    /**
     * 从 JSON 文本导入配置：逐条按现有校验规则规范化，URL 已存在则跳过。
     *
     * @param json     JSON 文本，支持“配置数组”或 {"configs": [...]} 两种形态
     * @param operator 操作人，写入 create_by
     * @return 导入结果（总数/写入/跳过/失败与失败明细）
     */
    StressConfigImportResult importConfigs(String json, String operator);
}
