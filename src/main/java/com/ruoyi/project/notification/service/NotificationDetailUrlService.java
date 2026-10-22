package com.ruoyi.project.notification.service;

import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.NotificationDetailUrlSettings;
import com.ruoyi.project.system.domain.SysConfig;
import com.ruoyi.project.system.service.ISysConfigService;

/**
 * 统一详情页地址的设置读取与保存。
 *
 * <p>只读写若依 sys_config 中固定 key {@link NotificationUrlService#CONFIG_KEY}，不触碰主密钥、
 * 渠道 Secret 或任何其他参数。保存后由 ISysConfigService 同步刷新参数缓存，立即影响后续新通知，
 * 无需重启；历史记录使用接单时的地址快照。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationDetailUrlService
{
    private static final String CONFIG_NAME = "通知详情页地址";

    private static final String CONFIG_REMARK = "通知详情链接使用的前端站点地址；仅影响保存后新接单的通知，历史记录使用接单快照";

    private final ISysConfigService configService;

    private final NotificationUrlService urlService;

    private final NotificationProperties properties;

    public NotificationDetailUrlService(ISysConfigService configService, NotificationUrlService urlService,
            NotificationProperties properties)
    {
        this.configService = configService;
        this.urlService = urlService;
        this.properties = properties;
    }

    /**
     * 读取当前设置。即使数据库或回退配置无效也返回原始值和明确错误，方便管理员在页面修正。
     */
    public NotificationDetailUrlSettings getSettings()
    {
        NotificationDetailUrlSettings settings = new NotificationDetailUrlSettings();
        String databaseValue = configService.selectConfigByKey(NotificationUrlService.CONFIG_KEY);
        String fallbackValue = properties.getPublicBaseUrl();
        settings.setConfiguredValue(databaseValue);
        settings.setFallbackUrl(fallbackValue);

        if (StringUtils.isNotBlank(databaseValue))
        {
            settings.setSource("DATABASE");
            try
            {
                settings.setEffectiveUrl(urlService.normalize(databaseValue));
            }
            catch (ServiceException e)
            {
                settings.setError(NotificationUrlService.CONFIG_KEY + " 配置无效：" + e.getMessage());
            }
            return settings;
        }
        if (StringUtils.isNotBlank(fallbackValue))
        {
            settings.setSource("CONFIG");
            try
            {
                settings.setEffectiveUrl(urlService.normalize(fallbackValue));
            }
            catch (ServiceException e)
            {
                settings.setError("notification.public-base-url 配置无效：" + e.getMessage());
            }
            return settings;
        }
        settings.setSource("NONE");
        settings.setError("尚未配置详情页地址，新通知将暂不能生成详情链接");
        return settings;
    }

    /**
     * 校验并保存唯一允许的 sys_config key。空白、非法地址直接拒绝；返回保存后的最新实际值。
     */
    @Transactional(rollbackFor = Exception.class)
    public NotificationDetailUrlSettings save(String url, String username)
    {
        String normalized = urlService.normalize(url);
        SysConfig existing = findExistingConfig();
        int rows;
        if (existing != null)
        {
            existing.setConfigValue(normalized);
            existing.setUpdateBy(username);
            existing.setUpdateTime(new Date());
            rows = configService.updateConfig(existing);
        }
        else
        {
            SysConfig config = new SysConfig();
            config.setConfigName(CONFIG_NAME);
            config.setConfigKey(NotificationUrlService.CONFIG_KEY);
            config.setConfigValue(normalized);
            config.setConfigType("N");
            config.setCreateBy(username);
            config.setCreateTime(new Date());
            config.setRemark(CONFIG_REMARK);
            rows = configService.insertConfig(config);
        }
        if (rows <= 0)
        {
            throw new ServiceException("详情页地址保存失败，请稍后重试", 500);
        }
        return getSettings();
    }

    private SysConfig findExistingConfig()
    {
        SysConfig query = new SysConfig();
        query.setConfigKey(NotificationUrlService.CONFIG_KEY);
        List<SysConfig> configs = configService.selectConfigList(query);
        if (configs == null)
        {
            return null;
        }
        for (SysConfig config : configs)
        {
            // selectConfigList 是模糊匹配，这里必须精确等于固定 key，避免误改其他参数
            if (config != null && NotificationUrlService.CONFIG_KEY.equals(config.getConfigKey()))
            {
                return config;
            }
        }
        return null;
    }
}
