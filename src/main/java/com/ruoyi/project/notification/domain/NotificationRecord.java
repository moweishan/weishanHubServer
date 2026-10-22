package com.ruoyi.project.notification.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 通知记录。Markdown 原文保存在 detail，公共详情只返回必要字段。
 *
 * @author weishanHub
 */
@JsonIgnoreProperties({ "contentHash", "tokenHash", "tokenCipher", "deleted", "deletedTime", "routeCode" })
public class NotificationRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long recordId;

    private String requestId;

    private Long credentialId;

    /** 请求内容指纹，用于同 requestId 冲突判定 */
    @JsonIgnore
    private String contentHash;

    private String messageType;

    private String title;

    private String summary;

    /** 原始 Markdown / 文本，后台与公共详情按权限返回 */
    private String detail;

    /** 详情票据哈希，永不回显 */
    @JsonIgnore
    private String tokenHash;

    /** 详情票据密文，用于幂等重复调用返回同一链接，永不回显 */
    @JsonIgnore
    private String tokenCipher;

    /** 接单时的路径快照，逗号分隔 */
    private String routeCodes;

    /**
     * 接单时解析一次的详情页基地址快照。后续发送、重试、幂等返回与后台详情都使用该值；
     * 旧记录为空时兼容旧私密配置，绝不套用后来修改的数据库地址。
     */
    private String detailBaseUrl;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    /** 首个渠道成功时间；只允许 CAS 设置一次 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date sentAt;

    /** sentAt + 1 小时；任何后续成功/重试都不延长 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiresAt;

    /** 撤销标记：0 否，1 是 */
    private String revoked;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date revokedTime;

    private String revokedBy;

    /** 软删除标记：0 否，1 是 */
    @JsonIgnore
    private String deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonIgnore
    private Date deletedTime;

    /** 仅用于列表筛选，不映射数据库列 */
    @JsonIgnore
    private String routeCode;

    public Long getRecordId()
    {
        return recordId;
    }

    public void setRecordId(Long recordId)
    {
        this.recordId = recordId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public Long getCredentialId()
    {
        return credentialId;
    }

    public void setCredentialId(Long credentialId)
    {
        this.credentialId = credentialId;
    }

    public String getContentHash()
    {
        return contentHash;
    }

    public void setContentHash(String contentHash)
    {
        this.contentHash = contentHash;
    }

    public String getMessageType()
    {
        return messageType;
    }

    public void setMessageType(String messageType)
    {
        this.messageType = messageType;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getDetail()
    {
        return detail;
    }

    public void setDetail(String detail)
    {
        this.detail = detail;
    }

    public String getTokenHash()
    {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash)
    {
        this.tokenHash = tokenHash;
    }

    public String getTokenCipher()
    {
        return tokenCipher;
    }

    public void setTokenCipher(String tokenCipher)
    {
        this.tokenCipher = tokenCipher;
    }

    public String getRouteCodes()
    {
        return routeCodes;
    }

    public void setRouteCodes(String routeCodes)
    {
        this.routeCodes = routeCodes;
    }

    public String getDetailBaseUrl()
    {
        return detailBaseUrl;
    }

    public void setDetailBaseUrl(String detailBaseUrl)
    {
        this.detailBaseUrl = detailBaseUrl;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getTotalCount()
    {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount)
    {
        this.totalCount = totalCount;
    }

    public Integer getSuccessCount()
    {
        return successCount;
    }

    public void setSuccessCount(Integer successCount)
    {
        this.successCount = successCount;
    }

    public Integer getFailCount()
    {
        return failCount;
    }

    public void setFailCount(Integer failCount)
    {
        this.failCount = failCount;
    }

    public Date getSentAt()
    {
        return sentAt;
    }

    public void setSentAt(Date sentAt)
    {
        this.sentAt = sentAt;
    }

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
    }

    public String getRevoked()
    {
        return revoked;
    }

    public void setRevoked(String revoked)
    {
        this.revoked = revoked;
    }

    public Date getRevokedTime()
    {
        return revokedTime;
    }

    public void setRevokedTime(Date revokedTime)
    {
        this.revokedTime = revokedTime;
    }

    public String getRevokedBy()
    {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy)
    {
        this.revokedBy = revokedBy;
    }

    public String getDeleted()
    {
        return deleted;
    }

    public void setDeleted(String deleted)
    {
        this.deleted = deleted;
    }

    public Date getDeletedTime()
    {
        return deletedTime;
    }

    public void setDeletedTime(Date deletedTime)
    {
        this.deletedTime = deletedTime;
    }

    public String getRouteCode()
    {
        return routeCode;
    }

    public void setRouteCode(String routeCode)
    {
        this.routeCode = routeCode;
    }
}
