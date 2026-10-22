package com.ruoyi.project.notification.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.notification.domain.NotificationCredential;

/**
 * 通知调用凭证 Mapper。
 *
 * @author weishanHub
 */
public interface NotificationCredentialMapper
{
    List<NotificationCredential> selectCredentialList(NotificationCredential query);

    NotificationCredential selectCredentialById(Long credentialId);

    NotificationCredential selectCredentialByKeyHash(String keyHash);

    int insertCredential(NotificationCredential credential);

    int updateCredential(NotificationCredential credential);

    int updateKey(@Param("credentialId") Long credentialId, @Param("keyHash") String keyHash,
            @Param("keyPrefix") String keyPrefix, @Param("updateBy") String updateBy,
            @Param("updateTime") Date updateTime);

    int deleteCredentialByIds(@Param("credentialIds") Long[] credentialIds);

    int updateLastUsedTime(@Param("credentialId") Long credentialId, @Param("lastUsedTime") Date lastUsedTime);
}
