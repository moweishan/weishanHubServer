package com.ruoyi.project.notification.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.notification.domain.NotificationChannel;
import com.ruoyi.project.notification.domain.NotificationChannelRequest;
import com.ruoyi.project.notification.domain.NotificationDetailUrlRequest;
import com.ruoyi.project.notification.service.NotificationChannelService;
import com.ruoyi.project.notification.service.NotificationDetailUrlService;

/**
 * 通知渠道管理接口。所有管理变更都需要方法权限与审计日志。
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/notification/channel")
public class NotificationChannelController extends BaseController
{
    private final NotificationChannelService channelService;

    private final NotificationDetailUrlService detailUrlService;

    public NotificationChannelController(NotificationChannelService channelService,
            NotificationDetailUrlService detailUrlService)
    {
        this.channelService = channelService;
        this.detailUrlService = detailUrlService;
    }

    @PreAuthorize("@ss.hasPermi('notification:channel:list')")
    @GetMapping("/list")
    public TableDataInfo list(NotificationChannel query)
    {
        startPage();
        List<NotificationChannel> list = channelService.selectChannelList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasAnyPermi('notification:channel:list,notification:record:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(channelService.selectOptions());
    }

    /**
     * 所有路径共用的详情页地址。只读权限即可查看，无编辑权限时前端禁止保存。
     */
    @PreAuthorize("@ss.hasPermi('notification:channel:list')")
    @GetMapping("/detail-url")
    public AjaxResult getDetailUrl()
    {
        return success(detailUrlService.getSettings());
    }

    /**
     * 保存统一详情页地址：只改 notification.publicBaseUrl，保存后立即生效，仅影响后续新通知。
     */
    @PreAuthorize("@ss.hasPermi('notification:channel:edit')")
    @Log(title = "通知详情页地址", businessType = BusinessType.UPDATE)
    @PutMapping("/detail-url")
    public AjaxResult updateDetailUrl(@Validated @RequestBody NotificationDetailUrlRequest request)
    {
        return success(detailUrlService.save(request.getUrl(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('notification:channel:list')")
    @GetMapping("/{channelId}")
    public AjaxResult getInfo(@PathVariable Long channelId)
    {
        return success(channelService.selectChannelById(channelId));
    }

    @PreAuthorize("@ss.hasPermi('notification:channel:add')")
    @Log(title = "通知渠道", businessType = BusinessType.INSERT, excludeParamNames = { "secret" })
    @PostMapping
    public AjaxResult add(@Validated @RequestBody NotificationChannelRequest request)
    {
        return success(channelService.createChannel(request, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('notification:channel:edit')")
    @Log(title = "通知渠道", businessType = BusinessType.UPDATE, excludeParamNames = { "secret" })
    @PutMapping("/{channelId}")
    public AjaxResult edit(@PathVariable Long channelId,
            @Validated @RequestBody NotificationChannelRequest request)
    {
        channelService.updateChannel(channelId, request, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('notification:channel:remove')")
    @Log(title = "通知渠道", businessType = BusinessType.DELETE)
    @DeleteMapping("/{channelIds}")
    public AjaxResult remove(@PathVariable Long[] channelIds)
    {
        channelService.deleteChannels(channelIds);
        return success();
    }
}
