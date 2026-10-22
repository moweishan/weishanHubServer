package com.ruoyi.project.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知中心配置。
 *
 * <p>主密钥与公网地址均为可选配置：缺少主密钥时只禁用渠道 Secret 保存和实际发送，
 * 不影响后台启动；缺少公网地址时拒绝受理新通知，避免用请求 Host 拼接不可信链接。</p>
 *
 * @author weishanHub
 */
@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties
{
    /**
     * 渠道 Secret / 详情票据的加密主密钥，建议至少 32 字节随机值。
     * 支持 notification.master-key 或环境变量 NOTIFICATION_MASTER_KEY。
     */
    private String masterKey;

    /**
     * 对外开放的前端站点地址，例如 https://hub.example.com；仅开发环境允许 localhost 使用 http。
     */
    private String publicBaseUrl;

    private final Dispatch dispatch = new Dispatch();

    private final RateLimit rateLimit = new RateLimit();

    public String getMasterKey()
    {
        return masterKey;
    }

    public void setMasterKey(String masterKey)
    {
        this.masterKey = masterKey;
    }

    public String getPublicBaseUrl()
    {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl)
    {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Dispatch getDispatch()
    {
        return dispatch;
    }

    public RateLimit getRateLimit()
    {
        return rateLimit;
    }

    /**
     * 异步发送扫描与线程池参数。
     */
    public static class Dispatch
    {
        /** 是否启用后台发送扫描 */
        private boolean enabled = true;

        /** 扫描间隔（毫秒） */
        private long scanIntervalMs = 2000L;

        /** 每轮最多领取的待发送渠道数 */
        private int batchSize = 20;

        /** 发送线程池核心线程数 */
        private int corePoolSize = 2;

        /** 发送线程池最大线程数 */
        private int maxPoolSize = 4;

        /** 发送线程池有界队列容量 */
        private int queueCapacity = 200;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public long getScanIntervalMs()
        {
            return scanIntervalMs;
        }

        public void setScanIntervalMs(long scanIntervalMs)
        {
            this.scanIntervalMs = scanIntervalMs;
        }

        public int getBatchSize()
        {
            return batchSize;
        }

        public void setBatchSize(int batchSize)
        {
            this.batchSize = batchSize;
        }

        public int getCorePoolSize()
        {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize)
        {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize()
        {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize)
        {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity()
        {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity)
        {
            this.queueCapacity = queueCapacity;
        }
    }

    /**
     * 开放接口基础限流参数（进程内固定窗口，多实例部署时按实例分别计算）。
     */
    public static class RateLimit
    {
        /** 窗口秒数 */
        private int windowSeconds = 60;

        /** 单个调用凭证每窗口允许的请求数 */
        private int perCredential = 60;

        /** 单个 IP 每窗口允许的请求数 */
        private int perIp = 120;

        public int getWindowSeconds()
        {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds)
        {
            this.windowSeconds = windowSeconds;
        }

        public int getPerCredential()
        {
            return perCredential;
        }

        public void setPerCredential(int perCredential)
        {
            this.perCredential = perCredential;
        }

        public int getPerIp()
        {
            return perIp;
        }

        public void setPerIp(int perIp)
        {
            this.perIp = perIp;
        }
    }
}
