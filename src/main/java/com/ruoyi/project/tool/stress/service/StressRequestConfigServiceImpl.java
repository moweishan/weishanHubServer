package com.ruoyi.project.tool.stress.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.tool.stress.domain.StressConfigImportResult;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.mapper.StressRequestConfigMapper;

/**
 * 压力测试请求配置服务实现。
 *
 * @author weishanHub
 */
@Service
public class StressRequestConfigServiceImpl implements IStressRequestConfigService
{
    /** 单次导入允许的最大条数，避免一次性导入超大文件 */
    public static final int MAX_IMPORT_ITEMS = 2000;

    private final StressRequestConfigMapper configMapper;

    private final StressRequestValidator validator;

    private final ObjectMapper objectMapper;

    public StressRequestConfigServiceImpl(StressRequestConfigMapper configMapper,
            StressRequestValidator validator)
    {
        this(configMapper, validator, new ObjectMapper());
    }

    @Autowired
    public StressRequestConfigServiceImpl(StressRequestConfigMapper configMapper,
            StressRequestValidator validator, ObjectMapper objectMapper)
    {
        this.configMapper = configMapper;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<StressRequestConfig> selectConfigList(StressRequestConfig config)
    {
        return configMapper.selectConfigList(config);
    }

    @Override
    public StressRequestConfig selectConfigById(Long configId)
    {
        return configMapper.selectConfigById(configId);
    }

    @Override
    public int insertConfig(StressRequestConfig config)
    {
        validator.validateAndNormalize(config);
        return configMapper.insertConfig(config);
    }

    @Override
    public int updateConfig(StressRequestConfig config)
    {
        if (config.getConfigId() == null || configMapper.selectConfigById(config.getConfigId()) == null)
        {
            throw new ServiceException("请求配置不存在");
        }
        validator.validateAndNormalize(config);
        int rows = configMapper.updateConfig(config);
        if (rows == 0)
        {
            throw new ServiceException("请求配置修改失败，请刷新后重试");
        }
        return rows;
    }

    @Override
    public int deleteConfigByIds(Long[] configIds)
    {
        if (configIds == null || configIds.length == 0)
        {
            return 0;
        }
        return configMapper.deleteConfigByIds(configIds);
    }

    @Override
    public List<StressRequestConfig> selectEnabledConfigs()
    {
        return configMapper.selectEnabledConfigs();
    }

    @Override
    public int disableDeadConfigs(String updateBy)
    {
        return configMapper.disableDeadConfigs(updateBy);
    }

    @Override
    public List<Map<String, Object>> exportConfigs(StressRequestConfig query)
    {
        List<StressRequestConfig> list = configMapper.selectProbeConfigs(query, null);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(list == null ? 0 : list.size());
        if (list == null)
        {
            return rows;
        }
        for (StressRequestConfig config : list)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("configName", config.getConfigName());
            row.put("requestUrl", config.getRequestUrl());
            row.put("httpMethod", config.getHttpMethod());
            row.put("headersJson", config.getHeadersJson());
            row.put("queryParamsJson", config.getQueryParamsJson());
            row.put("bodyType", config.getBodyType());
            row.put("bodyContent", config.getBodyContent());
            row.put("contentType", config.getContentType());
            row.put("timeoutMs", config.getTimeoutMs());
            row.put("enabled", config.getEnabled());
            row.put("remark", config.getRemark());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public StressConfigImportResult importConfigs(String json, String operator)
    {
        if (StringUtils.isEmpty(StringUtils.trim(json)))
        {
            throw new ServiceException("导入文件内容为空");
        }
        JsonNode root;
        try
        {
            root = objectMapper.readTree(json);
        }
        catch (Exception e)
        {
            throw new ServiceException("JSON 解析失败，请确认文件是合法的请求配置数组");
        }
        if (root != null && root.isObject() && root.has("configs"))
        {
            root = root.get("configs");
        }
        if (root == null || !root.isArray())
        {
            throw new ServiceException("JSON 内容应为请求配置数组，或形如 {\"configs\": [...]} 的对象");
        }
        if (root.size() == 0)
        {
            throw new ServiceException("导入文件里没有请求配置");
        }
        if (root.size() > MAX_IMPORT_ITEMS)
        {
            throw new ServiceException("单次最多导入 " + MAX_IMPORT_ITEMS + " 条配置，请拆分文件后再导入");
        }

        Set<String> existingUrls = new HashSet<String>();
        List<String> urls = configMapper.selectAllUrls();
        if (urls != null)
        {
            existingUrls.addAll(urls);
        }

        StressConfigImportResult result = new StressConfigImportResult();
        result.setTotal(root.size());
        Set<String> seenUrls = new HashSet<String>();

        for (int i = 0; i < root.size(); i++)
        {
            JsonNode node = root.get(i);
            String label = "第 " + (i + 1) + " 条";
            if (node == null || !node.isObject())
            {
                result.setFailed(result.getFailed() + 1);
                result.addError(label + "：不是 JSON 对象");
                continue;
            }
            StressRequestConfig config = new StressRequestConfig();
            config.setConfigName(text(node, "configName"));
            config.setRequestUrl(text(node, "requestUrl"));
            config.setHttpMethod(text(node, "httpMethod"));
            config.setHeadersJson(text(node, "headersJson"));
            config.setQueryParamsJson(text(node, "queryParamsJson"));
            config.setBodyType(text(node, "bodyType"));
            config.setBodyContent(text(node, "bodyContent"));
            config.setContentType(text(node, "contentType"));
            config.setTimeoutMs(integer(node, "timeoutMs"));
            config.setEnabled(text(node, "enabled"));
            config.setRemark(text(node, "remark"));
            config.setCreateBy(operator);

            String name = StringUtils.isEmpty(config.getConfigName()) ? "-" : config.getConfigName();
            try
            {
                validator.validateAndNormalize(config);
            }
            catch (ServiceException e)
            {
                result.setFailed(result.getFailed() + 1);
                result.addError(label + " " + name + "：" + e.getMessage());
                continue;
            }
            if (existingUrls.contains(config.getRequestUrl()) || !seenUrls.add(config.getRequestUrl()))
            {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            try
            {
                if (configMapper.insertConfig(config) > 0)
                {
                    result.setInserted(result.getInserted() + 1);
                }
                else
                {
                    result.setFailed(result.getFailed() + 1);
                    result.addError(label + " " + name + "：写入失败，请检查数据库");
                }
            }
            catch (Exception e)
            {
                result.setFailed(result.getFailed() + 1);
                result.addError(label + " " + name + "：写入失败（" + messageOf(e) + "）");
            }
        }
        return result;
    }

    private String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        if (value == null || value.isNull())
        {
            return null;
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private Integer integer(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        if (value == null || value.isNull())
        {
            return null;
        }
        if (value.isNumber())
        {
            return value.asInt();
        }
        try
        {
            return Integer.valueOf(value.asText().trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String messageOf(Exception e)
    {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty())
        {
            return e.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }
}
