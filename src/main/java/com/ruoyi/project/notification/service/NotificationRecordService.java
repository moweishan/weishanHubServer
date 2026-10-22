package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationChannelResult;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationRecordDetail;
import com.ruoyi.project.notification.domain.NotificationStatus;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 后台通知记录查询与人工操作。
 *
 * @author weishanHub
 */
@Service
public class NotificationRecordService
{
    private final NotificationRecordMapper recordMapper;

    private final NotificationRecordChannelMapper recordChannelMapper;

    private final NotificationCryptoService cryptoService;

    private final NotificationUrlService urlService;

    private final Clock clock;

    public NotificationRecordService(NotificationRecordMapper recordMapper,
            NotificationRecordChannelMapper recordChannelMapper, NotificationCryptoService cryptoService,
            NotificationUrlService urlService, @Qualifier("notificationClock") Clock clock)
    {
        this.recordMapper = recordMapper;
        this.recordChannelMapper = recordChannelMapper;
        this.cryptoService = cryptoService;
        this.urlService = urlService;
        this.clock = clock;
    }

    public List<NotificationRecord> selectRecordList(NotificationRecord query)
    {
        return recordMapper.selectRecordList(query);
    }

    /**
     * 后台详情：记录、每渠道结果与可复制的详情链接，不含 Secret/CorpID/UserID。
     */
    public NotificationRecordDetail selectRecordDetail(Long recordId)
    {
        NotificationRecord record = requireRecord(recordId);
        NotificationRecordDetail detail = new NotificationRecordDetail();
        detail.setRecord(record);
        List<NotificationChannelResult> results = new ArrayList<>();
        List<NotificationRecordChannel> channels = recordChannelMapper.selectChannelsByRecordId(recordId);
        if (channels != null)
        {
            for (NotificationRecordChannel channel : channels)
            {
                NotificationChannelResult result = new NotificationChannelResult();
                result.setChannelId(channel.getChannelId());
                result.setRouteCode(channel.getRouteCode());
                result.setChannelName(channel.getChannelName());
                result.setProvider(channel.getProvider());
                result.setStatus(channel.getStatus());
                result.setAttemptCount(channel.getAttemptCount());
                result.setErrorMessage(channel.getErrorMessage());
                result.setWecomMsgId(channel.getWecomMsgId());
                result.setLastAttemptTime(channel.getLastAttemptTime());
                results.add(result);
            }
        }
        detail.setChannels(results);
        if (cryptoService.isAvailable() && StringUtils.isNotBlank(record.getTokenCipher()))
        {
            try
            {
                String detailBaseUrl = urlService.resolveRecordBaseUrlOrNull(record);
                if (StringUtils.isNotBlank(detailBaseUrl))
                {
                    detail.setDetailUrl(urlService.buildDetailUrl(detailBaseUrl,
                            cryptoService.decrypt(record.getTokenCipher())));
                }
            }
            catch (ServiceException ignored)
            {
                detail.setDetailUrl(null);
            }
        }
        return detail;
    }

    /**
     * 只重试失败渠道；不改写 sentAt/expiresAt。
     */
    @Transactional(rollbackFor = Exception.class)
    public int retryFailed(Long recordId)
    {
        // 与发送完成器保持一致的父行 -> 子行锁顺序
        NotificationRecord record = requireRecordForUpdate(recordId);
        if ("1".equals(record.getRevoked()))
        {
            throw new ServiceException("通知链接已撤销，不能重试", 409);
        }
        if (!cryptoService.isAvailable())
        {
            throw new ServiceException("通知主密钥未配置，暂不能重试发送", 503);
        }
        int reset = recordChannelMapper.resetFailedToPending(recordId, now());
        if (reset == 0)
        {
            throw new ServiceException("没有失败的渠道可重试", 409);
        }
        recordMapper.updateStatus(recordId, NotificationStatus.SENDING, now());
        return reset;
    }

    /**
     * 撤销详情链接。撤销后公共详情立即不可访问，已成功发送过的消息不会撤回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long recordId, String username)
    {
        NotificationRecord record = requireRecord(recordId);
        if ("1".equals(record.getRevoked()))
        {
            return;
        }
        recordMapper.revokeRecord(recordId, username, now());
    }

    /**
     * 软删除记录：保留历史行，公共详情与后台列表立即不可见。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecords(Long[] recordIds, String username)
    {
        if (recordIds == null || recordIds.length == 0)
        {
            throw new ServiceException("请选择要删除的通知记录", 400);
        }
        recordMapper.softDeleteRecords(recordIds, username, now());
    }

    private NotificationRecord requireRecord(Long recordId)
    {
        NotificationRecord record = recordMapper.selectRecordById(recordId);
        if (record == null || "1".equals(record.getDeleted()))
        {
            throw new ServiceException("通知记录不存在", 404);
        }
        return record;
    }

    private NotificationRecord requireRecordForUpdate(Long recordId)
    {
        NotificationRecord record = recordMapper.selectRecordForUpdate(recordId);
        if (record == null || "1".equals(record.getDeleted()))
        {
            throw new ServiceException("通知记录不存在", 404);
        }
        return record;
    }

    private Date now()
    {
        return Date.from(clock.instant());
    }
}
