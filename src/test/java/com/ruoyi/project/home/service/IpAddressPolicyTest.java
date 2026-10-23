package com.ruoyi.project.home.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 访问者 IP 分类：只允许公网 IPv4 自动定位；行政区划只接受中国大陆编码。
 */
class IpAddressPolicyTest
{
    @Test
    void publicIpv4CanBeLocated()
    {
        assertEquals(IpAddressPolicy.Kind.PUBLIC_IPV4, IpAddressPolicy.classify("8.8.8.8"));
        assertEquals(IpAddressPolicy.Kind.PUBLIC_IPV4, IpAddressPolicy.classify("223.5.5.5"));
        assertEquals(IpAddressPolicy.Kind.PUBLIC_IPV4, IpAddressPolicy.classify("203.0.114.1"));
    }

    @Test
    void privateAndReservedIpv4AreRejected()
    {
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("127.0.0.1"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("10.1.2.3"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("172.16.0.1"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("172.31.255.255"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("192.168.1.1"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("100.64.0.1"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("169.254.1.1"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("0.0.0.0"));
        assertEquals(IpAddressPolicy.Kind.PRIVATE_IPV4, IpAddressPolicy.classify("255.255.255.255"));
    }

    @Test
    void ipv6AndUnknownAreRejected()
    {
        assertEquals(IpAddressPolicy.Kind.IPV6, IpAddressPolicy.classify("2408:8207:1234::1"));
        assertEquals(IpAddressPolicy.Kind.IPV6, IpAddressPolicy.classify("::1"));
        assertEquals(IpAddressPolicy.Kind.UNKNOWN, IpAddressPolicy.classify("unknown"));
        assertEquals(IpAddressPolicy.Kind.UNKNOWN, IpAddressPolicy.classify(""));
        assertEquals(IpAddressPolicy.Kind.UNKNOWN, IpAddressPolicy.classify("999.1.1.1"));
        assertEquals(IpAddressPolicy.Kind.UNKNOWN, IpAddressPolicy.classify("1.2.3"));
    }

    @Test
    void onlyMainlandAdcodesAreAccepted()
    {
        assertTrue(ChinaRegion.isMainlandAdcode("330106"));
        assertTrue(ChinaRegion.isMainlandAdcode("110000"));
        assertTrue(ChinaRegion.isMainlandAdcode("659000"));
        assertFalse(ChinaRegion.isMainlandAdcode("710000"));
        assertFalse(ChinaRegion.isMainlandAdcode("810000"));
        assertFalse(ChinaRegion.isMainlandAdcode("820000"));
        assertFalse(ChinaRegion.isMainlandAdcode("100000"));
        assertFalse(ChinaRegion.isMainlandAdcode("abc"));
    }
}
