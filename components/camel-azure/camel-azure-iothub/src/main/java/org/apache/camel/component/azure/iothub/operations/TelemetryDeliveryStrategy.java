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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.Message;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the body as a device-to-cloud telemetry message. The call blocks until the hub acknowledges delivery or the
 * configured timeout elapses.
 */
public class TelemetryDeliveryStrategy implements DeliveryStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryDeliveryStrategy.class);

    private final String contentType;
    private final Duration sendTimeout;

    public TelemetryDeliveryStrategy(String contentType, Duration sendTimeout) {
        this.contentType = contentType;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public void execute(DeviceClient client, String deviceId, DeliveryContext context) throws Exception {
        byte[] payload = toBytes(context.telemetry());

        Message msg = new Message(payload);
        msg.setContentType(contentType);
        msg.setContentEncoding(StandardCharsets.UTF_8.name());
        msg.setMessageId(UUID.randomUUID().toString());

        int timeoutMillis = Math.toIntExact(sendTimeout.toMillis());
        LOG.debug("Dispatching telemetry for device: {} with timeout {}ms", deviceId, timeoutMillis);
        client.sendEvent(msg, timeoutMillis);
        LOG.debug("Telemetry sent for device: {}", deviceId);
    }

    private byte[] toBytes(Object body) {
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        if (body instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException(
                "Telemetry delivery expects a byte[] or String body but was: "
                                           + (body == null ? "null" : body.getClass().getName()));
    }

    @Override
    public boolean supports(IoTHubDeliveryMode mode) {
        return mode == IoTHubDeliveryMode.TELEMETRY || mode == IoTHubDeliveryMode.BOTH;
    }
}
