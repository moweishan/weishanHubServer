package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.project.notification.domain.NotificationChannelCounts;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationStatus;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 发送结果落库完成器。
 *
 * <p>外部企业微信 SDK 调用在事务外；本 bean 只负责在独立事务中原子完成：
 * 父记录 FOR UPDATE → 子渠道成功/失败 → 首次 sentAt CAS → 读取最新计数并汇总。
 * 成功、失败、重试、启动恢复汇总均按父行加锁的相同顺序执行，避免并发 count 覆盖。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationSendCompletionService
{
    private static final long EXPIRE_MILLIS = 60L * 60L * 1000L;

    private final NotificationRecordMapper recordMapper;

    private final NotificationRecordChannelMapper recordChannelMapper;

    private final Clock clock;

    public NotificationSendCompletionService(NotificationRecordMapper recordMapper,
            NotificationRecordChannelMapper recordChannelMapper,
            @Qualifier("notificationClock") Clock clock)
    {
        this.recordMapper = recordMapper;
        this.recordChannelMapper = recordChannelMapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean completeSuccess(Long recordId, Long channelId, String claimToken, String msgId)
    {
        NotificationRecord record = recordMapper.selectRecordForUpdate(recordId);
        if (record == null)
        {
            return false;
        }
        Date now = now();
        if (recordChannelMapper.markChannelSuccess(channelId, claimToken, msgId, now) != 1)
        {
            return false;
        }
        // 首次成功只写一次；父行已加锁，CAS 仍作为最终数据库保证
        if (record.getSentAt() == null)
        {
            recordMapper.markFirstSent(recordId, now, new Date(now.getTime() + EXPIRE_MILLIS));
        }
        aggregateLocked(recordId, now);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean completeFailure(Long recordId, Long channelId, String claimToken, String errorMessage)
    {
        NotificationRecord record = recordMapper.selectRecordForUpdate(recordId);
        if (record == null)
        {
            return false;
        }
        Date now = now();
        if (recordChannelMapper.markChannelFailed(channelId, claimToken, errorMessage, now) != 1)
        {
            return false;
        }
        aggregateLocked(recordId, now);
        return true;
    }

    /**
     * 启动恢复：按父记录加锁串行处理遗留 SENDING，再更新子记录与汇总。
     */
    @Transactional(rollbackFor = Exception.class)
    public int recoverUnknownSending(String errorMessage)
    {
        List<Long> recordIds = recordChannelMapper.selectRecordIdsByChannelStatus(NotificationStatus.SENDING);
        if (recordIds == null || recordIds.isEmpty())
        {
            return 0;
        }
        java.util.Collections.sort(recordIds);
        int affected = 0;
        for (Long recordId : recordIds)
        {
            recordMapper.selectRecordForUpdate(recordId);
            affected += recordChannelMapper.recoverSendingByRecord(recordId, errorMessage, now());
            aggregateLocked(recordId, now());
        }
        return affected;
    }

    private void aggregateLocked(Long recordId, Date now)
    {
        NotificationChannelCounts counts = recordChannelMapper.selectCounts(recordId);
        if (counts == null || counts.getTotalCount() == 0)
        {
            return;
        }
        String status;
        if (counts.getPendingCount() + counts.getSendingCount() > 0)
        {
            status = NotificationStatus.SENDING;
        }
        else if (counts.getSuccessCount() == counts.getTotalCount())
        {
            status = NotificationStatus.SUCCESS;
        }
        else if (counts.getSuccessCount() > 0)
        {
            status = NotificationStatus.PARTIAL_SUCCESS;
        }
        else
        {
            status = NotificationStatus.FAILED;
        }
        recordMapper.updateAggregate(recordId, status, counts.getSuccessCount(), counts.getFailedCount(),
                counts.getTotalCount(), now);
    }

    private Date now()
    {
        return Date.from(clock.instant());
    }
}
