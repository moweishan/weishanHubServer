package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.util.Date;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.project.notification.domain.NotificationAccessContext;
import com.ruoyi.project.notification.domain.NotificationDetailResult;
import com.ruoyi.project.notification.domain.NotificationPublicView;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 公共详情授权与响应组装。
 *
 * <p>每次请求都重新检查票据哈希、撤销/删除标记、服务端时间与登录权限，不依赖任何前端状态。
 * 微信/企微 UA 只是免登录入口条件之一，不识别身份；UA 可伪造的边界已在文档中明确接受。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationDetailService
{
    private final NotificationRecordMapper recordMapper;

    private final NotificationKeyService keyService;

    private final Clock clock;

    public NotificationDetailService(NotificationRecordMapper recordMapper, NotificationKeyService keyService,
            @Qualifier("notificationClock") Clock clock)
    {
        this.recordMapper = recordMapper;
        this.keyService = keyService;
        this.clock = clock;
    }

    public NotificationDetailResult load(String token, NotificationAccessContext context)
    {
        if (!keyService.isDetailTokenFormat(token))
        {
            return NotificationDetailResult.error(HttpStatus.NOT_FOUND, "通知不存在或链接已失效");
        }
        NotificationRecord record = recordMapper.selectRecordByTokenHash(keyService.sha256Hex(token));
        if (record == null || NotificationAccessPolicy.isDeleted(record))
        {
            return NotificationDetailResult.error(HttpStatus.NOT_FOUND, "通知不存在或链接已失效");
        }
        if (NotificationAccessPolicy.isRevoked(record))
        {
            return NotificationDetailResult.error(HttpStatus.NOT_FOUND, "通知链接已撤销");
        }

        Date now = Date.from(clock.instant());
        boolean canLoginView = context != null && context.isAuthenticated() && context.isHasQueryPermission();
        boolean wechatView = context != null
                && NotificationAccessPolicy.isWeChatUserAgent(context.getUserAgent())
                && NotificationAccessPolicy.withinPublicWindow(record, now);

        if (!canLoginView && !wechatView)
        {
            if (context == null || !context.isAuthenticated())
            {
                return NotificationDetailResult.error(HttpStatus.UNAUTHORIZED, "该链接需要登录后查看");
            }
            return NotificationDetailResult.error(HttpStatus.FORBIDDEN, "没有查看通知详情的权限");
        }

        NotificationPublicView view = new NotificationPublicView();
        view.setTitle(record.getTitle());
        view.setSummary(record.getSummary());
        view.setDetail(record.getDetail());
        view.setMessageType(record.getMessageType());
        view.setSentAt(record.getSentAt());
        view.setExpiresAt(record.getExpiresAt());
        view.setServerTime(now);
        view.setExpired(!NotificationAccessPolicy.withinPublicWindow(record, now));
        view.setAccessMode(canLoginView ? "LOGIN" : "WECHAT");
        return NotificationDetailResult.ok(view);
    }
}
