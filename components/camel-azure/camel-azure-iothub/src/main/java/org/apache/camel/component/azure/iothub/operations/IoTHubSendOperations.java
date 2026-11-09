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
package org.apache.camel.component.azure.iothub.operations;

import java.util.List;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.apache.camel.component.azure.iothub.client.DeviceClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches a message body to Azure IoT Hub, reusing pooled {@link DeviceClient} instances and recovering from
 * connection-level failures with a single retry (first with the same client if the error is retryable, otherwise with a
 * freshly created client).
 */
public class IoTHubSendOperations {

    private static final Logger LOG = LoggerFactory.getLogger(IoTHubSendOperations.class);

    private final DeviceClientPool pool;
    private final List<DeliveryStrategy> strategies;

    public IoTHubSendOperations(DeviceClientPool pool, List<DeliveryStrategy> strategies) {
        this.pool = pool;
        this.strategies = strategies;
    }

    /**
     * @return {@code true} if delivered, {@code false} if skipped (e.g. shutdown or empty payload)
     */
    public boolean send(
            String deviceId, String connectionString, IotHubClientProtocol protocol,
            IoTHubDeliveryMode mode, DeliveryContext context)
            throws Exception {

        if (context == null || context.isEmpty()) {
            LOG.warn("Skipping empty payload for device: {}", deviceId);
            return false;
        }
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        DeviceClient client = pool.getOrCreate(deviceId, connectionString, protocol);
        try {
            executeStrategies(client, deviceId, context, mode);
            return true;
        } catch (Exception firstFailure) {
            if (isShutdownInterruption(firstFailure)) {
                return false;
            }

            if (isRetryable(firstFailure)) {
                LOG.warn("Transient dispatch failure for device [{}]; retrying once with existing client", deviceId);
                executeStrategies(client, deviceId, context, mode);
                return true;
            }

            if (!shouldRecreateClient(firstFailure)) {
                throw firstFailure;
            }

            LOG.warn("Connection failure for device [{}]; retrying with a new client", deviceId, firstFailure);
            pool.remove(deviceId);
            DeviceClient retryClient = pool.getOrCreate(deviceId, connectionString, protocol);
            executeStrategies(retryClient, deviceId, context, mode);
            return true;
        }
    }

    private void executeStrategies(DeviceClient client, String deviceId, DeliveryContext context, IoTHubDeliveryMode mode)
            throws Exception {
        for (DeliveryStrategy strategy : strategies) {
            if (strategy.supports(mode)) {
                strategy.execute(client, deviceId, context);
            }
        }
    }

    private boolean isRetryable(Throwable t) {
        IotHubClientException hub = findCause(t, IotHubClientException.class);
        return hub != null && hub.isRetryable();
    }

    private boolean shouldRecreateClient(Throwable t) {
        return findCause(t, IllegalStateException.class) != null || containsTypeName(t, "TransportException");
    }

    private boolean containsTypeName(Throwable t, String simpleName) {
        while (t != null) {
            if (t.getClass().getSimpleName().equals(simpleName)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        while (t != null) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            t = t.getCause();
        }
        return null;
    }

    private boolean isShutdownInterruption(Throwable t) {
        while (t != null) {
            if (t instanceof InterruptedException) {
                return true;
            }
            t = t.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }
}
