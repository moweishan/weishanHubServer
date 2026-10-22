package com.ruoyi.project.notification.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationChannel;
import com.ruoyi.project.notification.domain.NotificationChannelOption;
import com.ruoyi.project.notification.domain.NotificationChannelRequest;
import com.ruoyi.project.notification.mapper.NotificationChannelMapper;

/**
 * 通知渠道管理。
 *
 * <p>Secret 只在保存时加密落库；列表与详情只暴露 secretConfigured。
 * 主密钥缺失时保存 Secret 的操作会被拒绝，其他后台功能不受影响。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationChannelService
{
    private final NotificationChannelMapper channelMapper;

    private final NotificationCryptoService cryptoService;

    private final NotificationSenderFactory senderFactory;

    public NotificationChannelService(NotificationChannelMapper channelMapper,
            NotificationCryptoService cryptoService, NotificationSenderFactory senderFactory)
    {
        this.channelMapper = channelMapper;
        this.cryptoService = cryptoService;
        this.senderFactory = senderFactory;
    }

    public List<NotificationChannel> selectChannelList(NotificationChannel query)
    {
        return channelMapper.selectChannelList(query);
    }

    public NotificationChannel selectChannelById(Long channelId)
    {
        NotificationChannel channel = channelMapper.selectChannelById(channelId);
        if (channel == null)
        {
            throw new ServiceException("通知渠道不存在", 404);
        }
        return channel;
    }

    public List<NotificationChannelOption> selectOptions()
    {
        List<NotificationChannel> channels = channelMapper.selectEnabledChannels();
        List<NotificationChannelOption> options = new ArrayList<>();
        if (channels != null)
        {
            for (NotificationChannel channel : channels)
            {
                NotificationChannelOption option = new NotificationChannelOption();
                option.setChannelId(channel.getChannelId());
                option.setRouteCode(channel.getRouteCode());
                option.setChannelName(channel.getChannelName());
                option.setProvider(channel.getProvider());
                options.add(option);
            }
        }
        return options;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createChannel(NotificationChannelRequest request, String username)
    {
        String routeCode = normalizeRouteCode(request.getRouteCode());
        if (channelMapper.countByRouteCode(routeCode, null) > 0)
        {
            throw new ServiceException("routeCode 已存在：" + routeCode, 409);
        }
        if (StringUtils.isBlank(request.getSecret()))
        {
            throw new ServiceException("新增渠道必须配置 Secret", 400);
        }
        String provider = normalizeProvider(request.getProvider());
        assertProviderSupported(provider);
        String toUser = normalizeToUser(request.getToUser());
        String enabled = normalizeEnabled(request.getEnabled(), "0");
        if ("0".equals(enabled) && StringUtils.isBlank(request.getSecret()))
        {
            throw new ServiceException("启用渠道前必须配置 Secret", 400);
        }

        NotificationChannel channel = new NotificationChannel();
        channel.setRouteCode(routeCode);
        channel.setChannelName(request.getChannelName().trim());
        channel.setProvider(provider);
        channel.setCorpId(request.getCorpId().trim());
        channel.setAgentId(request.getAgentId());
        channel.setToUser(toUser);
        channel.setSecretCipher(cryptoService.encrypt(request.getSecret()));
        channel.setEnabled(enabled);
        channel.setRemark(StringUtils.trimToNull(request.getRemark()));
        channel.setCreateBy(username);
        channel.setCreateTime(new Date());
        try
        {
            channelMapper.insertChannel(channel);
        }
        catch (org.springframework.dao.DuplicateKeyException e)
        {
            throw new ServiceException("routeCode 已存在：" + routeCode, 409);
        }
        return channel.getChannelId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateChannel(Long channelId, NotificationChannelRequest request, String username)
    {
        NotificationChannel existing = selectChannelById(channelId);
        String routeCode = normalizeRouteCode(request.getRouteCode());
        if (channelMapper.countByRouteCode(routeCode, channelId) > 0)
        {
            throw new ServiceException("routeCode 已存在：" + routeCode, 409);
        }
        String provider = normalizeProvider(StringUtils.isBlank(request.getProvider())
                ? existing.getProvider() : request.getProvider());
        assertProviderSupported(provider);
        String toUser = normalizeToUser(request.getToUser());
        String enabled = normalizeEnabled(StringUtils.isBlank(request.getEnabled())
                ? existing.getEnabled() : request.getEnabled(), existing.getEnabled());
        String secretCipher = existing.getSecretCipher();
        if (StringUtils.isNotBlank(request.getSecret()))
        {
            secretCipher = cryptoService.encrypt(request.getSecret());
        }
        if ("0".equals(enabled) && StringUtils.isBlank(secretCipher))
        {
            throw new ServiceException("启用渠道前必须配置 Secret", 400);
        }

        existing.setRouteCode(routeCode);
        existing.setChannelName(request.getChannelName().trim());
        existing.setProvider(provider);
        existing.setCorpId(request.getCorpId().trim());
        existing.setAgentId(request.getAgentId());
        existing.setToUser(toUser);
        existing.setEnabled(enabled);
        existing.setRemark(StringUtils.trimToNull(request.getRemark()));
        existing.setUpdateBy(username);
        existing.setUpdateTime(new Date());
        // MyBatis 只在非空时更新 secret_cipher，留空表示保持原 Secret
        existing.setSecretCipher(StringUtils.isBlank(request.getSecret()) ? null : secretCipher);
        try
        {
            channelMapper.updateChannel(existing);
        }
        catch (org.springframework.dao.DuplicateKeyException e)
        {
            throw new ServiceException("routeCode 已存在：" + routeCode, 409);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteChannels(Long[] channelIds)
    {
        if (channelIds == null || channelIds.length == 0)
        {
            throw new ServiceException("请选择要删除的通知渠道", 400);
        }
        channelMapper.deleteChannelByIds(channelIds);
    }

    /**
     * 接收成员支持逗号、分号或 | 分隔，统一存为 |，并去重。
     */
    public String normalizeToUser(String toUser)
    {
        if (StringUtils.isBlank(toUser))
        {
            throw new ServiceException("接收 UserID 不能为空", 400);
        }
        String normalized = toUser.replace(',', '|').replace('，', '|').replace(';', '|').replace('；', '|');
        Set<String> users = new LinkedHashSet<>();
        for (String item : normalized.split("\\|"))
        {
            String trimmed = item == null ? null : item.trim();
            if (StringUtils.isNotBlank(trimmed))
            {
                users.add(trimmed);
            }
        }
        if (users.isEmpty())
        {
            throw new ServiceException("接收 UserID 不能为空", 400);
        }
        String joined = String.join("|", users);
        if (joined.length() > 1000)
        {
            throw new ServiceException("接收 UserID 合计不能超过 1000 个字符", 400);
        }
        return joined;
    }

    private String normalizeRouteCode(String routeCode)
    {
        String normalized = StringUtils.trimToEmpty(routeCode);
        if (!normalized.matches("[A-Za-z0-9_-]{1,64}"))
        {
            throw new ServiceException("routeCode 只能包含字母、数字、下划线和中划线，长度 1-64", 400);
        }
        return normalized;
    }

    private String normalizeProvider(String provider)
    {
        return StringUtils.isBlank(provider) ? "WECOM_APP" : provider.trim().toUpperCase(Locale.ROOT);
    }

    private void assertProviderSupported(String provider)
    {
        if (!senderFactory.supports(provider))
        {
            throw new ServiceException("暂不支持的通知渠道类型：" + provider, 400);
        }
    }

    private String normalizeEnabled(String enabled, String defaultValue)
    {
        String value = StringUtils.isBlank(enabled) ? defaultValue : enabled.trim();
        if (!"0".equals(value) && !"1".equals(value))
        {
            throw new ServiceException("启用状态只能是 0 或 1", 400);
        }
        return value;
    }
}
