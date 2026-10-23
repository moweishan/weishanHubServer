package com.ruoyi.project.home.service;

import com.ruoyi.common.utils.StringUtils;

/**
 * 访问者 IP 分类：高德 IP 定位只支持公网 IPv4，私网、IPv6 和未知地址直接提示手动选城。
 *
 * @author weishanHub
 */
public final class IpAddressPolicy
{
    public enum Kind
    {
        PUBLIC_IPV4,
        PRIVATE_IPV4,
        IPV6,
        UNKNOWN
    }

    private IpAddressPolicy()
    {
    }

    public static Kind classify(String ip)
    {
        if (StringUtils.isBlank(ip))
        {
            return Kind.UNKNOWN;
        }
        String value = ip.trim();
        if (value.startsWith("[") || value.indexOf(':') >= 0)
        {
            return Kind.IPV6;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4)
        {
            return Kind.UNKNOWN;
        }
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++)
        {
            if (parts[i].isEmpty() || parts[i].length() > 3)
            {
                return Kind.UNKNOWN;
            }
            for (int j = 0; j < parts[i].length(); j++)
            {
                if (!Character.isDigit(parts[i].charAt(j)))
                {
                    return Kind.UNKNOWN;
                }
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] < 0 || octets[i] > 255)
            {
                return Kind.UNKNOWN;
            }
        }
        return isNonPublicIpv4(octets) ? Kind.PRIVATE_IPV4 : Kind.PUBLIC_IPV4;
    }

    /**
     * 判断是否为不可用于公网定位的 IPv4（私网、回环、链路本地、保留、组播等）。
     */
    private static boolean isNonPublicIpv4(int[] octets)
    {
        int a = octets[0];
        int b = octets[1];
        int c = octets[2];
        if (a == 0 || a == 10 || a == 127 || a >= 224)
        {
            return true;
        }
        if (a == 100 && b >= 64 && b <= 127)
        {
            return true;
        }
        if (a == 169 && b == 254)
        {
            return true;
        }
        if (a == 172 && b >= 16 && b <= 31)
        {
            return true;
        }
        if (a == 192 && b == 168)
        {
            return true;
        }
        if (a == 192 && b == 0 && (c == 0 || c == 2))
        {
            return true;
        }
        if (a == 198 && (b == 18 || b == 19))
        {
            return true;
        }
        if (a == 198 && b == 51 && c == 100)
        {
            return true;
        }
        if (a == 203 && b == 0 && c == 113)
        {
            return true;
        }
        return false;
    }
}
