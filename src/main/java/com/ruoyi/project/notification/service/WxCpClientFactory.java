package com.ruoyi.project.notification.service;

import me.chanjar.weixin.cp.api.WxCpService;

/**
 * 企业微信客户端工厂，便于单元测试替换为 mock，避免测试发起真实网络请求。
 *
 * @author weishanHub
 */
public interface WxCpClientFactory
{
    WxCpService create(String corpId, Integer agentId, String secret);
}
