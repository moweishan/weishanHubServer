package com.ruoyi.project.tool.stress.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.tool.stress.domain.StressRequestConfigSnapshot;
import com.ruoyi.project.tool.stress.domain.StressTask;
import com.ruoyi.project.tool.stress.domain.StressTaskStatus;
import com.ruoyi.project.tool.stress.mapper.StressTaskMapper;
import com.ruoyi.project.tool.stress.service.StressSnapshotCodec;
import com.ruoyi.project.tool.stress.service.StressTaskResponseService;
import com.ruoyi.project.tool.stress.service.StressTemplateRenderer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 真实本机慢 HTTP 回归：慢响应读取不阻塞截止定时器，手动停止立即返回，
 * 且停止后迟到的完整响应不能记为成功。
 */
class StressTaskSlowHttpTest
{
    private StressTaskMapper taskMapper;

    private StressTaskResponseService responseService;

    private StressSnapshotCodec codec;

    private ThreadPoolExecutor workers;

    private ThreadPoolExecutor cancelExecutor;

    private RecordingTimer timer;

    private StressTaskExecutionService service;

    private final List<RawHttpServer> servers = new ArrayList<RawHttpServer>();

    private final Map<Long, String> finishedStatuses = new ConcurrentHashMap<Long, String>();

    private volatile CountDownLatch finishLatch;

    @BeforeEach
    void setUp()
    {
        taskMapper = mock(StressTaskMapper.class);
        responseService = mock(StressTaskResponseService.class);
        codec = new StressSnapshotCodec(new ObjectMapper());
        when(taskMapper.finishTask(anyLong(), anyString(), any(Date.class))).thenAnswer(invocation -> {
            Long taskId = invocation.getArgument(0);
            String status = invocation.getArgument(1);
            finishedStatuses.put(taskId, status);
            CountDownLatch latch = finishLatch;
            if (latch != null)
            {
                latch.countDown();
            }
            return 1;
        });

        workers = new ThreadPoolExecutor(0, 8, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                daemonFactory("slow-http-worker"));
        cancelExecutor = new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                daemonFactory("slow-http-cancel"));
        timer = new RecordingTimer();
        service = new StressTaskExecutionService(taskMapper, responseService,
                new StressTemplateRenderer(new ObjectMapper()), new JdkStressHttpTransport(), codec,
                new SystemStressClock(), new StressSchedule(), workers, timer, cancelExecutor);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        for (RawHttpServer server : servers)
        {
            server.close();
        }
        if (service != null)
        {
            service.shutdown();
        }
        if (workers != null)
        {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void deadlineOfSlowBodyDoesNotBlockOtherTaskDeadline() throws Exception
    {
        RawHttpServer slow = startServer(RawHttpServer.Mode.DRIP_BODY, 0L);
        RawHttpServer fast = startServer(RawHttpServer.Mode.FAST, 0L);
        finishLatch = new CountDownLatch(2);

        StressTask slowTask = newTask(101L, "13800138001", slow.url("/slow"), 1000L);
        StressTask fastTask = newTask(102L, "13800138002", fast.url("/fast"), 1600L);

        long testStart = System.nanoTime();
        service.start(slowTask);
        service.start(fastTask);

        assertTrue(slow.accepted.await(2, TimeUnit.SECONDS));
        assertTrue(fast.accepted.await(2, TimeUnit.SECONDS));
        assertTrue(finishLatch.await(8, TimeUnit.SECONDS));
        // 截止可由截止定时器触发，也可由到期后自行退出的工作线程触发；两者都不经过慢 disconnect，
        // 因此用整体耗时判断：慢响应体（约 2 秒）不应拖住快任务的 1.6 秒截止。
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - testStart);
        assertTrue(elapsedMillis < 4000L, "慢 disconnect 不应阻塞任务截止，实际=" + elapsedMillis + "ms");
        assertEquals(StressTaskStatus.COMPLETED, finishedStatuses.get(101L));
        assertEquals(StressTaskStatus.COMPLETED, finishedStatuses.get(102L));
    }

    @Test
    void manualStopReturnsImmediatelyAndLateResponseIsFailure() throws Exception
    {
        RawHttpServer delayed = startServer(RawHttpServer.Mode.DELAYED_FULL, 400L);
        finishLatch = new CountDownLatch(1);
        StressTask task = newTask(201L, "13800138003", delayed.url("/delayed"), 3000L);
        service.start(task);
        assertTrue(delayed.accepted.await(2, TimeUnit.SECONDS));
        Thread.sleep(100L);

        long stopStart = System.nanoTime();
        assertTrue(service.stopTask(201L));
        long stopElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStart);

        assertTrue(stopElapsedMillis < 500L, "停止接口不应等待慢 disconnect，实际=" + stopElapsedMillis + "ms");
        assertTrue(finishLatch.await(6, TimeUnit.SECONDS));
        assertEquals(StressTaskStatus.STOPPED, finishedStatuses.get(201L));

