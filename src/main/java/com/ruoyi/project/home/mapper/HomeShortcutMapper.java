package com.ruoyi.project.home.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.home.domain.HomeShortcutEntity;

/**
 * 用户快捷入口数据层。
 *
 * @author weishanHub
 */
public interface HomeShortcutMapper
{
    List<HomeShortcutEntity> selectByUserId(Long userId);

    int deleteByUserId(Long userId);

    int insertShortcut(@Param("userId") Long userId, @Param("menuId") Long menuId,
            @Param("orderNum") Integer orderNum, @Param("now") Date now);
}
