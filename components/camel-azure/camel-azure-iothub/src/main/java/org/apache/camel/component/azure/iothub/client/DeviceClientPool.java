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
import java.util.function.LongSupplier;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe pool of Azure IoT Hub {@link DeviceClient} instances, keyed by device id.
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

        PooledClient(DeviceClient client, long nowMillis) {
            this.client = client;
            this.lastUsed = nowMillis;
        }

        void touch(long nowMillis) {
            this.lastUsed = nowMillis;
        }
    }

    private final Duration idleTimeout;
    private final Duration evictionInterval;
    private final Map<String, PooledClient> pool = new ConcurrentHashMap<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private DeviceClientFactory deviceClientFactory = DeviceClient::new;
    private LongSupplier nowMillisSupplier = System::currentTimeMillis;
    private ScheduledExecutorService evictionExecutor;

    public DeviceClientPool(Duration idleTimeout, Duration evictionInterval) {
        this.idleTimeout = isPositive(idleTimeout) ? idleTimeout : Duration.ofMinutes(5);
        this.evictionInterval = isPositive(evictionInterval) ? evictionInterval : Duration.ofSeconds(30);
    }

    private static boolean isPositive(Duration d) {
        return d != null && !d.isZero() && !d.isNegative();
    }

    public void start() {
        evictionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camel-azure-iothub-device-client-evictor");
            t.setDaemon(true);
            return t;
        });
        evictionExecutor.scheduleAtFixedRate(this::evictIdleClients,
                evictionInterval.toMillis(), evictionInterval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("DeviceClient eviction enabled. idleTimeout={}s, interval={}s",
                idleTimeout.getSeconds(), evictionInterval.getSeconds());
    }

    public DeviceClient getOrCreate(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        if (Thread.currentThread().isInterrupted() || shuttingDown.get()) {
            throw new IllegalStateException("Skipping client creation due to shutdown: " + deviceId);
        }
        final long now = nowMillisSupplier.getAsLong();

        PooledClient pooled = pool.compute(deviceId, (id, current) -> {
            if (current != null) {
                current.touch(now);
                return current;
            }
            DeviceClient client = createClient(id, connectionString, protocol);
            return client == null ? null : new PooledClient(client, now);
        });

        if (pooled == null) {
            throw new IllegalStateException("Client creation interrupted due to shutdown: " + deviceId);
        }
        pooled.touch(now);
        return pooled.client;
    }

    private DeviceClient createClient(String deviceId, String connectionString, IotHubClientProtocol protocol) {
        try {
            DeviceClient client = deviceClientFactory.create(connectionString, protocol);
            client.open(true);
            LOG.info("Created new DeviceClient for device: {}", deviceId);
            return client;
        } catch (Exception e) {
            if (isShutdownInterruption(e)) {
                Thread.currentThread().interrupt();
                LOG.warn("DeviceClient creation interrupted due to shutdown for device: {}", deviceId);
                return null;
            }
            throw new RuntimeException("DeviceClient creation failed for device: " + deviceId, e);
        }
    }

    public void remove(String deviceId) {
        PooledClient pooled = pool.remove(deviceId);
        if (pooled != null) {
            closeQuietly(deviceId, pooled.client, "explicit removal");
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
            if ((now - entry.getValue().lastUsed) > idleTimeout.toMillis()
                    && pool.remove(entry.getKey(), entry.getValue())) {
                closeQuietly(entry.getKey(), entry.getValue().client, "idle eviction");
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
