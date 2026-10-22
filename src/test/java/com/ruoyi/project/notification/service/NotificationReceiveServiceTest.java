package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.notification.domain.NotificationAcceptRequest;
import com.ruoyi.project.notification.domain.NotificationAcceptResponse;
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationStatus;
import com.ruoyi.project.notification.mapper.NotificationRecordMapper;

/**
 * 对外受理：鉴权、幂等/冲突、并发唯一键兜底、全路径失败不落库。
 */
class NotificationReceiveServiceTest
{
    private NotificationCredentialService credentialService;

    private NotificationRecordMapper recordMapper;

    private NotificationRecordWriter recordWriter;

    private NotificationCryptoService cryptoService;

    private NotificationUrlService urlService;

    private NotificationRateLimitService rateLimitService;

    private NotificationKeyService keyService;

    private NotificationReceiveService service;

    private NotificationCredential credential;

    @BeforeEach
    void setUp()
    {
        credentialService = org.mockito.Mockito.mock(NotificationCredentialService.class);
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        recordWriter = org.mockito.Mockito.mock(NotificationRecordWriter.class);
        cryptoService = org.mockito.Mockito.mock(NotificationCryptoService.class);
        urlService = org.mockito.Mockito.mock(NotificationUrlService.class);
        rateLimitService = org.mockito.Mockito.mock(NotificationRateLimitService.class);
        keyService = new NotificationKeyService();
        service = new NotificationReceiveService(credentialService, recordMapper, recordWriter, cryptoService,
                keyService, urlService, rateLimitService);
        credential = new NotificationCredential();
        credential.setCredentialId(7L);
        credential.setEnabled("0");
        when(credentialService.authenticate(anyString())).thenReturn(credential);
        when(cryptoService.isAvailable()).thenReturn(true);
        when(cryptoService.encrypt(anyString())).thenReturn("token-cipher");
        when(urlService.resolveBaseUrl()).thenReturn("https://hub.example.com");
        when(urlService.buildDetailUrl(anyString())).thenReturn("https://hub.example.com/notification/detail/token");
        when(urlService.buildDetailUrl(eq("https://hub.example.com"), anyString()))
                .thenReturn("https://hub.example.com/notification/detail/token");
    }

    @Test
    void acceptsAndSnapshotsAllRoutes()
    {
        NotificationAcceptRequest request = request("req-1", "CARD", "标题", "摘要", "正文", Arrays.asList("order", "pay"));
        when(recordMapper.selectRecordByRequestId(7L, "req-1")).thenReturn(null);
        when(recordWriter.create(any(NotificationCredential.class), any(NotificationAcceptRequest.class),
                anyList(), anyString(), anyString(), anyString(), anyString(), any(java.util.Date.class)))
                .thenAnswer(invocation -> {
                    NotificationRecord record = new NotificationRecord();
                    record.setRecordId(100L);
                    record.setRequestId("req-1");
                    record.setStatus(NotificationStatus.PENDING);
                    return record;
                });

        NotificationAcceptResponse response = service.accept("ntf_012345678901234567890123456789", request);

        assertNotNull(response);
        assertEquals(100L, response.getRecordId());
        assertEquals("req-1", response.getRequestId());
        assertEquals(false, response.isDuplicate());
        assertEquals("https://hub.example.com/notification/detail/token", response.getDetailUrl());
        verify(rateLimitService).checkCredential(7L);
        verify(urlService, org.mockito.Mockito.times(1)).resolveBaseUrl();
        verify(recordWriter).create(any(NotificationCredential.class), any(NotificationAcceptRequest.class),
                eq(Arrays.asList("order", "pay")), anyString(), anyString(), eq("token-cipher"),
                eq("https://hub.example.com"), any(java.util.Date.class));
    }

