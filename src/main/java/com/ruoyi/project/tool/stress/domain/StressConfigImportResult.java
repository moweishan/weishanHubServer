package com.ruoyi.project.tool.stress.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求配置 JSON 导入结果。
 *
 * @author weishanHub
 */
public class StressConfigImportResult
{
    /** 文件中的配置条数 */
    private int total;

    /** 实际写入条数 */
    private int inserted;

    /** 因 URL 重复被跳过的条数 */
    private int skipped;

    /** 校验失败条数 */
    private int failed;

    /** 失败明细，形如“第 3 条 接口A：请求URL仅支持HTTP(S)协议” */
    private List<String> errors = new ArrayList<String>();

    public int getTotal()
    {
        return total;
    }

    public void setTotal(int total)
    {
        this.total = total;
    }

    public int getInserted()
    {
        return inserted;
    }

    public void setInserted(int inserted)
    {
        this.inserted = inserted;
    }

    public int getSkipped()
    {
        return skipped;
    }

    public void setSkipped(int skipped)
    {
        this.skipped = skipped;
    }

    public int getFailed()
    {
        return failed;
    }

    public void setFailed(int failed)
    {
        this.failed = failed;
    }

    public List<String> getErrors()
    {
        return errors;
    }

    public void setErrors(List<String> errors)
    {
        this.errors = errors;
    }

    public void addError(String message)
    {
        if (errors.size() < 50)
        {
            errors.add(message);
        }
    }
}
