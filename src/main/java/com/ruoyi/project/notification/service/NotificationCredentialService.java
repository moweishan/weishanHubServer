package com.ruoyi.project.notification.service;

import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationCredentialCreated;
import com.ruoyi.project.notification.domain.NotificationCredentialRequest;
import com.ruoyi.project.notification.mapper.NotificationCredentialMapper;

/**
 * 通知调用凭证管理。
 *
 * <p>API Key 只在创建/重置时返回一次；数据库只保存 SHA-256 哈希和可辨识前缀。
 * 明文不进入 URL、日志、列表或后续响应。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationCredentialService
{
    private final NotificationCredentialMapper credentialMapper;

    private final NotificationKeyService keyService;

    public NotificationCredentialService(NotificationCredentialMapper credentialMapper,
            NotificationKeyService keyService)
    {
        this.credentialMapper = credentialMapper;
        this.keyService = keyService;
    }

    public List<NotificationCredential> selectCredentialList(NotificationCredential query)
    {
        return credentialMapper.selectCredentialList(query);
    }

    public NotificationCredential selectCredentialById(Long credentialId)
    {
        NotificationCredential credential = credentialMapper.selectCredentialById(credentialId);
        if (credential == null)
        {
            throw new ServiceException("调用凭证不存在", 404);
        }
        return credential;
    }

    /**
     * 用 API Key 查找并校验凭据。失败统一返回 401，避免枚举。
     */
    public NotificationCredential authenticate(String apiKey)
    {
        keyService.assertApiKeyFormat(apiKey);
        NotificationCredential credential = credentialMapper.selectCredentialByKeyHash(keyService.sha256Hex(apiKey));
        if (credential == null || !"0".equals(credential.getEnabled()))
        {
            throw new ServiceException("调用凭证无效或已禁用", 401);
        }
        credentialMapper.updateLastUsedTime(credential.getCredentialId(), new Date());
        return credential;
    }

    @Transactional(rollbackFor = Exception.class)
    public NotificationCredentialCreated createCredential(NotificationCredentialRequest request, String username)
    {
        String apiKey = keyService.generateApiKey();
        NotificationCredential credential = new NotificationCredential();
        credential.setCredentialName(request.getCredentialName().trim());
        credential.setKeyHash(keyService.sha256Hex(apiKey));
        credential.setKeyPrefix(keyService.keyPrefix(apiKey));
        credential.setEnabled(normalizeEnabled(request.getEnabled()));
        credential.setRemark(StringUtils.trimToNull(request.getRemark()));
        credential.setCreateBy(username);
        credential.setCreateTime(new Date());
        credentialMapper.insertCredential(credential);
        return toCreated(credential, apiKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCredential(Long credentialId, NotificationCredentialRequest request, String username)
    {
        NotificationCredential existing = selectCredentialById(credentialId);
        existing.setCredentialName(request.getCredentialName().trim());
        existing.setEnabled(normalizeEnabled(request.getEnabled()));
        existing.setRemark(StringUtils.trimToNull(request.getRemark()));
        existing.setUpdateBy(username);
        existing.setUpdateTime(new Date());
        credentialMapper.updateCredential(existing);
    }

    /**
     * 重置 API Key：旧 Key 立即失效，新 Key 只返回这一次。
     */
    @Transactional(rollbackFor = Exception.class)
    public NotificationCredentialCreated resetCredential(Long credentialId, String username)
    {
        NotificationCredential existing = selectCredentialById(credentialId);
        String apiKey = keyService.generateApiKey();
        Date now = new Date();
        credentialMapper.updateKey(credentialId, keyService.sha256Hex(apiKey), keyService.keyPrefix(apiKey),
                username, now);
        existing.setKeyPrefix(keyService.keyPrefix(apiKey));
        return toCreated(existing, apiKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCredentials(Long[] credentialIds)
    {
        if (credentialIds == null || credentialIds.length == 0)
        {
            throw new ServiceException("请选择要删除的调用凭证", 400);
        }
        credentialMapper.deleteCredentialByIds(credentialIds);
    }

    private NotificationCredentialCreated toCreated(NotificationCredential credential, String apiKey)
    {
        NotificationCredentialCreated created = new NotificationCredentialCreated();
        created.setCredentialId(credential.getCredentialId());
        created.setCredentialName(credential.getCredentialName());
        created.setKeyPrefix(credential.getKeyPrefix());
        created.setApiKey(apiKey);
        return created;
    }

    private String normalizeEnabled(String enabled)
    {
        String value = StringUtils.isBlank(enabled) ? "0" : enabled.trim();
        if (!"0".equals(value) && !"1".equals(value))
        {
            throw new ServiceException("启用状态只能是 0 或 1", 400);
        }
        return value;
    }
}
