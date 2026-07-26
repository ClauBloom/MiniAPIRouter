package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledHealthCheckerTest {

    @Test
    void scheduledCheckProbesKeysConcurrently() throws Exception {
        CountDownLatch bothRequestsArrived = new CountDownLatch(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(serverExecutor);
            server.createContext("/v1/models", exchange -> {
                bothRequestsArrived.countDown();
                try {
                    bothRequestsArrived.await(2, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();

            ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
            when(mapper.selectList(any())).thenReturn(List.of(
                    key(1L, server.getAddress().getPort()),
                    key(2L, server.getAddress().getPort())));
            CryptoUtils cryptoUtils = mock(CryptoUtils.class);
            when(cryptoUtils.decrypt(anyString())).thenReturn("key");
            ScheduledHealthChecker checker = new ScheduledHealthChecker(
                    mapper, mock(ApiKeyConfigRepository.class), cryptoUtils);

            long started = System.nanoTime();
            checker.scheduledCheck();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(elapsed).isLessThan(Duration.ofMillis(1500));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void scheduledCheckBoundsConcurrentProbes() throws Exception {
        CountDownLatch releaseResponses = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (ExecutorService serverExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(serverExecutor);
            server.createContext("/v1/models", exchange -> {
                int current = active.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                try {
                    releaseResponses.await(2, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                    exchange.close();
                }
            });
            server.start();

            ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
            List<ApiKeyConfigDO> keys = LongStream.rangeClosed(1, 24)
                    .mapToObj(id -> key(id, server.getAddress().getPort()))
                    .toList();
            when(mapper.selectList(any())).thenReturn(keys);
            CryptoUtils cryptoUtils = mock(CryptoUtils.class);
            when(cryptoUtils.decrypt(anyString())).thenReturn("key");
            ScheduledHealthChecker checker = new ScheduledHealthChecker(
                    mapper, mock(ApiKeyConfigRepository.class), cryptoUtils);

            Thread checkThread = Thread.startVirtualThread(checker::scheduledCheck);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                while (peak.get() == 0 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                Thread.sleep(150);
                assertThat(peak.get()).isBetween(1, 16);
            } finally {
                releaseResponses.countDown();
                checkThread.join(5000);
            }
            assertThat(checkThread.isAlive()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private ApiKeyConfigDO key(Long id, int port) {
        ApiKeyConfigDO key = new ApiKeyConfigDO();
        key.setId(id);
        key.setApiKeyEnc("encrypted");
        key.setBaseUrl("http://127.0.0.1:" + port);
        key.setProtocol("openai");
        return key;
    }
}
