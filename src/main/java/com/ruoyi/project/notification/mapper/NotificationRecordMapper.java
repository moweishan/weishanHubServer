package com.ruoyi.project.notification.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.notification.domain.NotificationRecord;

/**
 * 通知记录 Mapper。
 *
 * @author weishanHub
 */
public interface NotificationRecordMapper
{
    int insertRecord(NotificationRecord record);

    NotificationRecord selectRecordById(Long recordId);

    /**
     * 发送完成/重试/恢复时锁定父记录，统一“父行 -> 子行”的加锁顺序。
     */
    NotificationRecord selectRecordForUpdate(Long recordId);

    NotificationRecord selectRecordByTokenHash(String tokenHash);

    NotificationRecord selectRecordByRequestId(@Param("credentialId") Long credentialId,
            @Param("requestId") String requestId);

    List<NotificationRecord> selectRecordList(NotificationRecord query);

    /**
     * 统计北京时间 [begin, end) 内首次渠道成功的父通知记录数。
     *
     * <p>只按 notification_record.sent_at 计数，不关联渠道子表、不按成功状态、
     * 不排除软删除记录：软删除只影响可见性，不扣减历史统计。</p>
     */
    long countFirstSentBetween(@Param("begin") Date begin, @Param("end") Date end);

    /**
     * CAS 设置首次成功时间；只在 sent_at 为空时成功，保证后续渠道成功与重试都不延长。
     */
    int markFirstSent(@Param("recordId") Long recordId, @Param("sentAt") Date sentAt,
            @Param("expiresAt") Date expiresAt);

    int updateAggregate(@Param("recordId") Long recordId, @Param("status") String status,
            @Param("successCount") int successCount, @Param("failCount") int failCount,
            @Param("totalCount") int totalCount, @Param("updateTime") Date updateTime);

    int updateStatus(@Param("recordId") Long recordId, @Param("status") String status,
            @Param("updateTime") Date updateTime);

    int revokeRecord(@Param("recordId") Long recordId, @Param("revokedBy") String revokedBy,
            @Param("revokedTime") Date revokedTime);

    int softDeleteRecords(@Param("recordIds") Long[] recordIds, @Param("deletedBy") String deletedBy,
            @Param("deletedTime") Date deletedTime);
}
