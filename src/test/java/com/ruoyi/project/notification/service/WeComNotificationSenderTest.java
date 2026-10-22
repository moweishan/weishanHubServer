package com.ruoyi.project.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.WxCpMessageService;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import me.chanjar.weixin.cp.bean.message.WxCpMessageSendResult;

/**
 * 企业微信 SDK 发送结果判定：textcard 只含标题/摘要/链接、TEXT 纯文本、错误码与全部接收人无效不误报。
 */
class WeComNotificationSenderTest
{
    private WxCpClientFactory clientFactory;

    private WxCpService cpService;

    private WxCpMessageService messageService;

    private WeComNotificationSender sender;

    private NotificationRecord record;

    private NotificationRecordChannel channel;

    @BeforeEach
    void setUp()
    {
        clientFactory = mock(WxCpClientFactory.class);
        cpService = mock(WxCpService.class);
        messageService = mock(WxCpMessageService.class);
        sender = new WeComNotificationSender(clientFactory);
        when(clientFactory.create("corp", 1001, "plain-secret")).thenReturn(cpService);
        when(cpService.getMessageService()).thenReturn(messageService);

        record = new NotificationRecord();
        record.setTitle("订单告警");
        record.setSummary("订单 123 支付超时");
        record.setMessageType("CARD");
        record.setDetail("DETAIL-MARKER-不应出现在卡片消息中");
        channel = new NotificationRecordChannel();
        channel.setCorpId("corp");
        channel.setAgentId(1001);
        channel.setToUser("u1|u2");
        channel.setProvider("WECOM_APP");
    }

    @Test
    void cardUsesTextCardWithTitleSummaryAndUrlOnly() throws Exception
    {
        WxCpMessageSendResult result = new WxCpMessageSendResult();
        result.setErrCode(0);
        result.setMsgId("msg-1");
        when(messageService.send(any(WxCpMessage.class))).thenReturn(result);

        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret",
                "https://hub.example.com/notification/detail/token");

        assertTrue(sendResult.isSuccess());
        assertEquals("msg-1", sendResult.getMessageId());
        ArgumentCaptor<WxCpMessage> captor = ArgumentCaptor.forClass(WxCpMessage.class);
        verify(messageService).send(captor.capture());
        WxCpMessage message = captor.getValue();
        assertEquals("textcard", message.getMsgType());
        assertEquals(Integer.valueOf(1001), message.getAgentId());
        assertEquals("u1|u2", message.getToUser());
        assertEquals("订单告警", message.getTitle());
        assertEquals("订单 123 支付超时", message.getDescription());
        assertEquals("https://hub.example.com/notification/detail/token", message.getUrl());
        assertEquals("查看详情", message.getBtnTxt());
        assertFalse(message.getDescription().contains("DETAIL-MARKER"));
        assertFalse(message.toJson().contains("DETAIL-MARKER"));
    }

    @Test
    void textUsesTextTypeAndDetailAsBody() throws Exception
    {
        record.setMessageType("TEXT");
        record.setDetail("纯文本正文");
        WxCpMessageSendResult result = new WxCpMessageSendResult();
        result.setErrCode(0);
        when(messageService.send(any(WxCpMessage.class))).thenReturn(result);

        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret", "https://hub/detail/t");

        assertTrue(sendResult.isSuccess());
        ArgumentCaptor<WxCpMessage> captor = ArgumentCaptor.forClass(WxCpMessage.class);
        verify(messageService).send(captor.capture());
        assertEquals("text", captor.getValue().getMsgType());
        assertTrue(captor.getValue().getContent().contains("纯文本正文"));
        assertTrue(captor.getValue().getContent().contains("https://hub/detail/t"));
    }

    @Test
    void nonZeroErrCodeIsFailureAndDoesNotLeakSecret() throws Exception
    {
        WxCpMessageSendResult result = new WxCpMessageSendResult();
        result.setErrCode(81013);
        result.setErrMsg("user invalid with access_token=leak");
        when(messageService.send(any(WxCpMessage.class))).thenReturn(result);

        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret", "https://hub/detail/t");

        assertFalse(sendResult.isSuccess());
        assertTrue(sendResult.getErrorMessage().contains("81013"));
        assertFalse(sendResult.getErrorMessage().contains("plain-secret"));
        assertFalse(sendResult.getErrorMessage().contains("access_token"));
    }

    @Test
    void allConfiguredRecipientsInvalidIsFailure() throws Exception
    {
        WxCpMessageSendResult result = new WxCpMessageSendResult();
        result.setErrCode(0);
        result.setInvalidUser("u1|u2");
        when(messageService.send(any(WxCpMessage.class))).thenReturn(result);

        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret", "https://hub/detail/t");

        assertFalse(sendResult.isSuccess());
        assertTrue(sendResult.getErrorMessage().contains("接收人无效"));
    }

    @Test
    void partiallyInvalidRecipientsStillCountsAsPlatformAccepted() throws Exception
    {
        WxCpMessageSendResult result = new WxCpMessageSendResult();
        result.setErrCode(0);
        result.setInvalidUser("u1");
        result.setMsgId("msg-2");
        when(messageService.send(any(WxCpMessage.class))).thenReturn(result);

        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret", "https://hub/detail/t");

        assertTrue(sendResult.isSuccess());
    }

    @Test
    void missingErrCodeIsNotReportedAsSuccess() throws Exception
    {
        when(messageService.send(any(WxCpMessage.class))).thenReturn(new WxCpMessageSendResult());
        NotificationSendResult sendResult = sender.send(record, channel, "plain-secret", "https://hub/detail/t");
        assertFalse(sendResult.isSuccess());
    }

    @Test
    void textCardEscapesHtmlAndTruncatesByUtf8WithoutSplittingEntities()
    {
        record.setTitle("<script>" + repeat("标", 60));
        record.setSummary("A&B<C>" + repeat("摘", 220));
        WxCpMessage message = sender.buildMessage(record, channel, "https://hub/detail/t");

        assertTrue(message.getTitle().startsWith("&lt;script&gt;"));
        assertTrue(message.getTitle().getBytes(StandardCharsets.UTF_8).length <= 128);
        assertTrue(message.getDescription().startsWith("A&amp;B&lt;C&gt;"));
        assertTrue(message.getDescription().getBytes(StandardCharsets.UTF_8).length <= 512);
        assertFalse(message.getDescription().endsWith("&"));
    }

    private String repeat(String value, int count)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++)
        {
            builder.append(value);
        }
        return builder.toString();
    }
}