    @Test
    void duplicateRequestIdReturnsOriginalWithoutSendingAgain()
    {
        NotificationAcceptRequest request = request("req-2", "CARD", "标题", "摘要", "正文", Arrays.asList("pay", "order"));
        String hash = keyService.contentHash("CARD", "标题", "摘要", "正文", "order,pay");
        NotificationRecord existing = new NotificationRecord();
        existing.setRecordId(200L);
        existing.setRequestId("req-2");
        existing.setContentHash(hash);
        existing.setTokenCipher("token-cipher");
        existing.setDetailBaseUrl("https://hub.example.com");
        existing.setStatus(NotificationStatus.SUCCESS);
        when(recordMapper.selectRecordByRequestId(7L, "req-2")).thenReturn(existing);
        when(cryptoService.decrypt("token-cipher")).thenReturn("token");
        when(urlService.resolveRecordBaseUrlOrNull(existing)).thenReturn("https://hub.example.com");

        NotificationAcceptResponse response = service.accept("ntf_012345678901234567890123456789", request);

        assertTrue(response.isDuplicate());
        assertEquals(200L, response.getRecordId());
        assertEquals("https://hub.example.com/notification/detail/token", response.getDetailUrl());
        verify(recordWriter, never()).create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sameRequestIdWithDifferentContentIsRejected()
    {
        NotificationAcceptRequest request = request("req-3", "TEXT", "新标题", null, "正文", Collections.singletonList("order"));
        NotificationRecord existing = new NotificationRecord();
        existing.setRecordId(300L);
        existing.setRequestId("req-3");
        existing.setContentHash("different-hash");
        when(recordMapper.selectRecordByRequestId(7L, "req-3")).thenReturn(existing);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789", request));

        assertEquals(409, exception.getCode().intValue());
        verify(recordWriter, never()).create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void cardRequiresSummaryAndDetailAndTextRequiresBody()
    {
        NotificationAcceptRequest cardNoSummary = request("req-card-1", "CARD", "标题", null, "正文",
                Collections.singletonList("order"));
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789", cardNoSummary)).getCode().intValue());

        NotificationAcceptRequest cardNoDetail = request("req-card-2", "CARD", "标题", "摘要", null,
                Collections.singletonList("order"));
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789", cardNoDetail)).getCode().intValue());

        NotificationAcceptRequest emptyText = request("req-text", "TEXT", "标题", null, null,
                Collections.singletonList("order"));
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789", emptyText)).getCode().intValue());
        verify(recordWriter, never()).create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void duplicateKeyRaceFallsBackToOriginalRecord()
    {
        NotificationAcceptRequest request = request("req-4", "TEXT", "标题", null, "正文", Collections.singletonList("order"));
        String hash = keyService.contentHash("TEXT", "标题", null, "正文", "order");
        NotificationRecord existing = new NotificationRecord();
        existing.setRecordId(400L);
        existing.setRequestId("req-4");
        existing.setContentHash(hash);
        existing.setStatus(NotificationStatus.PENDING);
        when(recordMapper.selectRecordByRequestId(7L, "req-4")).thenReturn(null, existing);
        when(recordWriter.create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("uk_notification_record_request"));

        NotificationAcceptResponse response = service.accept("ntf_012345678901234567890123456789", request);

        assertTrue(response.isDuplicate());
        assertEquals(400L, response.getRecordId());
    }

    @Test
    void missingOrDisabledCredentialCannotAccept()
    {
        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.accept("", request("req-header", "TEXT", "标题", null, null,
                        Collections.singletonList("order"))));
        assertEquals(401, missing.getCode().intValue());

        when(credentialService.authenticate(anyString()))
                .thenThrow(new ServiceException("调用凭证无效或已禁用", 401));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789",
                        request("req-5", "TEXT", "标题", null, null, Collections.singletonList("order"))));
        assertEquals(401, exception.getCode().intValue());
        verify(recordWriter, never()).create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void missingCryptoOrPublicUrlDoesNotPersist()
    {
        NotificationAcceptRequest request = request("req-6", "TEXT", "标题", null, "正文", Collections.singletonList("order"));
        when(recordMapper.selectRecordByRequestId(7L, "req-6")).thenReturn(null);
        org.mockito.Mockito.doThrow(new ServiceException("通知主密钥未配置", 503))
                .when(cryptoService).requireAvailable();

        assertThrows(ServiceException.class,
                () -> service.accept("ntf_012345678901234567890123456789", request));
        verify(recordWriter, never()).create(any(), any(), anyList(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private NotificationAcceptRequest request(String requestId, String type, String title, String summary,
            String detail, java.util.List<String> routes)
    {
        NotificationAcceptRequest request = new NotificationAcceptRequest();
        request.setRequestId(requestId);
        request.setMessageType(type);
        request.setTitle(title);
        request.setSummary(summary);
        request.setDetail(detail);
        request.setRouteCodes(routes);
        return request;
    }
}
