package com.ruoyi.project.home.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 只解析 Mapper XML 并检查 SQL 契约，不连接数据库。
 *
 * <p>今日通知必须按父记录 sent_at 半开区间计数：不按 create_time/status，不关联渠道，
 * 不排除 soft-delete（deleted），因此 SQL 中不能出现这些条件。</p>
 */
class HomeMapperXmlTest
{
    private Configuration configuration;

    @BeforeEach
    void setUp()
    {
        configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.project.home.domain");
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.project.notification.domain");
    }

    @Test
    void mapperXmlCanBeParsedAndNotificationCountSqlMatchesContract() throws Exception
    {
        parse("mybatis/home/HomeShortcutMapper.xml");
        parse("mybatis/notification/NotificationRecordMapper.xml");

        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.home.mapper.HomeShortcutMapper.selectByUserId"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.home.mapper.HomeShortcutMapper.insertShortcut"));
        assertTrue(configuration.hasStatement(
                "com.ruoyi.project.notification.mapper.NotificationRecordMapper.countFirstSentBetween"));

        Map<String, Object> params = new HashMap<>();
        params.put("begin", new Date());
        params.put("end", new Date());
        BoundSql boundSql = configuration.getMappedStatement(
                "com.ruoyi.project.notification.mapper.NotificationRecordMapper.countFirstSentBetween")
                .getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ");

        assertTrue(sql.contains("count(1)"), sql);
        assertTrue(sql.contains("create_by = 'open-api'"), sql);
        assertTrue(sql.contains("sent_at >= ?"), sql);
        assertTrue(sql.contains("sent_at < ?"), sql);
        assertFalse(sql.contains("deleted"), sql);
        assertFalse(sql.contains("status"), sql);
        assertFalse(sql.contains("create_time"), sql);
        assertFalse(sql.contains("notification_record_channel"), sql);
    }

    private void parse(String resource) throws Exception
    {
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder builder = new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments());
            builder.parse();
        }
    }
}
