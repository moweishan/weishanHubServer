package com.ruoyi.project.tool.stress.runner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 基于 JDK HttpURLConnection 的 HTTP 传输实现。
 *
 * <p>使用 JDK 默认 TLS 校验（证书链与主机名），不覆盖 SSLSocketFactory 或 HostnameVerifier。
 * 响应最多读取 64KB，超出部分丢弃并在结果上标记截断，避免长响应无限占用内存。</p>
 *
 * @author weishanHub
 */
@Component
public class JdkStressHttpTransport implements StressHttpTransport
{
    /** 单次响应最多保留的字节数 */
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;

    @Override
    public StressHttpResult execute(StressHttpRequest request, StressCancellation cancellation) throws Exception
    {
        if (cancellation.isCancelled())
        {
            throw new IOException("请求已取消");
        }

        URL url;
        try
        {
            url = new URL(request.getUrl());
        }
        catch (Exception e)
        {
            throw new IOException("请求URL无效：" + e.getMessage(), e);
        }
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol))
        {
            throw new IOException("仅支持HTTP(S)协议");
        }

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        final Runnable cancelAction = new Runnable()
        {
            @Override
            public void run()
            {
                // 断开连接以尝试解除阻塞中的 connect/read；已发送到服务端的请求无法撤回
                connection.disconnect();
            }
        };
        cancellation.onCancel(cancelAction);

        try
        {
            if (cancellation.isCancelled())
            {
                throw new IOException("请求已取消");
            }
            connection.setConnectTimeout(request.getTimeoutMs());
            connection.setReadTimeout(request.getTimeoutMs());
            connection.setRequestMethod(request.getMethod());
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);

            for (Map.Entry<String, String> entry : request.getHeaders().entrySet())
            {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            byte[] body = request.getBody();
            if (body != null)
            {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream output = connection.getOutputStream();
                try
                {
                    output.write(body);
                    output.flush();
                }
                finally
                {
                    output.close();
                }
            }

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : null;
            if (inputStream == null && statusCode < 400)
            {
                try
                {
                    inputStream = connection.getInputStream();
                }
                catch (IOException e)
                {
                    // 部分服务端在 3xx 时没有可读流；保留已获得的状态码，响应体按空处理
                    inputStream = connection.getErrorStream();
                }
            }

            ReadOutcome outcome = readBounded(inputStream);
            return StressHttpResult.response(statusCode, outcome.text, outcome.truncated);
        }
        finally
        {
            cancellation.removeCancelAction(cancelAction);
            connection.disconnect();
        }
    }

    private ReadOutcome readBounded(InputStream inputStream) throws IOException
    {
        if (inputStream == null)
        {
            return new ReadOutcome("", false);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        boolean truncated = false;
        try
        {
            int length;
            while ((length = inputStream.read(buffer)) != -1)
            {
                if (total + length > MAX_RESPONSE_BYTES)
                {
                    int keep = MAX_RESPONSE_BYTES - total;
                    if (keep > 0)
                    {
                        output.write(buffer, 0, keep);
                    }
                    truncated = true;
                    break;
                }
                output.write(buffer, 0, length);
                total += length;
            }
        }
        finally
        {
            inputStream.close();
        }
        return new ReadOutcome(new String(output.toByteArray(), StandardCharsets.UTF_8), truncated);
    }

    private static final class ReadOutcome
    {
        private final String text;

        private final boolean truncated;

        private ReadOutcome(String text, boolean truncated)
        {
            this.text = text;
            this.truncated = truncated;
        }
    }
}
