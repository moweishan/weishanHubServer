package com.ruoyi.project.notification.domain;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 调用凭证新增/修改请求。
 *
 * @author weishanHub
 */
public class NotificationCredentialRequest
{
    @NotBlank(message = "凭证名称不能为空")
    @Size(max = 100, message = "凭证名称不能超过 100 个字符")
    private String credentialName;

    @Pattern(regexp = "[01]", message = "启用状态只能是 0 或 1")
    private String enabled = "0";

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    public String getCredentialName()
    {
        return credentialName;
    }

    public void setCredentialName(String credentialName)
    {
        this.credentialName = credentialName;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
