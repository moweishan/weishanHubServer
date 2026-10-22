package com.ruoyi.project.tool.stress.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.framework.aspectj.lang.annotation.Log;
import com.ruoyi.framework.aspectj.lang.enums.BusinessType;
import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.page.TableDataInfo;
import com.ruoyi.project.tool.stress.domain.StressProbeRequest;
import com.ruoyi.project.tool.stress.domain.StressRequestConfig;
import com.ruoyi.project.tool.stress.service.IStressRequestConfigService;
import com.ruoyi.project.tool.stress.service.StressProbeService;

/**
 * 压力测试请求配置。
 *
 * @author weishanHub
 */
@RestController
@RequestMapping("/tool/stress/config")
public class StressRequestConfigController extends BaseController
{
    private final IStressRequestConfigService configService;

    private final StressProbeService probeService;

    private final ObjectMapper objectMapper;

    public StressRequestConfigController(IStressRequestConfigService configService,
            StressProbeService probeService, ObjectMapper objectMapper)
    {
        this.configService = configService;
        this.probeService = probeService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询请求配置列表。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:list')")
    @GetMapping("/list")
    public TableDataInfo list(StressRequestConfig config)
    {
        startPage();
        List<StressRequestConfig> list = configService.selectConfigList(config);
        return getDataTable(list);
    }

    /**
     * 查询请求配置详情。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:query')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        return success(configService.selectConfigById(configId));
    }

    /**
     * 新增请求配置。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:add')")
    @Log(title = "请求配置", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody StressRequestConfig config)
    {
        config.setCreateBy(getUsername());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改请求配置。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:edit')")
    @Log(title = "请求配置", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody StressRequestConfig config)
    {
        config.setUpdateBy(getUsername());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除请求配置。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:remove')")
    @Log(title = "请求配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        return toAjax(configService.deleteConfigByIds(configIds));
    }

    /**
     * 一键停用全部试活失效的配置。
     *
     * <p>只处理 {@code probe_status='dead'} 且当前仍启用的配置，重复执行不会影响已停用的记录。</p>
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:edit')")
    @Log(title = "请求配置停用失效", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/disable-dead")
    public AjaxResult disableDead()
    {
        int rows = configService.disableDeadConfigs(getUsername());
        return success("已停用 " + rows + " 个失效接口");
    }

    /**
     * 按查询条件导出请求配置为 JSON 文件。
     *
     * <p>导出内容只包含可回导字段（不含 configId、试活状态与审计字段），
     * 因此导出的文件可以直接用于 {@code /import} 回导。</p>
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:export')")
    @Log(title = "请求配置导出", businessType = BusinessType.EXPORT, isSaveRequestData = false)
    @PostMapping("/export")
    public void export(StressRequestConfig config, HttpServletResponse response) throws IOException
    {
        List<Map<String, Object>> rows = configService.exportConfigs(config);
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
        String fileName = "request-config-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".json";
        response.reset();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        FileUtils.setAttachmentResponseHeader(response, fileName);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    /**
     * 从 JSON 文件导入请求配置。
     *
     * <p>逐条按现有校验规则规范化；请求URL 已存在则跳过，失败条目在返回结果中说明原因。</p>
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:import')")
    @Log(title = "请求配置导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping("/import")
    public AjaxResult importConfigs(@RequestParam("file") MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("请选择要导入的 JSON 文件");
        }
        String json = new String(file.getBytes(), StandardCharsets.UTF_8);
        return success(configService.importConfigs(json, getUsername()));
    }

    /**
     * 批量试活：按条件或勾选的接口逐个发起一次真实请求，失效的接口置为失效状态。
     *
     * <p>试活在后台线程执行并立即返回，进度通过 {@code /probe/progress} 轮询。</p>
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:probe')")
    @Log(title = "请求配置试活", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @PostMapping("/probe")
    public AjaxResult probe(@RequestBody(required = false) StressProbeRequest request)
    {
        return success(probeService.start(request));
    }

    /**
     * 查询试活进度。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:probe')")
    @GetMapping("/probe/progress")
    public AjaxResult probeProgress()
    {
        return success(probeService.progress());
    }

    /**
     * 停止试活：已发出的请求自然结束，未开始的接口记入跳过。
     */
    @PreAuthorize("@ss.hasPermi('magic:requestConfig:probe')")
    @PostMapping("/probe/stop")
    public AjaxResult stopProbe()
    {
        boolean handled = probeService.stop();
        return handled ? success("停止请求已发送") : AjaxResult.error("当前没有正在执行的试活任务");
    }
}
