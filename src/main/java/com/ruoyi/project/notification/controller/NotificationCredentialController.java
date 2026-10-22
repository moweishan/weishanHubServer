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
import com.ruoyi.project.notification.domain.NotificationCredential;
import com.ruoyi.project.notification.domain.NotificationCredentialRequest;
import com.ruoyi.project.notification.service.NotificationCredentialService;

/**
 * 通知调用凭证管理接口。
 *
 * <p>创建/重置的响应包含一次性 API Key，因此关闭该方法的响应审计，并排除 apiKey 参数，
 * 避免凭据进入操作日志。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/notification/credential")
public class NotificationCredentialController extends BaseController
{
    private final NotificationCredentialService credentialService;

    public NotificationCredentialController(NotificationCredentialService credentialService)
    {
        this.credentialService = credentialService;
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:list')")
    @GetMapping("/list")
    public TableDataInfo list(NotificationCredential query)
    {
        startPage();
        List<NotificationCredential> list = credentialService.selectCredentialList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:list')")
    @GetMapping("/{credentialId}")
    public AjaxResult getInfo(@PathVariable Long credentialId)
    {
        return success(credentialService.selectCredentialById(credentialId));
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:add')")
    @Log(title = "调用凭证", businessType = BusinessType.INSERT, isSaveResponseData = false,
            excludeParamNames = { "apiKey" })
    @PostMapping
    public AjaxResult add(@Validated @RequestBody NotificationCredentialRequest request)
    {
        return success(credentialService.createCredential(request, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:edit')")
    @Log(title = "调用凭证", businessType = BusinessType.UPDATE,
            excludeParamNames = { "apiKey" })
    @PutMapping("/{credentialId}")
    public AjaxResult edit(@PathVariable Long credentialId,
            @Validated @RequestBody NotificationCredentialRequest request)
    {
        credentialService.updateCredential(credentialId, request, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:reset')")
    @Log(title = "调用凭证", businessType = BusinessType.UPDATE, isSaveResponseData = false,
            excludeParamNames = { "apiKey" })
    @PutMapping("/{credentialId}/reset")
    public AjaxResult reset(@PathVariable Long credentialId)
    {
        return success(credentialService.resetCredential(credentialId, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('notification:credential:remove')")
    @Log(title = "调用凭证", businessType = BusinessType.DELETE)
    @DeleteMapping("/{credentialIds}")
    public AjaxResult remove(@PathVariable Long[] credentialIds)
    {
        credentialService.deleteCredentials(credentialIds);
        return success();
    }
}
