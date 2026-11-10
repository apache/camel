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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import com.microsoft.azure.sdk.iot.device.ConnectionStatusChangeContext;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe pool of Azure IoT Hub {@link DeviceClient} instances, keyed by the device identity in the connection
 * string instead of only the raw device id.
 *
 * <p>
 * Clients are created lazily, reused across messages, and evicted after an idle timeout. The Azure device SDK does not
 * expose connection state, so broken clients are removed by the caller on send failure (see the send operations).
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
            return pooled.client;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (pooled.release()) {
                pool.touchAndMaybeEvict(poolKey, pooled);
            }
        }
    }

    private final Duration idleTimeout;
    private final Duration evictionInterval;
    private final Map<String, PooledClient> pool = new ConcurrentHashMap<>();
    private final Map<String, Object> creationLocks = new ConcurrentHashMap<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private DeviceClientFactory deviceClientFactory = DeviceClient::new;
    private LongSupplier nowMillisSupplier = System::currentTimeMillis;
    private ScheduledExecutorService evictionExecutor;

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

    public void start() {
        if (evictionExecutor == null) {
            evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "camel-azure-iothub-device-client-evictor");
                t.setDaemon(true);
                return t;
            });
        }
        evictionExecutor.scheduleAtFixedRate(this::evictIdleClients,
                evictionInterval.toMillis(), evictionInterval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("DeviceClient eviction enabled. idleTimeout={}s, interval={}s",
                idleTimeout.getSeconds(), evictionInterval.getSeconds());
    }

    public DeviceClient getOrCreate(String deviceId, String connectionString, IotHubClientProtocol protocol) {
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

        Object lock = creationLocks.computeIfAbsent(poolKey, k -> new Object());
        synchronized (lock) {
            try {
                existing = pool.get(poolKey);
                if (existing != null) {
                    existing.touch(now);
                    return existing.client;
                }

                DeviceClient client = createClient(deviceId, connectionString, protocol);
                PooledClient pooled = new PooledClient(client, now);
                PooledClient previous = pool.putIfAbsent(poolKey, pooled);
                if (previous != null) {
                    pooled.client.close();
                    previous.touch(now);
                    return previous.client;
                }
                return pooled.client;
            } finally {
                creationLocks.remove(poolKey, lock);
            }
        }
    }

    public Lease acquire(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        if (Thread.currentThread().isInterrupted() || shuttingDown.get()) {
            throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
        }

        String poolKey = resolvePoolKey(deviceId, connectionString);
        final long now = nowMillisSupplier.getAsLong();

        java.util.concurrent.atomic.AtomicReference<PooledClient> evicted = new java.util.concurrent.atomic.AtomicReference<>();
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
        }

        Object lock = creationLocks.computeIfAbsent(poolKey, k -> new Object());
        synchronized (lock) {
            try {
                existing = pool.get(poolKey);
                if (existing != null) {
                    existing.touch(now);
                    existing.acquire();
                    return new Lease(this, poolKey, existing);
                }

                DeviceClient client = createClient(deviceId, connectionString, protocol);
                PooledClient pooled = new PooledClient(client, now);
                PooledClient previous = pool.putIfAbsent(poolKey, pooled);
                if (previous != null) {
                    pooled.client.close();
                    previous.touch(now);
                    previous.acquire();
                    return new Lease(this, poolKey, previous);
                }
                pooled.acquire();
                return new Lease(this, poolKey, pooled);
            } finally {
                creationLocks.remove(poolKey, lock);
            }
        }
    }

    public Lease lease(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        return acquire(deviceId, connectionString, protocol);
    }

    public void remove(String deviceId, String connectionString) {
        String poolKey = resolvePoolKey(deviceId, connectionString);
        PooledClient pooled = pool.remove(poolKey);
        if (pooled != null) {
            closeQuietly(deviceId, pooled.client, "explicit removal");
        }
    }

    public void remove(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }

        PooledClient pooled = pool.remove(deviceId);
        if (pooled != null) {
            closeQuietly(deviceId, pooled.client, "explicit removal");
            return;
        }

        for (Iterator<Map.Entry<String, PooledClient>> it = pool.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, PooledClient> entry = it.next();
            String key = entry.getKey();
            if (matchesDeviceId(key, deviceId)) {
                if (pool.remove(key, entry.getValue())) {
                    closeQuietly(deviceId, entry.getValue().client, "explicit removal");
                    return;
                }
            }
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
        return identity.length() > 0 ? identity.toString() : deviceId;
    }

    private DeviceClient createClient(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        try {
            DeviceClient client = deviceClientFactory.create(connectionString, protocol);
            client.open(true);
            try {
                // Avoid duplicate desired-property subscriptions: subscribe only if not already subscribed.
                // The Azure DeviceClient does not expose a way to check, so we rely on only subscribing once per client creation.
                client.subscribeToDesiredProperties((twinUpdate, ctx) -> {
                }, null);
                client.setConnectionStatusChangeCallback(context -> {
                    if (context == null) {
                        return;
                    }
                    if (context.getNewStatus()
                        == com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus.DISCONNECTED) {
                        evictDisconnectedClient(deviceId, connectionString, client, context);
                    }
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
        if (context == null) {
            return;
        }
        if (context.getNewStatus() != IotHubConnectionStatus.DISCONNECTED) {
            return;
        }

        String poolKey = resolvePoolKey(deviceId, connectionString);
        PooledClient pooled = pool.get(poolKey);
        if (pooled != null && pooled.client == client && pool.remove(poolKey, pooled)) {
            LOG.warn("Evicted DeviceClient for device {} after disconnect. previousStatus={}, reason={}, cause={}",
                    deviceId, context.getPreviousStatus(), context.getNewStatusReason(), context.getCause());
            closeQuietly(deviceId, client, "connection status changed to DISCONNECTED");
            return;
        }

        for (Iterator<Map.Entry<String, PooledClient>> it = pool.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, PooledClient> entry = it.next();
            PooledClient candidate = entry.getValue();
            if (candidate != null && candidate.client == client && pool.remove(entry.getKey(), candidate)) {
                LOG.warn("Evicted DeviceClient for device {} after disconnect. previousStatus={}, reason={}, cause={}",
                        deviceId, context.getPreviousStatus(), context.getNewStatusReason(), context.getCause());
                closeQuietly(deviceId, client, "connection status changed to DISCONNECTED");
                return;
            }
        }
    }

    private void touchAndMaybeEvict(String poolKey, PooledClient pooled) {
        long now = nowMillisSupplier.getAsLong();
        long elapsed = now - pooled.lastUsed;
        pooled.touch(now);
        if (elapsed > idleTimeout.toMillis() && pool.remove(poolKey, pooled)) {
            closeQuietly(poolKey, pooled.client, "idle eviction");
        }
    }

    private void evictIdleClients() {
        if (shuttingDown.get()) {
            return;
        }
        long now = nowMillisSupplier.getAsLong();
        Iterator<Map.Entry<String, PooledClient>> it = pool.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PooledClient> entry = it.next();
            PooledClient pooled = entry.getValue();
            if (pooled.inUse.get() > 0) {
                continue;
            }
            if ((now - pooled.lastUsed) > idleTimeout.toMillis()
                    && pool.remove(entry.getKey(), pooled)) {
                closeQuietly(entry.getKey(), pooled.client, "idle eviction");
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
