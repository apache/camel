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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import com.microsoft.azure.sdk.iot.device.ConnectionStatusChangeContext;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe pool of Azure IoT Hub {@link DeviceClient} instances, keyed by the resolved hub/device/module identity
 * derived from the connection string instead of only the raw device id.
 *
 * <p>
 * Clients are created lazily, reused across messages, and evicted after an idle timeout. Connection status changes are
 * monitored through the Azure SDK callback, and disconnected clients are retired automatically; any in-use client is
 * closed only when the final lease is released. Callers may also remove a client from the pool explicitly during
 * cleanup.
 * </p>
 */
public class DeviceClientPool {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceClientPool.class);

    @FunctionalInterface
    public interface DeviceClientFactory {
        DeviceClient create(String connectionString, IotHubClientProtocol protocol) throws Exception;
    }

    private static final class PooledClient {
        final DeviceClient client;
        volatile long lastUsed;
        final AtomicInteger inUse = new AtomicInteger();
        volatile boolean retired;
        final AtomicBoolean closed = new AtomicBoolean();

        PooledClient(DeviceClient client, long nowMillis) {
            this.client = client;
            this.lastUsed = nowMillis;
        }

        void touch(long nowMillis) {
            this.lastUsed = nowMillis;
        }

        void acquire() {
            inUse.incrementAndGet();
        }

        boolean release() {
            return inUse.decrementAndGet() == 0;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final DeviceClientPool pool;
        private final String poolKey;
        private final PooledClient pooled;
        private boolean closed;

        private Lease(DeviceClientPool pool, String poolKey, PooledClient pooled) {
            this.pool = pool;
            this.poolKey = poolKey;
            this.pooled = pooled;
        }

        public DeviceClient getClient() {
            return pooled.client;
        }

        public DeviceClient client() {
            return getClient();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (pooled.release()) {
                if (pooled.retired) {
                    pool.closeRetired(pooled);
                } else {
                    pool.touchAndMaybeEvict(poolKey);
                }
            }
        }
    }

    private final Duration idleTimeout;
    private final Duration evictionInterval;
    private final Map<String, PooledClient> pool = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<DeviceClient>> creationFutures = new ConcurrentHashMap<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private DeviceClientFactory deviceClientFactory = DeviceClient::new;
    private LongSupplier nowMillisSupplier = System::currentTimeMillis;
    private ScheduledExecutorService evictionExecutor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public DeviceClientPool(Duration idleTimeout, Duration evictionInterval) {
        this(idleTimeout, evictionInterval, null);
    }

    public DeviceClientPool(Duration idleTimeout, Duration evictionInterval, ScheduledExecutorService evictionExecutor) {
        this.idleTimeout = isPositive(idleTimeout) ? idleTimeout : Duration.ofMinutes(5);
        this.evictionInterval = isPositive(evictionInterval) ? evictionInterval : Duration.ofSeconds(30);
        this.evictionExecutor = evictionExecutor;
    }

    private static boolean isPositive(Duration d) {
        return d != null && !d.isZero() && !d.isNegative();
    }

    private ScheduledExecutorService getOrCreateEvictionExecutor() {
        ScheduledExecutorService executor = evictionExecutor;
        if (executor != null) {
            return executor;
        }
        synchronized (this) {
            executor = evictionExecutor;
            if (executor == null) {
                executor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "camel-azure-iothub-device-client-evictor");
                    t.setDaemon(true);
                    return t;
                });
                evictionExecutor = executor;
            }
            return executor;
        }
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ScheduledExecutorService executor = getOrCreateEvictionExecutor();
        executor.scheduleAtFixedRate(this::evictIdleClients,
                evictionInterval.toMillis(), evictionInterval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("DeviceClient eviction enabled. idleTimeout={}s, interval={}s",
                idleTimeout.getSeconds(), evictionInterval.getSeconds());
    }

    // package-private for test hooks only
    DeviceClient getOrCreate(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        if (Thread.currentThread().isInterrupted() || shuttingDown.get()) {
            throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
        }

        String poolKey = resolvePoolKey(deviceId, connectionString);
        final long now = nowMillisSupplier.getAsLong();

        PooledClient existing = pool.get(poolKey);
        if (existing != null) {
            existing.touch(now);
            return existing.client;
        }

        CompletableFuture<DeviceClient> createdFuture = new CompletableFuture<>();
        CompletableFuture<DeviceClient> inFlight = creationFutures.putIfAbsent(poolKey, createdFuture);
        if (inFlight == null) {
            try {
                PooledClient rechecked = pool.get(poolKey);
                if (rechecked != null) {
                    createdFuture.complete(rechecked.client);
                    return rechecked.client;
                }
                DeviceClient client = createClient(deviceId, connectionString, protocol);
                PooledClient pooled = new PooledClient(client, now);
                PooledClient previous = pool.putIfAbsent(poolKey, pooled);
                if (previous != null) {
                    client.close();
                    previous.touch(now);
                    createdFuture.complete(previous.client);
                    return previous.client;
                }
                if (shuttingDown.get()) {
                    pool.remove(poolKey, pooled);
                    closeQuietly(deviceId, client, "shutdown after creation");
                    throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
                }
                createdFuture.complete(client);
                return client;
            } catch (Throwable e) {
                if (!createdFuture.isDone()) {
                    createdFuture.completeExceptionally(e);
                }
                throw e;
            } finally {
                creationFutures.remove(poolKey, createdFuture);
                if (!createdFuture.isDone()) {
                    createdFuture.completeExceptionally(
                            new IllegalStateException("DeviceClient creation failed for device: " + deviceId));
                }
            }
        }

        try {
            return inFlight.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for DeviceClient creation for device: " + deviceId, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException("DeviceClient creation failed for device: " + deviceId, cause);
        }
    }

    public Lease acquire(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        if (Thread.currentThread().isInterrupted() || shuttingDown.get()) {
            throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
        }

        String poolKey = resolvePoolKey(deviceId, connectionString);
        final long now = nowMillisSupplier.getAsLong();

        for (;;) {
            AtomicReference<PooledClient> evicted = new AtomicReference<>();
            PooledClient existing = pool.computeIfPresent(poolKey, (key, pooled) -> {
                if (pooled.inUse.get() > 0) {
                    pooled.touch(now);
                    pooled.acquire();
                    return pooled;
                }
                if ((now - pooled.lastUsed) > idleTimeout.toMillis()) {
                    evicted.set(pooled);
                    return null;
                }
                pooled.touch(now);
                pooled.acquire();
                return pooled;
            });
            if (existing != null) {
                return new Lease(this, poolKey, existing);
            }
            if (evicted.get() != null) {
                closeQuietly(poolKey, evicted.get().client, "idle eviction");
                continue;
            }

            DeviceClient client = getOrCreate(deviceId, connectionString, protocol);
            AtomicReference<PooledClient> acquired = new AtomicReference<>();
            pool.computeIfPresent(poolKey, (key, pooled) -> {
                if (pooled.client != client) {
                    return pooled;
                }
                pooled.touch(now);
                pooled.acquire();
                acquired.set(pooled);
                return pooled;
            });
            if (acquired.get() != null) {
                return new Lease(this, poolKey, acquired.get());
            }
            // The pool entry may have been evicted or replaced between creation and the acquire step.
            // Loop back to retry instead of recursing to keep the stack depth constant.
            continue;
        }
    }

    public Lease lease(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        return acquire(deviceId, connectionString, protocol);
    }

    private boolean isCreationInProgress(String poolKey) {
        return creationFutures.containsKey(poolKey);
    }

    private boolean isCreationInProgressForDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }
        for (String poolKey : creationFutures.keySet()) {
            if (matchesDeviceId(poolKey, deviceId)) {
                return true;
            }
        }
        return false;
    }

    void remove(String deviceId, String connectionString) {
        removePoolEntry(resolvePoolKey(deviceId, connectionString));
    }

    void remove(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        if (isCreationInProgressForDevice(deviceId)) {
            LOG.debug("Skipping removal of in-flight DeviceClient for device {} while creation is still in progress", deviceId);
            return;
        }

        String directKey = deviceId;
        PooledClient pooled = pool.remove(directKey);
        if (pooled != null) {
            closeQuietly(deviceId, pooled.client, "explicit removal");
            return;
        }

        for (Map.Entry<String, PooledClient> entry : pool.entrySet()) {
            String key = entry.getKey();
            if (matchesDeviceId(key, deviceId)) {
                if (pool.remove(key, entry.getValue())) {
                    closeQuietly(deviceId, entry.getValue().client, "explicit removal");
                    return;
                }
            }
        }
    }

    private void removePoolEntry(String poolKey) {
        if (poolKey == null || poolKey.isBlank()) {
            return;
        }
        if (isCreationInProgress(poolKey)) {
            LOG.debug("Skipping removal of in-flight DeviceClient for device {} while creation is still in progress", poolKey);
            return;
        }
        PooledClient pooled = pool.remove(poolKey);
        if (pooled != null) {
            closeQuietly(poolKey, pooled.client, "explicit removal");
        }
    }

    private static boolean matchesDeviceId(String poolKey, String deviceId) {
        if (poolKey == null) {
            return false;
        }
        if (poolKey.equals(deviceId)) {
            return true;
        }
        String[] parts = poolKey.split(":");
        return parts.length >= 2 && parts[1].equals(deviceId);
    }

    private static String resolvePoolKey(String deviceId, String connectionString) {
        if (deviceId == null || deviceId.isBlank()) {
            return connectionString;
        }
        String[] items = connectionString == null ? new String[0] : connectionString.split(";");
        String hostName = null;
        String csDeviceId = null;
        String moduleId = null;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            switch (key) {
                case "HostName" -> hostName = value;
                case "DeviceId" -> csDeviceId = value;
                case "ModuleId" -> moduleId = value;
                default -> {
                    // ignore other connection-string fields
                }
            }
        }
        if (csDeviceId != null && !csDeviceId.equals(deviceId)) {
            throw new IllegalArgumentException(
                    "Device id '" + deviceId + "' does not match the connection string's DeviceId '" + csDeviceId + "'");
        }
        StringBuilder identity = new StringBuilder();
        if (hostName != null && !hostName.isBlank()) {
            identity.append(hostName);
        }
        if (csDeviceId != null && !csDeviceId.isBlank()) {
            if (!identity.isEmpty()) {
                identity.append(':');
            }
            identity.append(csDeviceId);
        }
        if (moduleId != null && !moduleId.isBlank()) {
            if (!identity.isEmpty()) {
                identity.append(':');
            }
            identity.append(moduleId);
        }
        return !identity.isEmpty() ? identity.toString() : deviceId;
    }

    private DeviceClient createClient(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        try {
            DeviceClient client = deviceClientFactory.create(connectionString, protocol);
            if (shuttingDown.get()) {
                closeQuietly(deviceId, client, "shutdown while creating");
                throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
            }
            client.open(true);
            if (shuttingDown.get()) {
                closeQuietly(deviceId, client, "shutdown after open");
                throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
            }
            try {
                // Avoid duplicate desired-property subscriptions: subscribe only if not already subscribed.
                // The Azure DeviceClient does not expose a way to check, so we rely on only subscribing once per client creation.
                client.subscribeToDesiredProperties((twinUpdate, ctx) -> {
                }, null);
                client.setConnectionStatusChangeCallback(context -> {
                    if (context == null) {
                        return;
                    }
                    evictDisconnectedClient(deviceId, connectionString, client, context);
                }, null);
            } catch (Exception e) {
                closeQuietly(deviceId, client, "subscription setup");
                throw e;
            }
            LOG.debug("Created new DeviceClient for device: {}", deviceId);
            return client;
        } catch (Exception e) {
            if (isShutdownInterruption(e)) {
                Thread.currentThread().interrupt();
                LOG.warn("DeviceClient creation interrupted due to shutdown for device: {}", deviceId);
                throw new IllegalStateException("DeviceClient creation interrupted for device: " + deviceId, e);
            }
            throw new RuntimeException("DeviceClient creation failed for device: " + deviceId, e);
        }
    }

    private void evictDisconnectedClient(
            String deviceId, String connectionString, DeviceClient client,
            ConnectionStatusChangeContext context) {
        if (context == null || shuttingDown.get()) {
            return;
        }
        if (context.getNewStatus() != IotHubConnectionStatus.DISCONNECTED) {
            return;
        }
        if (context.getNewStatusReason() == IotHubConnectionStatusChangeReason.CLIENT_CLOSE) {
            return;
        }

        String poolKey = resolvePoolKey(deviceId, connectionString);

        AtomicReference<PooledClient> removed = new AtomicReference<>();
        pool.computeIfPresent(poolKey, (key, pooled) -> {
            if (pooled.client != client) {
                return pooled;
            }
            pooled.retired = true;
            removed.set(pooled);
            return null;
        });

        PooledClient retired = removed.get();
        if (retired != null) {
            LOG.warn("Evicted DeviceClient for device {} after disconnect. previousStatus={}, reason={}, cause={}",
                    deviceId, context.getPreviousStatus(), context.getNewStatusReason(), context.getCause());
            if (retired.inUse.get() == 0) {
                closeRetired(retired);
            }
        }
    }

    private void touchAndMaybeEvict(String poolKey) {
        long now = nowMillisSupplier.getAsLong();
        AtomicReference<PooledClient> retired = new AtomicReference<>();
        pool.computeIfPresent(poolKey, (key, current) -> {
            if (current.inUse.get() > 0) {
                current.touch(now);
                return current;
            }
            if ((now - current.lastUsed) > idleTimeout.toMillis()) {
                current.retired = true;
                retired.set(current);
                return null;
            }
            current.touch(now);
            return current;
        });
        PooledClient expired = retired.get();
        if (expired != null) {
            closeRetired(expired);
        }
    }

    private void closeRetired(PooledClient pooled) {
        if (pooled.closed.compareAndSet(false, true)) {
            ScheduledExecutorService executor = getOrCreateEvictionExecutor();
            executor.execute(() -> closeQuietly("retired", pooled.client, "retired eviction"));
        }
    }

    private void evictIdleClients() {
        if (shuttingDown.get()) {
            return;
        }
        long now = nowMillisSupplier.getAsLong();
        for (Map.Entry<String, PooledClient> entry : pool.entrySet()) {
            String key = entry.getKey();
            AtomicReference<PooledClient> retired = new AtomicReference<>();
            pool.computeIfPresent(key, (k, current) -> {
                if (current.inUse.get() > 0) {
                    return current;
                }
                if ((now - current.lastUsed) > idleTimeout.toMillis()) {
                    current.retired = true;
                    retired.set(current);
                    return null;
                }
                return current;
            });
            PooledClient expired = retired.get();
            if (expired != null) {
                closeRetired(expired);
            }
        }
    }

    public int getActiveClientCount() {
        return pool.size();
    }

    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        if (evictionExecutor != null) {
            evictionExecutor.shutdownNow();
        }
        creationFutures.clear();
        for (Map.Entry<String, PooledClient> entry : pool.entrySet()) {
            closeQuietly(entry.getKey(), entry.getValue() == null ? null : entry.getValue().client, "shutdown");
        }
        pool.clear();
    }

    private void closeQuietly(String deviceId, DeviceClient client, String action) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            LOG.warn("Error closing DeviceClient during {} for device: {}", action, deviceId, e);
        }
    }

    private boolean isShutdownInterruption(Throwable e) {
        while (e != null) {
            if (e instanceof InterruptedException) {
                return true;
            }
            e = e.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    // ---- test hooks ----

    public void setDeviceClientFactory(DeviceClientFactory factory) {
        this.deviceClientFactory = factory;
    }

    public void setNowMillisSupplier(LongSupplier supplier) {
        this.nowMillisSupplier = supplier;
    }
}
