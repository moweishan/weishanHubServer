package com.ruoyi.project.notification.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationAcceptRequest;
import com.ruoyi.project.notification.domain.NotificationChannel;
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationStatus;
import com.ruoyi.project.notification.mapper.NotificationChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 通知接单事务。
 *
 * <p>在同一个数据库事务内锁定并向全部目标渠道行加锁，校验全体路径存在且启用后，
 * 复制 CorpID/AgentID/UserID/加密 Secret 为渠道快照，再写入父记录与子记录。
 * 任一渠道不满足条件都会整体回滚。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationRecordWriter
{
    private final NotificationChannelMapper channelMapper;

    private final NotificationRecordMapper recordMapper;

    private final NotificationRecordChannelMapper recordChannelMapper;

    public NotificationRecordWriter(NotificationChannelMapper channelMapper,
            NotificationRecordMapper recordMapper, NotificationRecordChannelMapper recordChannelMapper)
    {
        this.channelMapper = channelMapper;
        this.recordMapper = recordMapper;
        this.recordChannelMapper = recordChannelMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public NotificationRecord create(NotificationCredential credential, NotificationAcceptRequest request,
            List<String> routes, String contentHash, String tokenHash, String tokenCipher,
            String detailBaseUrl, Date now)
    {
        if (routes == null || routes.isEmpty())
        {
            throw new ServiceException("目标路径不能为空", 400);
        }
        List<NotificationChannel> channels = channelMapper.selectEnabledByRouteCodesForUpdate(routes);
        Map<String, NotificationChannel> byRoute = new HashMap<>();
        if (channels != null)
        {
            for (NotificationChannel channel : channels)
            {
                byRoute.put(channel.getRouteCode(), channel);
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        List<NotificationChannel> snapshots = new ArrayList<>();
        for (String route : routes)
        {
            NotificationChannel channel = byRoute.get(route);
            if (channel == null)
            {
                missing.add(route);
                continue;
            }
            if (StringUtils.isBlank(channel.getSecretCipher()))
            {
                throw new ServiceException("通知渠道未配置 Secret，暂不能接收：" + route, 400);
            }
            snapshots.add(channel);
        }
        if (!missing.isEmpty())
        {
            throw new ServiceException("以下路径不存在或已停用：" + String.join("、", missing), 400);
        }
        if (snapshots.size() != routes.size())
        {
            throw new ServiceException("目标路径校验失败，请稍后重试", 409);
        }

        NotificationRecord record = new NotificationRecord();
        record.setRequestId(request.getRequestId().trim());
        record.setCredentialId(credential.getCredentialId());
        record.setContentHash(contentHash);
        record.setMessageType(request.getMessageType().trim().toUpperCase());
        record.setTitle(request.getTitle().trim());
        record.setSummary(StringUtils.trimToNull(request.getSummary()));
        record.setDetail(StringUtils.isBlank(request.getDetail()) ? null : request.getDetail());
        record.setTokenHash(tokenHash);
        record.setTokenCipher(tokenCipher);
        record.setRouteCodes(String.join(",", routes));
        record.setDetailBaseUrl(detailBaseUrl);
        record.setStatus(NotificationStatus.PENDING);
        record.setTotalCount(routes.size());
        record.setRevoked("0");
        record.setDeleted("0");
        record.setCreateBy("open-api");
        record.setCreateTime(now);
        recordMapper.insertRecord(record);
        if (record.getRecordId() == null)
        {
            throw new ServiceException("通知记录保存失败", 500);
        }

        List<NotificationRecordChannel> children = new ArrayList<>();
        for (NotificationChannel channel : snapshots)
        {
            NotificationRecordChannel child = new NotificationRecordChannel();
            child.setRecordId(record.getRecordId());
            child.setRouteCode(channel.getRouteCode());
            child.setChannelName(channel.getChannelName());
            child.setProvider(channel.getProvider());
            child.setCorpId(channel.getCorpId());
            child.setAgentId(channel.getAgentId());
            child.setToUser(channel.getToUser());
            child.setSecretCipher(channel.getSecretCipher());
            child.setStatus(NotificationStatus.PENDING);
            child.setAttemptCount(0);
            child.setCreateTime(now);
            child.setUpdateTime(now);
            children.add(child);
        }
        int inserted = recordChannelMapper.batchInsertChannels(children);
        if (inserted != children.size())
        {
            throw new ServiceException("通知渠道快照保存失败", 500);
        }
        return record;
    }
}
