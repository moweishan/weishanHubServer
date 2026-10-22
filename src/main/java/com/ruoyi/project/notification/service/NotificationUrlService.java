package com.ruoyi.project.notification.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.system.service.ISysConfigService;

/**
 * 详情链接地址解析与规范化。
 *
 * <p>新通知优先使用数据库参数 {@value #CONFIG_KEY}（由通知渠道页维护），未配置时回退私密配置
 * notification.public-base-url / NOTIFICATION_PUBLIC_BASE_URL；绝不使用请求 Host。
 * 历史记录没有地址快照时只回退旧私密配置，不套用后来写入数据库的新值。</p>
 *
 * <p>地址规则：必须是绝对 http/https 地址；http 仅允许 localhost / 127.0.0.1 / ::1；
 * 可带部署子路径与端口，去掉末尾斜杠；拒绝 userinfo、query、fragment、空白/控制字符与超长地址。</p>
 *
 * @author weishanHub
 */
@Component
public class NotificationUrlService
{
    /** 详情页地址在若依参数配置中的固定 key */
    public static final String CONFIG_KEY = "notification.publicBaseUrl";

    /** 与 sys_config.config_value、detail_base_url 字段长度保持一致 */
    public static final int MAX_LENGTH = 500;

    private final NotificationProperties properties;

    private final ISysConfigService configService;

    public NotificationUrlService(NotificationProperties properties, ISysConfigService configService)
    {
        this.properties = properties;
        this.configService = configService;
    }

    /**
     * 解析新通知使用的当前生效地址：数据库值优先，空值回退私密配置。
     */
    public String resolveBaseUrl()
    {
        String databaseValue = configService.selectConfigByKey(CONFIG_KEY);
        if (StringUtils.isNotBlank(databaseValue))
        {
            return normalizeConfigured(databaseValue, CONFIG_KEY);
        }
        String fallback = properties.getPublicBaseUrl();
        if (StringUtils.isBlank(fallback))
        {
            throw new ServiceException("未配置通知详情页地址，暂不能生成通知详情链接。请管理员在通知渠道页设置，"
                    + "或在后端配置 notification.public-base-url", 503);
        }
        return normalizeConfigured(fallback, "notification.public-base-url");
    }

    /**
     * 只解析旧私密配置，供没有地址快照的历史记录使用。
     */
    public String resolveLegacyBaseUrl()
    {
        String fallback = properties.getPublicBaseUrl();
        if (StringUtils.isBlank(fallback))
        {
            throw new ServiceException("历史通知缺少详情地址快照，且未配置旧 notification.public-base-url，暂不能生成详情链接",
                    503);
        }
        return normalizeConfigured(fallback, "notification.public-base-url");
    }

    /**
     * 已接单记录优先使用自己的地址快照，缺失时兼容旧私密配置；绝不使用数据库当前值。
     */
    public String resolveRecordBaseUrl(NotificationRecord record)
    {
        if (record != null && StringUtils.isNotBlank(record.getDetailBaseUrl()))
        {
            return normalize(record.getDetailBaseUrl());
        }
        return resolveLegacyBaseUrl();
    }

    /**
     * 同上，但无法解析时返回 null，供幂等返回/后台详情尽量展示旧链接。
     */
    public String resolveRecordBaseUrlOrNull(NotificationRecord record)
    {
        try
        {
            return resolveRecordBaseUrl(record);
        }
        catch (ServiceException e)
        {
            return null;
        }
    }

    /**
     * 规范化用户提交或配置的基地址。校验失败抛出 400，不修改数据库。
     */
    public String normalize(String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            throw new ServiceException("详情页地址不能为空", 400);
        }
        if (raw.length() > MAX_LENGTH)
        {
            throw new ServiceException("详情页地址不能超过 " + MAX_LENGTH + " 个字符", 400);
        }
        for (int index = 0; index < raw.length(); index++)
        {
            char current = raw.charAt(index);
            if (Character.isWhitespace(current) || Character.isSpaceChar(current) || Character.isISOControl(current))
            {
                throw new ServiceException("详情页地址不能包含空格或控制字符", 400);
            }
        }
        String base = raw;
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty())
        {
            throw new ServiceException("详情页地址不能为空", 400);
        }
        URI uri;
        try
        {
            uri = new URI(base);
        }
        catch (URISyntaxException e)
        {
            throw new ServiceException("详情页地址不是合法 URL", 400);
        }
        if (!uri.isAbsolute() || StringUtils.isBlank(uri.getScheme()) || uri.getHost() == null)
        {
            throw new ServiceException("详情页地址必须是包含主机名的绝对地址", 400);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme))
        {
            throw new ServiceException("详情页地址只支持 http 或 https 协议", 400);
        }
        if (uri.getRawUserInfo() != null)
        {
            throw new ServiceException("详情页地址不能包含用户名或密码", 400);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null)
        {
            throw new ServiceException("详情页地址不能包含查询参数或锚点", 400);
        }
        if (uri.getPort() > 65535)
        {
            throw new ServiceException("详情页地址端口不合法", 400);
        }
        if ("http".equals(scheme) && !isLocalHost(uri.getHost()))
        {
            throw new ServiceException("详情页地址必须使用 https（仅 localhost / 127.0.0.1 / ::1 本地测试允许 http）",
                    400);
        }
        return base;
    }

    /**
     * 基于指定基地址拼接详情链接；基地址会再次规范化，避免旧数据带来异常链接。
     */
    public String buildDetailUrl(String baseUrl, String token)
    {
        if (StringUtils.isBlank(token))
        {
            throw new ServiceException("通知详情票据为空", 500);
        }
        return normalize(baseUrl) + "/notification/detail/" + token;
    }

    /**
     * 兼容旧调用：先解析当前生效地址再拼接。
     */
    public String buildDetailUrl(String token)
    {
        return buildDetailUrl(resolveBaseUrl(), token);
    }

    private String normalizeConfigured(String raw, String sourceName)
    {
        try
        {
            return normalize(raw);
        }
        catch (ServiceException e)
        {
            throw new ServiceException(sourceName + " 配置无效：" + e.getMessage(), 503);
        }
    }

    private boolean isLocalHost(String host)
    {
        if (host == null)
        {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized) || "[::1]".equals(normalized);
    }
}
