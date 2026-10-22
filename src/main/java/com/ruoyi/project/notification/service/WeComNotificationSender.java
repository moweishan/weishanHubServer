package com.ruoyi.project.notification.service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.domain.NotificationRecordChannel;
import com.ruoyi.project.notification.domain.NotificationSendResult;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import me.chanjar.weixin.cp.bean.message.WxCpMessageSendResult;

/**
 * 企业微信自建应用发送实现。
 *
 * <p>TEXT 使用文本消息；CARD 使用企业微信 textcard，只发送 title + summary 并跳转详情页，
 * 详细 Markdown 绝不下发到消息。平台返回 errcode=0 即视为接受（不要求已读）；
 * 若平台明确返回全部接收人无效，则按真实失败处理。</p>
 *
 * @author weishanHub
 */
@Component
public class WeComNotificationSender implements NotificationSender
{
    /** 企业微信文本消息内容上限 2048 字节，预留少量余量 */
    private static final int MAX_TEXT_BYTES = 2000;

    /** textcard 标题上限 128 UTF-8 字节 */
    private static final int MAX_CARD_TITLE_BYTES = 128;

    /** textcard 描述上限 512 UTF-8 字节 */
    private static final int MAX_CARD_DESCRIPTION_BYTES = 512;

    private final WxCpClientFactory clientFactory;

    public WeComNotificationSender(WxCpClientFactory clientFactory)
    {
        this.clientFactory = clientFactory;
    }

    @Override
    public String provider()
    {
        return "WECOM_APP";
    }

    @Override
    public NotificationSendResult send(NotificationRecord record, NotificationRecordChannel channel,
            String plainSecret, String detailUrl)
    {
        WxCpService service = null;
        try
        {
            service = clientFactory.create(channel.getCorpId(), channel.getAgentId(), plainSecret);
            WxCpMessage message = buildMessage(record, channel, detailUrl);
            WxCpMessageSendResult result = service.getMessageService().send(message);
            if (result == null)
            {
                return NotificationSendResult.failure("企业微信未返回发送结果");
            }
            Integer errCode = result.getErrCode();
            if (errCode == null)
            {
                return NotificationSendResult.failure("企业微信未返回错误码，发送结果未知");
            }
            if (errCode != 0)
            {
                return NotificationSendResult.failure("企业微信返回错误 " + errCode + "（平台拒绝发送）");
            }
            if (allRecipientsInvalid(channel.getToUser(), result))
            {
                return NotificationSendResult.failure("接收人无效，企业微信未投递给任何配置成员");
            }
            // errcode=0 表示平台已接受，不要求用户已读
            return NotificationSendResult.success(result.getMsgId());
        }
        catch (WxErrorException e)
        {
            WxError error = e.getError();
            if (error != null)
            {
                return NotificationSendResult.failure("企业微信返回错误 " + error.getErrorCode() + "（平台拒绝发送）");
            }
            return NotificationSendResult.failure("企业微信发送失败");
        }
        catch (RuntimeException e)
        {
            // 不原样回传 SDK 异常信息，避免 URL/access_token/CorpSecret 等进入记录或日志
            return NotificationSendResult.failure("企业微信发送异常（网络或协议错误）");
        }
    }

    /**
     * 构造 SDK 消息。CARD 只携带标题、摘要和详情链接；TEXT 使用纯文本正文。
     */
    WxCpMessage buildMessage(NotificationRecord record, NotificationRecordChannel channel, String detailUrl)
    {
        String messageType = record.getMessageType();
        if ("CARD".equals(messageType))
        {
            String title = escapeAndTruncate(oneLine(record.getTitle()), MAX_CARD_TITLE_BYTES);
            String description = escapeAndTruncate(StringUtils.defaultString(record.getSummary()),
                    MAX_CARD_DESCRIPTION_BYTES);
            return WxCpMessage.TEXTCARD()
                    .title(title)
                    .description(description)
                    .url(detailUrl)
                    .btnTxt("查看详情")
                    .agentId(channel.getAgentId())
                    .toUser(channel.getToUser())
                    .build();
        }
        if ("TEXT".equals(messageType))
        {
            return WxCpMessage.TEXT()
                    .agentId(channel.getAgentId())
                    .toUser(channel.getToUser())
                    .content(composeText(record, detailUrl))
                    .build();
        }
        throw new IllegalArgumentException("不支持的消息类型");
    }

    private String composeText(NotificationRecord record, String detailUrl)
    {
        String title = oneLine(record.getTitle());
        String body = StringUtils.isNotBlank(record.getDetail())
                ? record.getDetail() : StringUtils.defaultString(record.getSummary());
        String suffix = "\n\n查看详情：" + detailUrl;
        int available = MAX_TEXT_BYTES - utf8Length(suffix);
        return truncateUtf8(title + "\n\n" + body, Math.max(0, available)) + suffix;
    }

    private boolean allRecipientsInvalid(String toUser, WxCpMessageSendResult result)
    {
        if (StringUtils.isBlank(toUser) || toUser.contains("@all"))
        {
            return false;
        }
        List<String> invalidUsers = result.getInvalidUserList();
        if (invalidUsers == null || invalidUsers.isEmpty())
        {
            return false;
        }
        Set<String> invalid = new HashSet<>(invalidUsers);
        int configured = 0;
        int invalidCount = 0;
        for (String user : toUser.split("\\|"))
        {
            String trimmed = user == null ? null : user.trim();
            if (StringUtils.isBlank(trimmed))
            {
                continue;
            }
            configured++;
            if (invalid.contains(trimmed))
            {
                invalidCount++;
            }
        }
        return configured > 0 && invalidCount == configured;
    }

    private String oneLine(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * 先转义 HTML 特殊字符，再按字节截断；截断时保证不拆开 UTF-8 码点或 HTML 实体。
     */
    String escapeAndTruncate(String value, int maxBytes)
    {
        String escaped = escapeHtml(StringUtils.defaultString(value));
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        int index = 0;
        while (index < escaped.length())
        {
            int codePoint = escaped.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            String token = escaped.substring(index, index + charCount);
            int tokenBytes = utf8Length(token);
            if (token.equals("&"))
            {
                int end = escaped.indexOf(';', index);
                if (end > index && end - index <= 10)
                {
                    token = escaped.substring(index, end + 1);
                    charCount = token.length();
                    tokenBytes = utf8Length(token);
                }
            }
            if (bytes + tokenBytes > maxBytes)
            {
                break;
            }
            builder.append(token);
            bytes += tokenBytes;
            index += charCount;
        }
        return builder.toString();
    }

    private String escapeHtml(String value)
    {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String truncateUtf8(String value, int maxBytes)
    {
        if (value == null || maxBytes <= 0)
        {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int bytes = 0;
        int index = 0;
        while (index < value.length())
        {
            int codePoint = value.codePointAt(index);
            int length = utf8Length(codePoint);
            if (bytes + length > maxBytes)
            {
                break;
            }
            builder.appendCodePoint(codePoint);
            bytes += length;
            index += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    private int utf8Length(String value)
    {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private int utf8Length(int codePoint)
    {
        if (codePoint <= 0x7F)
        {
            return 1;
        }
        if (codePoint <= 0x7FF)
        {
            return 2;
        }
        if (codePoint <= 0xFFFF)
        {
            return 3;
        }
        return 4;
    }
}
