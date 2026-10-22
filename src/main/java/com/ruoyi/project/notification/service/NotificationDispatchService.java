package com.ruoyi.project.notification.service;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.config.NotificationProperties;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;
import com.ruoyi.project.notification.mapper.NotificationRecordChannelMapper;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 通知异步发送与状态汇总。
 *
 * <p>数据库待发送子记录即为队列；扫描线程只领取 PENDING，使用条件 UPDATE 防重复 claim。
 * 发送只使用接单时的渠道快照。首个成功时间通过 CAS 只写一次，expiresAt 固定为 sentAt+1 小时。
 * 失败不会自动重试，必须由后台显式操作。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationDispatchService
{
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private static final String RECOVERY_UNKNOWN_MESSAGE = "服务重启，发送结果未知，已标记失败；请人工确认后再重试";

    private final NotificationRecordMapper recordMapper;

    private final NotificationRecordChannelMapper recordChannelMapper;

    private final NotificationCryptoService cryptoService;

    private final NotificationKeyService keyService;

    private final NotificationSenderFactory senderFactory;

    private final NotificationSendCompletionService completionService;

    private final NotificationUrlService urlService;

    private final NotificationProperties properties;

    private final Clock clock;

    private final TaskExecutor sendExecutor;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public NotificationDispatchService(NotificationRecordMapper recordMapper,
            NotificationRecordChannelMapper recordChannelMapper, NotificationCryptoService cryptoService,
            NotificationKeyService keyService, NotificationSenderFactory senderFactory,
            NotificationSendCompletionService completionService, NotificationUrlService urlService,
            NotificationProperties properties, @Qualifier("notificationClock") Clock clock,
            @Qualifier("notificationSendExecutor") TaskExecutor sendExecutor)
    {
        this.recordMapper = recordMapper;
        this.recordChannelMapper = recordChannelMapper;
        this.cryptoService = cryptoService;
        this.keyService = keyService;
        this.senderFactory = senderFactory;
        this.completionService = completionService;
        this.urlService = urlService;
        this.properties = properties;
        this.clock = clock;
        this.sendExecutor = sendExecutor;
    }

    /**
     * 扫描待发送记录并提交到有界线程池。队列满时记录保持 PENDING，等待下一轮扫描。
     */
    public void scanPending()
    {
        int batchSize = Math.max(1, properties.getDispatch().getBatchSize());
        List<Long> channelIds = recordChannelMapper.selectPendingChannelIds(batchSize);
        if (channelIds == null || channelIds.isEmpty())
        {
            return;
        }
        for (Long channelId : channelIds)
        {
            if (!inFlight.add(channelId))
            {
                continue;
            }
            try
            {
                sendExecutor.execute(() -> {
                    try
                    {
                        dispatchChannel(channelId);
                    }
                    finally
                    {
                        inFlight.remove(channelId);
                    }
                });
            }
            catch (RuntimeException e)
            {
                inFlight.remove(channelId);
                log.warn("通知发送任务提交失败，待发送记录保留到下一轮扫描：{}", e.getClass().getSimpleName());
                break;
            }
        }
    }

    /**
     * 发送单个渠道。仅在成功 claim 时继续；每次调用都重新读取接单快照。
     */
    public void dispatchChannel(Long channelId)
    {
        if (!cryptoService.isAvailable())
        {
            // 主密钥缺失时保持 PENDING，不消费记录，待配置恢复后继续；不阻断应用启动
            return;
        }
        String claimToken = keyService.generateClaimToken();
        Date now = now();
        int claimed = recordChannelMapper.claimChannel(channelId, claimToken, now);
        if (claimed != 1)
        {
            return;
        }
        NotificationRecordChannel channel = recordChannelMapper.selectChannelById(channelId);
        NotificationRecord record = channel == null ? null : recordMapper.selectRecordById(channel.getRecordId());
        if (channel == null)
        {
            return;
        }
        if (record == null)
        {
            completionService.completeFailure(channel.getRecordId(), channelId, claimToken, "通知记录不存在，取消发送");
            return;
        }
        if ("1".equals(record.getDeleted()))
        {
            fail(channel, claimToken, "通知记录已删除，取消发送", true);
            return;
        }
        if ("1".equals(record.getRevoked()))
        {
            fail(channel, claimToken, "通知链接已撤销，取消发送", true);
            return;
        }

        NotificationSendResult result;
        try
        {
            NotificationSender sender = senderFactory.get(channel.getProvider());
            String plainSecret = cryptoService.decrypt(channel.getSecretCipher());
            String detailToken = cryptoService.decrypt(record.getTokenCipher());
            // 使用接单时的地址快照；旧记录回退旧私密配置，绝不跟随页面当前设置
            String detailUrl = urlService.buildDetailUrl(urlService.resolveRecordBaseUrl(record), detailToken);
            result = sender.send(record, channel, plainSecret, detailUrl);
        }
        catch (ServiceException e)
        {
            fail(channel, claimToken, e.getMessage(), true);
            return;
        }
        catch (RuntimeException e)
        {
            fail(channel, claimToken, "发送异常，结果未知", true);
            return;
        }

        if (result == null)
        {
            fail(channel, claimToken, "发送结果为空，结果未知", true);
            return;
        }
        if (!result.isSuccess())
        {
            fail(channel, claimToken, result.getErrorMessage(), true);
            return;
        }

        // 外部 SDK 调用已在事务外完成，以下成功落库/首次 sentAt/汇总由独立事务完成器保证原子性
        completionService.completeSuccess(record.getRecordId(), channelId, claimToken, result.getMessageId());
    }

    /**
     * 启动恢复：上次进程遗留的 SENDING 无法确认是否已被平台接受，统一标记失败并说明结果未知。
     *
     * @return 处理条数
     */
    public int recoverUnknownSending()
    {
        return completionService.recoverUnknownSending(RECOVERY_UNKNOWN_MESSAGE);
    }

    private void fail(NotificationRecordChannel channel, String claimToken, String errorMessage, boolean aggregate)
    {
        completionService.completeFailure(channel.getRecordId(), channel.getChannelId(), claimToken,
                safe(errorMessage));
    }

    private Date now()
    {
        return Date.from(clock.instant());
    }

    private String safe(String message)
    {
        String value = StringUtils.isBlank(message) ? "发送失败" : message.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
