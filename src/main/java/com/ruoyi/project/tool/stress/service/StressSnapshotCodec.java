package com.ruoyi.project.tool.stress.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;

/**
 * 接口快照 JSON 编解码。
 *
 * @author weishanHub
 */
@Component
public class StressSnapshotCodec
{
    private static final TypeReference<List<StressRequestConfigSnapshot>> SNAPSHOT_TYPE =
            new TypeReference<List<StressRequestConfigSnapshot>>()
            {
            };

    private final ObjectMapper objectMapper;

    public StressSnapshotCodec(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public String toJson(List<StressRequestConfigSnapshot> snapshots)
    {
        try
        {
            return objectMapper.writeValueAsString(snapshots);
        }
        catch (Exception e)
        {
            throw new ServiceException("接口配置快照序列化失败");
        }
    }

    public List<StressRequestConfigSnapshot> fromJson(String json)
    {
        if (StringUtils.isEmpty(StringUtils.trim(json)))
        {
            return new ArrayList<StressRequestConfigSnapshot>();
        }
        try
        {
            List<StressRequestConfigSnapshot> snapshots = objectMapper.readValue(json, SNAPSHOT_TYPE);
            return snapshots == null ? new ArrayList<StressRequestConfigSnapshot>() : snapshots;
        }
        catch (Exception e)
        {
            throw new ServiceException("接口配置快照解析失败");
        }
    }
}
