package com.ruoyi.project.tool.stress.controller;

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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskCreateRequest;
import com.ruoyi.project.tool.stress.service.IStressTaskService;
import com.ruoyi.project.tool.stress.service.StressTaskResponseService;

/**
 * 手机压力测试任务。
 *
 * <p>管理员可查看/操作全部任务，普通用户只能查看/操作自己的任务；
 * 服务层同时做所有者校验，按钮权限不能替代服务端鉴权。</p>
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/tool/stress/task")
public class StressTaskController extends BaseController
{
    private final IStressTaskService taskService;

    private final StressTaskResponseService responseService;

    public StressTaskController(IStressTaskService taskService, StressTaskResponseService responseService)
    {
        this.taskService = taskService;
        this.responseService = responseService;
    }

    /**
     * 分页查询任务列表。
     */
    @PreAuthorize("@ss.hasPermi('magic:mobileStressTest:view')")
    @GetMapping("/list")
    public TableDataInfo list(String phone, String status)
    {
        Long userId = getUserId();
        boolean admin = SecurityUtils.isAdmin(userId);
        startPage();
        List<StressTask> list = taskService.selectTaskList(phone, status, userId, admin);
        return getDataTable(list);
    }

    /**
     * 创建任务并立即开始。
     */
    @PreAuthorize("@ss.hasPermi('magic:mobileStressTest:add')")
    @Log(title = "手机压力测试", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody StressTaskCreateRequest request)
    {
        StressTask task = taskService.createTask(request, getUserId(), getUsername());
        return AjaxResult.success(task);
    }

    /**
     * 手动停止任务。
     */
    @PreAuthorize("@ss.hasPermi('magic:mobileStressTest:stop')")
    @Log(title = "手机压力测试", businessType = BusinessType.UPDATE)
    @PutMapping("/{taskId}/stop")
    public AjaxResult stop(@PathVariable Long taskId)
    {
        Long userId = getUserId();
        taskService.stopTask(taskId, userId, SecurityUtils.isAdmin(userId));
        return success("停止请求已发送");
    }

    /**
     * 分页查看任务请求结果。
     */
    @PreAuthorize("@ss.hasPermi('magic:mobileStressTest:result')")
    @GetMapping("/{taskId}/result/list")
    public TableDataInfo resultList(@PathVariable Long taskId)
    {
        Long userId = getUserId();
        taskService.checkTaskAccess(taskId, userId, SecurityUtils.isAdmin(userId));
        startPage();
        return getDataTable(responseService.selectResponsesByTaskId(taskId));
    }

    /**
     * 删除历史任务及其结果，运行中任务不允许删除。
     */
    @PreAuthorize("@ss.hasPermi('magic:mobileStressTest:remove')")
    @Log(title = "手机压力测试", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskId}")
    public AjaxResult remove(@PathVariable Long taskId)
    {
        Long userId = getUserId();
        taskService.deleteTask(taskId, userId, SecurityUtils.isAdmin(userId));
        return success();
    }
}
