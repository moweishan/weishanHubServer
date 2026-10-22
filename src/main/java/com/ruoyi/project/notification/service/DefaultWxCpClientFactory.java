package com.ruoyi.project.notification.service;

import javax.annotation.PreDestroy;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.stereotype.Component;
import me.chanjar.weixin.common.util.http.apache.ApacheHttpClientBuilder;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;

/**
 * 基于 WxJava weixin-java-cp 4.6.0 的企业微信客户端工厂。
 *
 * <p>每个 factory 实例自己拥有一个 Apache 连接池并在本实例内复用，避免使用 SDK 全局
 * DefaultApacheHttpClientBuilder 单例（DevTools 重载后可能拿到已关闭的全局 client）。
 * 显式设置 connect/socket/connectionRequest 超时为 5 秒，关闭 Apache 自动重试；SDK 业务层
 * maxRetryTimes 也设为 0。@PreDestroy 只关闭本实例拥有的 client/pool。</p>
 *
 * @author weishanHub
 */
@Component
public class DefaultWxCpClientFactory implements WxCpClientFactory
{
    private static final int TIMEOUT_MILLIS = 5000;

    private static final int MAX_TOTAL_CONNECTIONS = 20;

    private static final int MAX_CONNECTIONS_PER_ROUTE = 10;

    private final Object lock = new Object();

    private volatile CloseableHttpClient httpClient;

    private volatile PoolingHttpClientConnectionManager connectionManager;

    private volatile boolean closed;

    @Override
    public WxCpService create(String corpId, Integer agentId, String secret)
    {
        if (closed)
        {
            throw new IllegalStateException("通知企业微信客户端工厂已关闭");
        }
        WxCpDefaultConfigImpl config = new WxCpDefaultConfigImpl();
        config.setCorpId(corpId);
        config.setAgentId(agentId);
        config.setCorpSecret(secret);
        config.setApacheHttpClientBuilder(new FactoryApacheHttpClientBuilder(this));

        WxCpServiceImpl service = new WxCpServiceImpl();
        service.setWxCpConfigStorage(config);
        // SDK 业务层重试也关闭，失败交给人工重试，避免超时后盲目重复投递
        service.setMaxRetryTimes(0);
        service.setRetrySleepMillis(0);
        return service;
    }

    /**
     * 线程安全地获取本 factory 共享 client；关闭后拒绝再创建/获取。
     */
    CloseableHttpClient client()
    {
        if (closed)
        {
            throw new IllegalStateException("通知企业微信客户端工厂已关闭");
        }
        if (httpClient == null)
        {
            synchronized (lock)
            {
                if (closed)
                {
                    throw new IllegalStateException("通知企业微信客户端工厂已关闭");
                }
                if (httpClient == null)
                {
                    initClient();
                }
            }
        }
        return httpClient;
    }

    private void initClient()
    {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        manager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
        manager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(TIMEOUT_MILLIS).build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MILLIS)
                .setSocketTimeout(TIMEOUT_MILLIS)
                .setConnectionRequestTimeout(TIMEOUT_MILLIS)
                .build();
        httpClient = HttpClients.custom()
                .setConnectionManager(manager)
                // SDK getAccessToken 会关闭 getRequestHttpClient() 返回的 wrapper；共享 manager 防止误关本实例池
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .setRetryHandler((exception, executionCount, context) -> false)
                .build();
        connectionManager = manager;
    }

    /**
     * 测试可见：返回本实例当前 client，用于断言不同 factory 不共享对象。
     */
    CloseableHttpClient currentClient()
    {
        return httpClient;
    }

    /**
     * 只关闭本实例拥有的 client 与连接池；SDK/其他 factory 的池不受影响。
     */
    @PreDestroy
    public void shutdown()
    {
        CloseableHttpClient client;
        PoolingHttpClientConnectionManager manager;
        synchronized (lock)
        {
            closed = true;
            client = httpClient;
            manager = connectionManager;
            httpClient = null;
            connectionManager = null;
        }
        if (client != null)
        {
            try
            {
                client.close();
            }
            catch (Exception ignored)
            {
                // 应用停止阶段忽略关闭异常
            }
        }
        if (manager != null)
        {
            try
            {
                manager.shutdown();
            }
            catch (Exception ignored)
            {
                // 应用停止阶段忽略关闭异常
            }
        }
    }

    /**
     * 适配 WxJava 4.6.0 的 ApacheHttpClientBuilder 完整接口，让 SDK 使用本 factory 的共享 client。
     */
    static final class FactoryApacheHttpClientBuilder implements ApacheHttpClientBuilder
    {
        private final DefaultWxCpClientFactory factory;

        FactoryApacheHttpClientBuilder(DefaultWxCpClientFactory factory)
        {
            this.factory = factory;
        }

        @Override
        public CloseableHttpClient build()
        {
            return factory.client();
        }

        @Override
        public ApacheHttpClientBuilder httpProxyHost(String httpProxyHost)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder httpProxyPort(int httpProxyPort)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder httpProxyUsername(String httpProxyUsername)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder httpProxyPassword(String httpProxyPassword)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder httpRequestRetryHandler(HttpRequestRetryHandler httpRequestRetryHandler)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder keepAliveStrategy(ConnectionKeepAliveStrategy keepAliveStrategy)
        {
            return this;
        }

        @Override
        public ApacheHttpClientBuilder sslConnectionSocketFactory(
                SSLConnectionSocketFactory sslConnectionSocketFactory)
        {
            return this;
        }
    }
}
