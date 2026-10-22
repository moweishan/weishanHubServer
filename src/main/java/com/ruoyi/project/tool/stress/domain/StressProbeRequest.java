package com.ruoyi.project.tool.stress.domain;

/**
 * 批量试活请求参数。
 *
 * <p>configIds 为空时按 configName / enabled / probeStatus 过滤出目标接口；
 * 传入 configIds 时只试活指定接口（前端勾选行）。</p>
 *
 * @author weishanHub
 */
public class StressProbeRequest
{
    /** 指定接口ID，为空表示按条件筛选 */
    private Long[] configIds;

    /** 接口名称模糊匹配 */
    private String configName;

    /** 启用状态：0 启用，1 停用 */
    private String enabled;

    /** 试活状态：untested / alive / dead */
    private String probeStatus;

    public Long[] getConfigIds()
    {
        return configIds;
    }

    public void setConfigIds(Long[] configIds)
    {
        this.configIds = configIds;
    }

    public String getConfigName()
    {
        return configName;
    }

    public void setConfigName(String configName)
    {
        this.configName = configName;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getProbeStatus()
    {
        return probeStatus;
    }

    public void setProbeStatus(String probeStatus)
    {
        this.probeStatus = probeStatus;
    }
}
