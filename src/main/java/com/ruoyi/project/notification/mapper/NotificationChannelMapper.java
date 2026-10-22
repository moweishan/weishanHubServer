package com.ruoyi.project.notification.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.notification.domain.NotificationChannel;

/**
 * 通知渠道 Mapper。
 *
 * @author weishanHub
 */
public interface NotificationChannelMapper
{
    NotificationChannel selectChannelById(Long channelId);

    NotificationChannel selectChannelByRouteCode(String routeCode);

    List<NotificationChannel> selectChannelList(NotificationChannel query);

    /**
     * 事务内锁定并返回全部启用渠道；任一路径缺失或停用由服务层判断。
     */
    List<NotificationChannel> selectEnabledByRouteCodesForUpdate(@Param("routeCodes") List<String> routeCodes);

    List<NotificationChannel> selectEnabledChannels();

    int countByRouteCode(@Param("routeCode") String routeCode, @Param("excludeId") Long excludeId);

    int insertChannel(NotificationChannel channel);

    int updateChannel(NotificationChannel channel);

    int deleteChannelByIds(@Param("channelIds") Long[] channelIds);
}
