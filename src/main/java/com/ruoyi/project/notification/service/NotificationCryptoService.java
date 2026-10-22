package com.ruoyi.project.notification.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.notification.config.NotificationProperties;

/**
 * 通知渠道 Secret 与详情票据的 AES-GCM 加解密。
 *
 * <p>主密钥来自 notification.master-key / NOTIFICATION_MASTER_KEY，只用于派生 256 bit AES Key。
 * 缺少或过短时 isAvailable 返回 false：保存 Secret、接单与发送都会被拒绝，但应用仍可正常启动。
 * 本类不记录任何明文、密文或主密钥。</p>
 *
 * @author weishanHub
 */
@Component
public class NotificationCryptoService
{
    private static final String PREFIX = "v1:";

    private static final int IV_LENGTH = 12;

    private static final int TAG_BITS = 128;

    private static final int MIN_MASTER_KEY_LENGTH = 16;

    private final NotificationProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public NotificationCryptoService(NotificationProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 主密钥是否可用于加解密。缺少时只禁用通知相关保存/发送。
     */
    public boolean isAvailable()
    {
        String masterKey = properties.getMasterKey();
        return StringUtils.isNotBlank(masterKey) && masterKey.length() >= MIN_MASTER_KEY_LENGTH;
    }

    public void requireAvailable()
    {
        if (!isAvailable())
        {
            throw new ServiceException(
                    "后端通知加密主密钥未配置或长度不足：请勿将企业微信应用 Secret 当作主密钥。"
                    + "请在 server/config/application-local.yml 配置 notification.master-key，"
                    + "或设置环境变量 NOTIFICATION_MASTER_KEY（至少 16 字符，推荐 32 字节随机值），"
                    + "修改后重启后端生效。",
                    503);
        }
    }

    /**
     * 加密敏感值，返回 v1: + Base64(IV + 密文 + GCM Tag)。
     */
    public String encrypt(String plainText)
    {
        requireAvailable();
        if (plainText == null)
        {
            return null;
        }
        try
        {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        }
        catch (Exception e)
        {
            // 不输出明文、主密钥或底层异常细节
            throw new ServiceException("通知密文加密失败", 500);
        }
    }

    /**
     * 解密敏感值。主密钥缺失或密文损坏时抛出统一异常。
     */
    public String decrypt(String cipherText)
    {
        requireAvailable();
        if (StringUtils.isBlank(cipherText) || !cipherText.startsWith(PREFIX))
        {
            throw new ServiceException("通知密文格式不正确", 500);
        }
        try
        {
            byte[] payload = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH)
            {
                throw new IllegalArgumentException("invalid payload");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new ServiceException("通知密文解密失败", 500);
        }
    }

    private SecretKeySpec aesKey()
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(properties.getMasterKey().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        }
        catch (Exception e)
        {
            throw new ServiceException("通知主密钥初始化失败", 500);
        }
    }
}
