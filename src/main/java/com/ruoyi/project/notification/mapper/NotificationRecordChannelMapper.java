package com.ruoyi.project.notification.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.notification.domain.NotificationChannelCounts;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;

/**
 * 通知记录渠道快照 / 待发送队列 Mapper。
 *
 * @author weishanHub
 */
public interface NotificationRecordChannelMapper
{
    int batchInsertChannels(@Param("channels") List<NotificationRecordChannel> channels);

    List<NotificationRecordChannel> selectChannelsByRecordId(Long recordId);

    NotificationRecordChannel selectChannelById(Long channelId);

    List<Long> selectPendingChannelIds(@Param("limit") int limit);

    /**
     * 条件更新领取待发送记录，防止多线程/多实例重复发送。
     */
    int claimChannel(@Param("channelId") Long channelId, @Param("claimToken") String claimToken,
            @Param("now") Date now);

    int markChannelSuccess(@Param("channelId") Long channelId, @Param("claimToken") String claimToken,
            @Param("msgId") String msgId, @Param("now") Date now);

    int markChannelFailed(@Param("channelId") Long channelId, @Param("claimToken") String claimToken,
            @Param("errorMessage") String errorMessage, @Param("now") Date now);

    int resetFailedToPending(@Param("recordId") Long recordId, @Param("now") Date now);

    /**
     * 启动恢复：把上次进程遗留的 SENDING 标记为失败，并说明结果未知。
     */
    int recoverSending(@Param("errorMessage") String errorMessage, @Param("now") Date now);

    /**
     * 按父记录恢复 SENDING；调用方已先锁定父行，保持一致的锁顺序。
     */
    int recoverSendingByRecord(@Param("recordId") Long recordId, @Param("errorMessage") String errorMessage,
            @Param("now") Date now);

    List<Long> selectRecordIdsByChannelStatus(@Param("status") String status);

    NotificationChannelCounts selectCounts(Long recordId);
}
