package com.ruoyi.project.notification.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationAcceptRequest;
import com.ruoyi.project.notification.domain.NotificationAcceptResponse;
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 对外通知受理服务。
 *
 * <p>凭证鉴权、凭证级限流、requestId 幂等与冲突判定在事务外完成；真正的路径校验与父子记录写入
 * 由 {@link NotificationRecordWriter} 在单事务内完成。并发重复 requestId 通过数据库唯一键兜底。</p>
 *
 * @author weishanHub
 */
@Service
public class NotificationReceiveService
{
    private final NotificationCredentialService credentialService;

    private final NotificationRecordMapper recordMapper;

    private final NotificationRecordWriter recordWriter;

    private final NotificationCryptoService cryptoService;

    private final NotificationKeyService keyService;

    private final NotificationUrlService urlService;

    private final NotificationRateLimitService rateLimitService;

    public NotificationReceiveService(NotificationCredentialService credentialService,
            NotificationRecordMapper recordMapper, NotificationRecordWriter recordWriter,
            NotificationCryptoService cryptoService, NotificationKeyService keyService,
            NotificationUrlService urlService, NotificationRateLimitService rateLimitService)
    {
        this.credentialService = credentialService;
        this.recordMapper = recordMapper;
        this.recordWriter = recordWriter;
        this.cryptoService = cryptoService;
        this.keyService = keyService;
        this.urlService = urlService;
        this.rateLimitService = rateLimitService;
    }

    public NotificationAcceptResponse accept(String apiKey, NotificationAcceptRequest request)
    {
        if (StringUtils.isBlank(apiKey))
        {
            throw new ServiceException("缺少调用凭证", HttpStatus.UNAUTHORIZED);
        }
        NotificationCredential credential = credentialService.authenticate(apiKey);
        rateLimitService.checkCredential(credential.getCredentialId());

        String requestId = StringUtils.trimToEmpty(request.getRequestId());
        if (StringUtils.isBlank(requestId))
        {
            throw new ServiceException("requestId 不能为空", HttpStatus.BAD_REQUEST);
        }
        request.setRequestId(requestId);
        List<String> routes = normalizeRoutes(request.getRouteCodes());
        String messageType = StringUtils.trimToEmpty(request.getMessageType()).toUpperCase();
        if (!"TEXT".equals(messageType) && !"CARD".equals(messageType))
        {
            throw new ServiceException("messageType 只能是 TEXT 或 CARD", HttpStatus.BAD_REQUEST);
        }
        String title = StringUtils.trimToEmpty(request.getTitle());
        if (StringUtils.isBlank(title))
        {
            throw new ServiceException("title 不能为空", HttpStatus.BAD_REQUEST);
        }
        request.setMessageType(messageType);
        request.setTitle(title);
        String summary = StringUtils.trimToNull(request.getSummary());
        String detail = StringUtils.isBlank(request.getDetail()) ? null : request.getDetail();
        if ("CARD".equals(messageType) && (summary == null || detail == null))
        {
            throw new ServiceException("CARD 消息的 summary 与 detail 均不能为空", HttpStatus.BAD_REQUEST);
        }
        if ("TEXT".equals(messageType) && detail == null && summary == null)
        {
            throw new ServiceException("TEXT 消息需要非空正文（detail 或 summary）", HttpStatus.BAD_REQUEST);
        }
        String sortedRoutes = sortedRoutes(routes);
        String contentHash = keyService.contentHash(messageType, title, summary, detail, sortedRoutes);

        NotificationRecord existing = recordMapper.selectRecordByRequestId(credential.getCredentialId(),
                request.getRequestId().trim());
        if (existing != null)
        {
            return duplicateResponse(existing, contentHash);
        }

        cryptoService.requireAvailable();
        Date now = new Date();
        String token = keyService.generateDetailToken();
        String tokenHash = keyService.sha256Hex(token);
        String tokenCipher = cryptoService.encrypt(token);
        // 接单时只解析一次地址并随父记录保存快照；后续发送/重试/幂等返回都不再读取页面设置
        String detailBaseUrl = urlService.resolveBaseUrl();
        String detailUrl = urlService.buildDetailUrl(detailBaseUrl, token);

        try
        {
            NotificationRecord record = recordWriter.create(credential, request, routes, contentHash,
                    tokenHash, tokenCipher, detailBaseUrl, now);
            NotificationAcceptResponse response = response(record, false);
            response.setDetailUrl(detailUrl);
            return response;
        }
        catch (DuplicateKeyException e)
        {
            // 并发重复调用：唯一键兜底后返回原记录，绝不重复发送
            NotificationRecord concurrent = recordMapper.selectRecordByRequestId(credential.getCredentialId(),
                    request.getRequestId().trim());
            if (concurrent == null)
            {
                throw new ServiceException("通知受理冲突，请稍后重试", HttpStatus.CONFLICT);
            }
            return duplicateResponse(concurrent, contentHash);
        }
    }

    private NotificationAcceptResponse duplicateResponse(NotificationRecord existing, String contentHash)
    {
        if ("1".equals(existing.getDeleted()))
        {
            throw new ServiceException("requestId 对应的原通知已删除，不能复用", HttpStatus.CONFLICT);
        }
        if (!contentHash.equals(existing.getContentHash()))
        {
            throw new ServiceException("requestId 已存在且请求内容不一致", HttpStatus.CONFLICT);
        }
        NotificationAcceptResponse response = response(existing, true);
        if (cryptoService.isAvailable() && StringUtils.isNotBlank(existing.getTokenCipher()))
        {
            try
            {
                String detailBaseUrl = urlService.resolveRecordBaseUrlOrNull(existing);
                if (StringUtils.isNotBlank(detailBaseUrl))
                {
                    response.setDetailUrl(urlService.buildDetailUrl(detailBaseUrl,
                            cryptoService.decrypt(existing.getTokenCipher())));
                }
            }
            catch (ServiceException ignored)
            {
                // 主密钥轮换、旧地址缺失等情况下不返回旧链接，但幂等语义仍保持
                response.setDetailUrl(null);
            }
        }
        return response;
    }

    private NotificationAcceptResponse response(NotificationRecord record, boolean duplicate)
    {
        NotificationAcceptResponse response = new NotificationAcceptResponse();
        response.setRecordId(record.getRecordId());
        response.setRequestId(record.getRequestId());
        response.setDuplicate(duplicate);
        response.setStatus(record.getStatus());
        return response;
    }

    /**
     * 去空、去重并保留调用方顺序；最多 50 个，每个路径必须符合 routeCode 规则。
     */
    private List<String> normalizeRoutes(List<String> routeCodes)
    {
        if (routeCodes == null || routeCodes.isEmpty())
        {
            throw new ServiceException("routeCodes 不能为空", HttpStatus.BAD_REQUEST);
        }
        Set<String> routes = new LinkedHashSet<>();
        for (String route : routeCodes)
        {
            String trimmed = StringUtils.trimToEmpty(route);
            if (!trimmed.matches("[A-Za-z0-9_-]{1,64}"))
            {
                throw new ServiceException("routeCode 不合法：" + trimmed, HttpStatus.BAD_REQUEST);
            }
            routes.add(trimmed);
        }
        if (routes.isEmpty())
        {
            throw new ServiceException("routeCodes 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (routes.size() > 50)
        {
            throw new ServiceException("routeCodes 最多 50 个", HttpStatus.BAD_REQUEST);
        }
        return new ArrayList<>(routes);
    }

    private String sortedRoutes(List<String> routes)
    {
        List<String> sorted = new ArrayList<>(routes);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }
}
