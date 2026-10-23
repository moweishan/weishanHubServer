package com.ruoyi.project.home.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.project.home.domain.HomeShortcutOption;
import com.ruoyi.project.home.domain.HomeShortcutSaveRequest;
import com.ruoyi.project.home.service.HomeShortcutService;

/**
 * 个人工作台快捷入口接口。
 *
 * <p>用户身份只从登录身份读取，接口不接收 userId，避免跨用户读写。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/home/shortcuts")
public class HomeShortcutController
{
    private final HomeShortcutService shortcutService;

    public HomeShortcutController(HomeShortcutService shortcutService)
    {
        this.shortcutService = shortcutService;
    }

    @GetMapping
    public AjaxResult list()
    {
        List<HomeShortcutOption> shortcuts = shortcutService.getShortcuts(SecurityUtils.getUserId());
        return AjaxResult.success(shortcuts);
    }

    @GetMapping("/options")
    public AjaxResult options()
    {
        List<HomeShortcutOption> options = shortcutService.getOptions(SecurityUtils.getUserId());
        return AjaxResult.success(options);
    }

    @PutMapping
    public AjaxResult save(@RequestBody(required = false) HomeShortcutSaveRequest request)
    {
        shortcutService.save(SecurityUtils.getUserId(), request == null ? null : request.getMenuIds());
        return AjaxResult.success();
    }
}
