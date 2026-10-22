package com.ruoyi.project.notification.service;

import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;

/**
 * 通知发送提供方扩展点。新增 provider 时实现本接口并注册为 Spring Bean 即可。
 *
 * @author weishanHub
 */
public interface NotificationSender
{
    /**
     * 提供方标识，需与渠道配置的 provider 一致。
     */
    String provider();

    /**
     * 使用接单时的渠道快照发送。
     *
     * @param record 通知记录
     * @param channel 渠道配置快照
     * @param plainSecret 解密后的 Secret，只在本次调用内使用
     * @param detailUrl 公共详情链接
     */
    NotificationSendResult send(NotificationRecord record, NotificationRecordChannel channel,
            String plainSecret, String detailUrl);
}
