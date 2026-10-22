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
