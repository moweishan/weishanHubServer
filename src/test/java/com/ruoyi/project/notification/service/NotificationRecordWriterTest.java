package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.common.exception.ServiceException;
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
 * 接单事务：全路径校验失败不落库，成功时复制完整渠道快照。
 */
class NotificationRecordWriterTest
{
    private NotificationChannelMapper channelMapper;

    private NotificationRecordMapper recordMapper;

    private NotificationRecordChannelMapper recordChannelMapper;

    private NotificationRecordWriter writer;

    private NotificationCredential credential;

    @BeforeEach
    void setUp()
    {
        channelMapper = org.mockito.Mockito.mock(NotificationChannelMapper.class);
        recordMapper = org.mockito.Mockito.mock(NotificationRecordMapper.class);
        recordChannelMapper = org.mockito.Mockito.mock(NotificationRecordChannelMapper.class);
        writer = new NotificationRecordWriter(channelMapper, recordMapper, recordChannelMapper);
        credential = new NotificationCredential();
        credential.setCredentialId(1L);
    }

    @Test
    void missingOrDisabledRouteRejectsWholeAccept()
    {
        NotificationChannel channel = channel("a", "SecretA");
        when(channelMapper.selectEnabledByRouteCodesForUpdate(Arrays.asList("a", "b")))
                .thenReturn(Collections.singletonList(channel));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> writer.create(credential, request("r1", "CARD"), Arrays.asList("a", "b"),
                        "hash", "tokenHash", "tokenCipher", "https://hub.example.com", new Date()));

        assertEquals(400, exception.getCode().intValue());
        verify(recordMapper, never()).insertRecord(any(NotificationRecord.class));
        verify(recordChannelMapper, never()).batchInsertChannels(anyList());
    }

    @Test
    void channelWithoutSecretRejectsAccept()
    {
        NotificationChannel channel = channel("a", null);
        when(channelMapper.selectEnabledByRouteCodesForUpdate(Collections.singletonList("a")))
                .thenReturn(Collections.singletonList(channel));

        assertThrows(ServiceException.class, () -> writer.create(credential, request("r2", "TEXT"),
                Collections.singletonList("a"), "hash", "tokenHash", "tokenCipher", "https://hub.example.com",
                new Date()));
        verify(recordMapper, never()).insertRecord(any(NotificationRecord.class));
    }

    @Test
    void copiesEverySnapshotFieldIntoChildRows()
    {
        NotificationChannel a = channel("a", "SecretA");
        a.setChannelId(11L);
        a.setChannelName("渠道A");
        a.setCorpId("corp-a");
        a.setAgentId(1001);
        a.setToUser("user-a");
        NotificationChannel b = channel("b", "SecretB");
        b.setChannelId(12L);
        b.setChannelName("渠道B");
        b.setCorpId("corp-b");
        b.setAgentId(1002);
        b.setToUser("user-b");
        when(channelMapper.selectEnabledByRouteCodesForUpdate(Arrays.asList("a", "b"))).thenReturn(Arrays.asList(a, b));
        when(recordMapper.insertRecord(any(NotificationRecord.class))).thenAnswer(invocation -> {
            NotificationRecord record = invocation.getArgument(0);
            record.setRecordId(88L);
            return 1;
        });
        when(recordChannelMapper.batchInsertChannels(anyList())).thenReturn(2);

        NotificationRecord record = writer.create(credential, request("r3", "CARD"), Arrays.asList("a", "b"),
                "hash", "tokenHash", "tokenCipher", "https://hub.example.com", new Date());

        assertEquals(88L, record.getRecordId());
        assertEquals("https://hub.example.com", record.getDetailBaseUrl());
        assertEquals(NotificationStatus.PENDING, record.getStatus());
        assertEquals(2, record.getTotalCount());
        assertEquals("a,b", record.getRouteCodes());
        ArgumentCaptor<List<NotificationRecordChannel>> captor = ArgumentCaptor.forClass(List.class);
        verify(recordChannelMapper).batchInsertChannels(captor.capture());
        List<NotificationRecordChannel> children = captor.getValue();
        assertEquals(2, children.size());
        assertEquals("corp-a", children.get(0).getCorpId());
        assertEquals(Integer.valueOf(1001), children.get(0).getAgentId());
        assertEquals("user-a", children.get(0).getToUser());
        assertEquals("SecretA", children.get(0).getSecretCipher());
        assertEquals("SecretB", children.get(1).getSecretCipher());
        assertEquals(NotificationStatus.PENDING, children.get(0).getStatus());
        assertEquals("open-api", record.getCreateBy());
    }

    private NotificationChannel channel(String route, String secret)
    {
        NotificationChannel channel = new NotificationChannel();
        channel.setRouteCode(route);
        channel.setChannelName("渠道" + route);
        channel.setProvider("WECOM_APP");
        channel.setCorpId("corp");
        channel.setAgentId(1000);
        channel.setToUser("user");
        channel.setSecretCipher(secret);
        channel.setEnabled("0");
        return channel;
    }

    private NotificationAcceptRequest request(String requestId, String type)
    {
        NotificationAcceptRequest request = new NotificationAcceptRequest();
        request.setRequestId(requestId);
        request.setMessageType(type);
        request.setTitle("标题");
        request.setSummary("摘要");
        request.setDetail("正文");
        request.setRouteCodes(Arrays.asList("a", "b"));
        return request;
    }
}
