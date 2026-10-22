package com.ruoyi.project.notification.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/**
 * 通知模块安全随机数与摘要工具。
 *
 * <p>API Key 与详情票据都使用 {@link SecureRandom} 生成 256 bit 随机值；
 * 数据库只保存 SHA-256 摘要（API Key）或摘要 + 加密票密文（详情票据）。</p>
 *
 * @author weishanHub
 */
@Component
public class NotificationKeyService
{
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** API Key 固定前缀，便于识别但本身不携带安全信息 */
    public static final String API_KEY_PREFIX = "ntf_";

    /**
     * 生成只展示一次的 API Key。
     */
    public String generateApiKey()
    {
        return API_KEY_PREFIX + base64Url(randomBytes(32));
    }

    /**
     * 生成 256 bit 详情票据。
     */
    public String generateDetailToken()
    {
        return base64Url(randomBytes(32));
    }

    /**
     * 生成随机 claim token，用于条件更新防重复领取。
     */
    public String generateClaimToken()
    {
        return base64Url(randomBytes(18));
    }

    public String sha256Hex(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    /**
     * 仅用于界面辨识的前缀，不包含完整凭据。
     */
    public String keyPrefix(String apiKey)
    {
        if (apiKey == null)
        {
            return null;
        }
        return apiKey.substring(0, Math.min(12, apiKey.length()));
    }

    /**
     * 校验详情票据格式：256 bit 随机值 Base64URL 编码后为 43 个字符。
     */
    public boolean isDetailTokenFormat(String token)
    {
        return token != null && token.length() >= 40 && token.length() <= 64
                && token.matches("[A-Za-z0-9_-]+");
    }

    /**
     * 幂等内容指纹。路径数组排序后参与摘要，避免仅顺序不同被判定为内容冲突。
     */
    public String contentHash(String messageType, String title, String summary, String detail, String sortedRouteCodes)
    {
        String normalized = String.valueOf(messageType) + "\n" + String.valueOf(title) + "\n"
                + String.valueOf(summary) + "\n" + String.valueOf(detail) + "\n" + String.valueOf(sortedRouteCodes);
        return sha256Hex(normalized);
    }

    private byte[] randomBytes(int length)
    {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String base64Url(byte[] bytes)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String toHex(byte[] bytes)
    {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++)
        {
            int b = bytes[i] & 0xFF;
            chars[i * 2] = HEX[b >>> 4];
            chars[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(chars);
    }

    /**
     * 校验 API Key 长度，避免异常超长请求进入摘要计算。
     */
    public void assertApiKeyFormat(String apiKey)
    {
        if (apiKey == null || apiKey.length() < 20 || apiKey.length() > 100 || !apiKey.startsWith(API_KEY_PREFIX))
        {
            throw new ServiceException("调用凭证无效或已禁用", 401);
        }
    }
}
