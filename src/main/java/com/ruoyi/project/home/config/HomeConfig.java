package com.ruoyi.project.home.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 个人工作台基础 Bean。
 *
 * <p>通知统计固定按北京时间自然日计算，因此单独提供工作台时钟，避免依赖服务器默认时区。</p>
 *
 * @author weishanHub
 */
@Configuration
public class HomeConfig
{
    public static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean(name = "homeClock")
    public Clock homeClock()
    {
        return Clock.system(BEIJING_ZONE);
    }
}
