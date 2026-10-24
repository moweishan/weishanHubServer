package com.ruoyi.project.notification.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.notification.domain.NotificationQuestion;

/** 统一按凭证锁、通知父行、问题行的顺序写入。 */
public interface NotificationQuestionMapper
{
    Long lockCredential(Long credentialId);
    NotificationQuestion selectByRequest(@Param("credentialId") Long credentialId, @Param("requestId") String requestId);
    NotificationQuestion selectByRecord(Long recordId);
    int insert(NotificationQuestion question);
    int update(NotificationQuestion question);
}
