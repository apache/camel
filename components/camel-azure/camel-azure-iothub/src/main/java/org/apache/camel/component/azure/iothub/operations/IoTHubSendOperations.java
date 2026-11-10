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
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.apache.camel.component.azure.iothub.client.DeviceClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches a message body to Azure IoT Hub using a single delivery attempt per exchange.
 *
 * <p>
 * Camel redelivery and the Azure SDK retry policy handle transient failures; this component does not add a second retry
 * layer that can duplicate telemetry or twin updates.
 * </p>
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
     * @throws Exception if the send is invalid or cannot be delivered
     */
    public boolean send(
            String deviceId, String connectionString, IotHubClientProtocol protocol,
            IoTHubDeliveryMode mode, DeliveryContext context)
            throws Exception {

        if (context == null || context.isEmpty()) {
            throw new IllegalArgumentException("No telemetry payload or twin properties provided for device: " + deviceId);
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Thread interrupted before dispatch for device: " + deviceId);
        }

        try (DeviceClientPool.Lease lease = pool.lease(deviceId, connectionString, protocol)) {
            DeviceClient client = lease.client();
            executeStrategies(client, deviceId, context, mode);
        } catch (Exception e) {
            if (isShutdownInterruption(e)) {
                InterruptedException interrupted = new InterruptedException("Dispatch interrupted for device: " + deviceId);
                interrupted.initCause(e);
                throw interrupted;
            }
            throw e;
        }
        return true;
    }

    private void executeStrategies(DeviceClient client, String deviceId, DeliveryContext context, IoTHubDeliveryMode mode)
            throws Exception {
        for (DeliveryStrategy strategy : strategies) {
            if (strategy.supports(mode)) {
                strategy.execute(client, deviceId, context);
            }
        }
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
