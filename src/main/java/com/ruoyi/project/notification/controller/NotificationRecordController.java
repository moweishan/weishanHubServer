package com.ruoyi.project.notification.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.notification.domain.NotificationRecord;
import com.ruoyi.project.notification.service.NotificationRecordService;

/**
 * 后台通知记录接口。
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/notification/record")
public class NotificationRecordController extends BaseController
{
    private final NotificationRecordService recordService;

    public NotificationRecordController(NotificationRecordService recordService)
    {
        this.recordService = recordService;
    }

    @PreAuthorize("@ss.hasPermi('notification:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(NotificationRecord query)
    {
        startPage();
        List<NotificationRecord> list = recordService.selectRecordList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('notification:record:query')")
    @GetMapping("/{recordId}")
    public AjaxResult getInfo(@PathVariable Long recordId)
    {
        return success(recordService.selectRecordDetail(recordId));
    }

    /**
     * 只重试失败渠道，成功渠道不会重复发送，sentAt/expiresAt 不会延长。
     */
    @PreAuthorize("@ss.hasPermi('notification:record:retry')")
    @Log(title = "通知记录", businessType = BusinessType.UPDATE)
    @PostMapping("/{recordId}/retry")
    public AjaxResult retry(@PathVariable Long recordId)
    {
        int count = recordService.retryFailed(recordId);
        return success("已提交 " + count + " 个失败渠道重试");
    }

    @PreAuthorize("@ss.hasPermi('notification:record:revoke')")
    @Log(title = "通知记录", businessType = BusinessType.UPDATE)
    @PutMapping("/{recordId}/revoke")
    public AjaxResult revoke(@PathVariable Long recordId)
    {
        recordService.revoke(recordId, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('notification:record:remove')")
    @Log(title = "通知记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        recordService.deleteRecords(recordIds, getUsername());
        return success();
    }
}
