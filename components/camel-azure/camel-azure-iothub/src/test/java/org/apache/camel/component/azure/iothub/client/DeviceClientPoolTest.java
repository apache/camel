/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.iothub.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.microsoft.azure.sdk.iot.device.ConnectionStatusChangeContext;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeviceClientPoolTest {

    private static final String CS = "HostName=h;DeviceId=d;SharedAccessKey=k";

    @Test
    void testLeaseInUseIsReusedAndReleased() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        try (DeviceClientPool.Lease first = pool.lease("d", CS, IotHubClientProtocol.MQTT)) {
            assertSame(client, first.getClient());
            assertEquals(1, pool.getActiveClientCount());

            try (DeviceClientPool.Lease second = pool.lease("d", CS, IotHubClientProtocol.MQTT)) {
                assertSame(client, second.getClient());
                assertEquals(1, pool.getActiveClientCount());
            }
        }

        assertEquals(1, pool.getActiveClientCount());
    }

    @Test
    void testConcurrentFirstAcquireCreatesSingleClient() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicInteger created = new AtomicInteger();
        CountDownLatch factoryStarted = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            created.incrementAndGet();
            factoryStarted.countDown();
            try {
                assertTrue(releaseFactory.await(5, TimeUnit.SECONDS), "factory should be released after both tasks start");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return client;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<DeviceClientPool.Lease> leases = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                DeviceClientPool.Lease lease = pool.lease("d", CS, IotHubClientProtocol.MQTT);
                synchronized (leases) {
                    leases.add(lease);
                }
                return null;
            }));
        }

        assertTrue(factoryStarted.await(5, TimeUnit.SECONDS), "one thread should open the first client");
        releaseFactory.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "worker tasks did not finish");
        for (Future<?> future : futures) {
            future.get();
        }

        assertEquals(1, created.get(), "only one client should be created for first acquire");
        assertEquals(1, pool.getActiveClientCount());
        for (DeviceClientPool.Lease lease : leases) {
            lease.close();
        }
    }

    @Test
    void testGetOrCreateCreatesOnceAndReuses() throws Exception {
        AtomicInteger created = new AtomicInteger();
        DeviceClient client = mock(DeviceClient.class);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            created.incrementAndGet();
            return client;
        });

        DeviceClient first = pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        DeviceClient second = pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);

        assertSame(client, first);
        assertSame(first, second);
        assertEquals(1, created.get(), "client should be created once and reused");
        assertEquals(1, pool.getActiveClientCount());
        verify(client, times(1)).open(true);
    }

    @Test
    void testRejectsMismatchedDeviceId() {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> mock(DeviceClient.class));

        assertThrows(IllegalArgumentException.class, () -> pool.getOrCreate("dev1", CS, IotHubClientProtocol.MQTT));
    }

    @Test
    void testRemoveClosesClient() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        pool.remove("d");

        assertEquals(0, pool.getActiveClientCount());
        verify(client, times(1)).close();
    }

    @Test
    void testShutdownClosesAllAndBlocksCreation() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        pool.shutdown();

        assertEquals(0, pool.getActiveClientCount());
        verify(client, times(1)).close();
        assertThrows(IllegalStateException.class, () -> pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT));
    }

    @Test
    void testIdleClientsAreEvicted() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicLong clock = new AtomicLong(0);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMillis(10), Duration.ofMillis(20));
        pool.setDeviceClientFactory((cs, protocol) -> client);
        pool.setNowMillisSupplier(clock::get);
        pool.start();
        try {
            pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
            assertEquals(1, pool.getActiveClientCount());

            clock.set(10_000);
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> pool.getActiveClientCount() == 0);
            assertEquals(0, pool.getActiveClientCount(), "idle client should have been evicted");
            verify(client, Mockito.atLeastOnce()).close();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void testCreationFailurePropagates() {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            throw new IllegalArgumentException("bad connection string");
        });

        assertThrows(RuntimeException.class, () -> pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT));
        assertEquals(0, pool.getActiveClientCount());
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, CompletableFuture<DeviceClient>> accessCreationFutures(DeviceClientPool pool)
            throws Exception {
        java.lang.reflect.Field field = DeviceClientPool.class.getDeclaredField("creationFutures");
        field.setAccessible(true);
        return (java.util.Map<String, CompletableFuture<DeviceClient>>) field.get(pool);
    }

    @Test
    void testCreationErrorCompletesFutureForWaiters() throws Exception {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        CompletableFuture<DeviceClient> inflight = new CompletableFuture<>();
        accessCreationFutures(pool).put("h:d", inflight);

        CountDownLatch waiterStarted = new CountDownLatch(1);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            try {
                pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
            } catch (Throwable t) {
                waiterFailure.set(t);
            }
        }, "device-client-pool-waiter");

        waiter.start();
        assertTrue(waiterStarted.await(5, TimeUnit.SECONDS), "waiter should start before the in-flight future is completed");

        inflight.completeExceptionally(new NoClassDefFoundError("missing Azure SDK transitive"));
        waiter.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(waiter.isAlive(), "waiter should exit once the in-flight future completes exceptionally");
        assertTrue(waiterFailure.get() instanceof NoClassDefFoundError, "waiter should rethrow the original Error");
    }

    @Test
    void testDisconnectedCallbackEvictsClientImmediately() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicReference<IotHubConnectionStatusChangeCallback> callbackRef = new AtomicReference<>();

        doAnswer(invocation -> {
            callbackRef.set(invocation.getArgument(0));
            return null;
        }).when(client).setConnectionStatusChangeCallback(any(), any());

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        assertEquals(1, pool.getActiveClientCount());

        IotHubConnectionStatusChangeCallback callback = callbackRef.get();
        ConnectionStatusChangeContext context = mock(ConnectionStatusChangeContext.class);
        Mockito.when(context.getNewStatus()).thenReturn(IotHubConnectionStatus.DISCONNECTED);
        Mockito.when(context.getPreviousStatus()).thenReturn(IotHubConnectionStatus.CONNECTED);
        Mockito.when(context.getNewStatusReason()).thenReturn(IotHubConnectionStatusChangeReason.COMMUNICATION_ERROR);

        callback.onStatusChanged(context);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> pool.getActiveClientCount() == 0);
        assertEquals(0, pool.getActiveClientCount(), "client should be evicted immediately on DISCONNECTED");
        verify(client, Mockito.atLeastOnce()).close();
    }

    @Test
    void testDisconnectedCallbackDoesNotEvictInUseClient() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicReference<IotHubConnectionStatusChangeCallback> callbackRef = new AtomicReference<>();

        doAnswer(invocation -> {
            callbackRef.set(invocation.getArgument(0));
            return null;
        }).when(client).setConnectionStatusChangeCallback(any(), any());

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        DeviceClientPool.Lease lease = pool.lease("d", CS, IotHubClientProtocol.MQTT);
        assertEquals(1, pool.getActiveClientCount());

        IotHubConnectionStatusChangeCallback callback = callbackRef.get();
        ConnectionStatusChangeContext context = mock(ConnectionStatusChangeContext.class);
        Mockito.when(context.getNewStatus()).thenReturn(IotHubConnectionStatus.DISCONNECTED);
        Mockito.when(context.getPreviousStatus()).thenReturn(IotHubConnectionStatus.CONNECTED);
        Mockito.when(context.getNewStatusReason()).thenReturn(IotHubConnectionStatusChangeReason.COMMUNICATION_ERROR);

        callback.onStatusChanged(context);

        assertEquals(0, pool.getActiveClientCount(), "retired client should be removed immediately on terminal DISCONNECTED");
        verify(client, Mockito.never()).close();

        lease.close();
        assertEquals(0, pool.getActiveClientCount(), "retired client should close only when the final lease is released");
        verify(client, Mockito.times(1)).close();
    }

    @Test
    void testAcquireRetriesWhenEntryIsEvictedBetweenCreateAndAcquire() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicBoolean removedAfterCreation = new AtomicBoolean(false);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30)) {
            @Override
            DeviceClient getOrCreate(String deviceId, String connectionString, IotHubClientProtocol protocol) {
                DeviceClient created = super.getOrCreate(deviceId, connectionString, protocol);
                if (removedAfterCreation.compareAndSet(false, true)) {
                    remove(deviceId, connectionString);
                }
                return created;
            }
        };
        pool.setDeviceClientFactory((cs, protocol) -> client);

        try (DeviceClientPool.Lease lease = pool.acquire("d", CS, IotHubClientProtocol.MQTT)) {
            assertSame(client, lease.getClient());
            assertEquals(1, pool.getActiveClientCount());
        }

        assertEquals(1, pool.getActiveClientCount());
    }

    @Test
    void testShutdownDuringClientOpenDoesNotLeakClient() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        CountDownLatch openStarted = new CountDownLatch(1);
        CountDownLatch allowOpenToFinish = new CountDownLatch(1);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            openStarted.countDown();
            assertTrue(allowOpenToFinish.await(5, TimeUnit.SECONDS),
                    "open should be allowed to finish before shutdown proceeds");
            return client;
        });

        Thread creator = new Thread(() -> {
            try {
                pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
            } catch (RuntimeException e) {
                assertTrue(e.getCause() instanceof IllegalStateException,
                        "shutdown should fail creation with an IllegalStateException as the cause");
            }
        }, "device-client-pool-creator");
        creator.start();
        assertTrue(openStarted.await(5, TimeUnit.SECONDS), "creator should start creating the client");

        pool.shutdown();
        allowOpenToFinish.countDown();
        creator.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(0, pool.getActiveClientCount());
        verify(client, times(1)).close();
    }

    @Test
    void testStartIsIdempotent() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);
        pool.start();
        pool.start();

        try {
            pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
            assertEquals(1, pool.getActiveClientCount());
        } finally {
            pool.shutdown();
        }
    }

}
