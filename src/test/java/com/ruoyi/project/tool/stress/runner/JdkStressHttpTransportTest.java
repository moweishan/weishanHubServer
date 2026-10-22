package com.ruoyi.project.tool.stress.runner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 使用本机临时端口的裸 HTTP 服务测试传输层，不访问真实外部服务。
 */
class JdkStressHttpTransportTest
{
    private ServerSocket serverSocket;

    private Thread serverThread;

    private volatile CapturedRequest captured;

    private final JdkStressHttpTransport transport = new JdkStressHttpTransport();

    private final StressCancellation noopCancellation = new StressCancellation()
    {
        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public void onCancel(Runnable action)
        {
            // 测试中不取消
        }

        @Override
        public void removeCancelAction(Runnable action)
        {
            // 测试中无需移除
        }
    };

    @AfterEach
    void tearDown() throws Exception
    {
        stopServer();
    }

    @Test
    void sendsBodyHeadersAndQueryAndReadsResponse() throws Exception
    {
        int port = startServer(200, "ok".getBytes(StandardCharsets.UTF_8));
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Test", "yes");
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        StressHttpRequest request = new StressHttpRequest(
                "http://127.0.0.1:" + port + "/check?phone=%2B8613800138000&q=a+b",
                "POST", headers, body, 3000);
        StressHttpResult result = transport.execute(request, noopCancellation);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode().intValue());
        assertEquals("ok", result.getResponseText());
        assertFalse(result.isTruncated());
        assertEquals("/check?phone=%2B8613800138000&q=a+b", captured.path);
        assertEquals("yes", captured.headers.get("x-test"));
        assertArrayEquals(body, captured.body);
    }

    @Test
    void statusCode200To299IsSuccessAnd400IsFailure() throws Exception
    {
        int port = startServer(299, "ok".getBytes(StandardCharsets.UTF_8));
        StressHttpResult accepted = transport.execute(getRequest(port), noopCancellation);
        assertTrue(accepted.isSuccess());
        assertEquals(299, accepted.getStatusCode().intValue());
        stopServer();

        port = startServer(400, "bad".getBytes(StandardCharsets.UTF_8));
        StressHttpResult rejected = transport.execute(getRequest(port), noopCancellation);
        assertFalse(rejected.isSuccess());
        assertEquals(400, rejected.getStatusCode().intValue());
        assertEquals("bad", rejected.getResponseText());
    }

    @Test
    void truncatesLongResponseAndMarksIt() throws Exception
    {
        byte[] large = new byte[JdkStressHttpTransport.MAX_RESPONSE_BYTES + 4096];
        Arrays.fill(large, (byte) 'a');
        int port = startServer(200, large);

        StressHttpResult result = transport.execute(getRequest(port), noopCancellation);

        assertTrue(result.isSuccess());
        assertTrue(result.isTruncated());
        assertEquals(JdkStressHttpTransport.MAX_RESPONSE_BYTES, result.getResponseText().length());
    }

    private StressHttpRequest getRequest(int port)
    {
        return new StressHttpRequest("http://127.0.0.1:" + port + "/check", "GET",
                Collections.<String, String>emptyMap(), null, 3000);
    }

    private int startServer(int status, byte[] responseBody) throws IOException
    {
        serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        final int port = serverSocket.getLocalPort();
        serverThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try (Socket socket = serverSocket.accept())
                {
                    socket.setSoTimeout(5000);
                    captured = readRequest(socket.getInputStream());
                    String header = "HTTP/1.1 " + status + " Test\r\n"
                            + "Content-Length: " + responseBody.length + "\r\n"
                            + "Content-Type: text/plain; charset=UTF-8\r\n"
                            + "Connection: close\r\n\r\n";
                    OutputStream output = socket.getOutputStream();
                    output.write(header.getBytes(StandardCharsets.ISO_8859_1));
                    output.write(responseBody);
                    output.flush();
                }
                catch (Exception e)
                {
                    // 客户端主动断开（例如响应截断后提前关闭）属于测试预期，不在此失败
                }
            }
        }, "test-http-server");
        serverThread.setDaemon(true);
        serverThread.start();
        return port;
    }

    private CapturedRequest readRequest(InputStream inputStream) throws IOException
    {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int matched = 0;
        int value;
        while ((value = inputStream.read()) != -1)
        {
            headerBytes.write(value);
            if ((matched == 0 || matched == 2) && value == '\r')
            {
                matched++;
            }
            else if ((matched == 1 || matched == 3) && value == '\n')
            {
                matched++;
            }
            else
            {
                matched = 0;
            }
            if (matched == 4)
            {
                break;
            }
        }

        String headerText = new String(headerBytes.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\r\n");
        CapturedRequest request = new CapturedRequest();
        request.path = lines.length == 0 ? "" : lines[0].split(" ")[1];
        request.headers = new LinkedHashMap<String, String>();
        int contentLength = 0;
        for (int i = 1; i < lines.length; i++)
        {
            int colon = lines[i].indexOf(':');
            if (colon > 0)
            {
                String name = lines[i].substring(0, colon).trim().toLowerCase();
                String headerValue = lines[i].substring(colon + 1).trim();
                request.headers.put(name, headerValue);
                if ("content-length".equals(name))
                {
                    contentLength = Integer.parseInt(headerValue);
                }
            }
        }

        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < contentLength)
        {
            int read = inputStream.read(body, offset, contentLength - offset);
            if (read == -1)
            {
                break;
            }
            offset += read;
        }
        request.body = Arrays.copyOf(body, offset);
        return request;
    }

    private void stopServer() throws Exception
    {
        if (serverSocket != null)
        {
            serverSocket.close();
            serverSocket = null;
        }
        if (serverThread != null)
        {
            serverThread.join(2000);
            serverThread = null;
        }
    }

    private static final class CapturedRequest
    {
        private String path;

        private Map<String, String> headers;

        private byte[] body;
    }
}