        ArgumentCaptor<StressHttpResult> captor = ArgumentCaptor.forClass(StressHttpResult.class);
        verify(responseService, timeout(2000)).recordResult(eq(201L),
                any(StressRequestConfigSnapshot.class), captor.capture(), anyLong());
        assertFalse(captor.getValue().isSuccess(), "停止后迟到的响应不能记为成功");
    }

    @Test
    void manualStopOnStalledBodyReturnsImmediately() throws Exception
    {
        RawHttpServer stalled = startServer(RawHttpServer.Mode.SLOW_BODY, 0L);
        finishLatch = new CountDownLatch(1);
        StressTask task = newTask(301L, "13800138004", stalled.url("/stalled"), 5000L);
        service.start(task);
        assertTrue(stalled.accepted.await(2, TimeUnit.SECONDS));
        Thread.sleep(100L);

        long stopStart = System.nanoTime();
        assertTrue(service.stopTask(301L));
        long stopElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStart);

        assertTrue(stopElapsedMillis < 500L, "停止接口不应等待慢 disconnect，实际=" + stopElapsedMillis + "ms");
        assertTrue(finishLatch.await(7, TimeUnit.SECONDS));
        assertEquals(StressTaskStatus.STOPPED, finishedStatuses.get(301L));
    }

    private RawHttpServer startServer(RawHttpServer.Mode mode, long delayMs) throws Exception
    {
        RawHttpServer server = new RawHttpServer(mode, delayMs);
        server.start();
        servers.add(server);
        return server;
    }

    private StressTask newTask(long taskId, String phone, String url, long durationMs)
    {
        long now = System.currentTimeMillis();
        StressRequestConfigSnapshot snapshot = new StressRequestConfigSnapshot();
        snapshot.setConfigId(taskId * 10);
        snapshot.setConfigName("慢接口");
        snapshot.setRequestUrl(url);
        snapshot.setHttpMethod("GET");
        snapshot.setHeadersJson("{}");
        snapshot.setQueryParamsJson("{}");
        snapshot.setBodyType("RAW");
        snapshot.setTimeoutMs(10000);

        StressTask task = new StressTask();
        task.setTaskId(taskId);
        task.setPhone(phone);
        task.setDurationSeconds((int) (durationMs / 1000L));
        task.setStatus(StressTaskStatus.RUNNING);
        task.setRunningPhone(phone);
        task.setStartTime(new Date(now));
        task.setPlanEndTime(new Date(now + durationMs));
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setOwnerId(1L);
        task.setSnapshotJson(codec.toJson(Collections.singletonList(snapshot)));
        return task;
    }

    private static ThreadFactory daemonFactory(final String prefix)
    {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class RecordingTimer extends ScheduledThreadPoolExecutor
    {
        private final List<Long> starts = Collections.synchronizedList(new ArrayList<Long>());

        private RecordingTimer()
        {
            super(1);
            setRemoveOnCancelPolicy(true);
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable runnable)
        {
            starts.add(System.nanoTime());
            super.beforeExecute(thread, runnable);
        }
    }

    private static final class RawHttpServer
    {
        private enum Mode
        {
            FAST, DRIP_BODY, SLOW_BODY, DELAYED_FULL
        }

        private final Mode mode;

        private final long delayMs;

        private final ServerSocket serverSocket;

        private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<Socket>());

        private final CountDownLatch accepted = new CountDownLatch(1);

        private volatile boolean closed;

        private RawHttpServer(Mode mode, long delayMs) throws Exception
        {
            this.mode = mode;
            this.delayMs = delayMs;
            this.serverSocket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        }

        private void start()
        {
            Thread acceptThread = new Thread(() -> {
                while (!closed)
                {
                    try
                    {
                        Socket socket = serverSocket.accept();
                        sockets.add(socket);
                        Thread handler = new Thread(() -> handle(socket), "slow-http-handler");
                        handler.setDaemon(true);
                        handler.start();
                    }
                    catch (Exception e)
                    {
                        return;
                    }
                }
            }, "slow-http-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void handle(Socket socket)
        {
            accepted.countDown();
            try (Socket ignored = socket)
            {
                OutputStream output = socket.getOutputStream();
                if (mode == Mode.DELAYED_FULL && delayMs > 0)
                {
                    Thread.sleep(delayMs);
                }
                if (mode == Mode.DRIP_BODY)
                {
                    // 每 100ms 送一个字节，避免读超时先于截止定时器触发；
                    // 保持连接不立即结束，模拟真实慢响应体。
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 20\r\n"
                            + "Content-Type: text/plain\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    output.flush();
                    for (int i = 0; i < 20; i++)
                    {
                        output.write('a');
                        output.flush();
                        Thread.sleep(100L);
                    }
                    Thread.sleep(3000L);
                }
                else if (mode == Mode.SLOW_BODY)
                {
                    // 与慢连接探针一致：声明 100 字节但只发送 1 字节后保持连接
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n"
                            + "Content-Type: text/plain\r\nConnection: close\r\n\r\na")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    output.flush();
                    Thread.sleep(4000L);
                }
                else
                {
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                            + "Content-Type: text/plain\r\nConnection: close\r\n\r\nok")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    output.flush();
                }
            }
            catch (Exception e)
            {
                // 客户端 disconnect/关闭属于慢连接测试的预期路径
            }
        }

        private String url(String path)
        {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + path;
        }

        private void close()
        {
            closed = true;
            try
            {
                serverSocket.close();
            }
            catch (Exception ignored)
            {
            }
            synchronized (sockets)
            {
                for (Socket socket : sockets)
                {
                    try
                    {
                        socket.close();
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }
        }
    }
}
