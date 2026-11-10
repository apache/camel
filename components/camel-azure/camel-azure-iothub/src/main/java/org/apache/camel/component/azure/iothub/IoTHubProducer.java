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
package org.apache.camel.component.azure.iothub;

import java.util.List;
import java.util.Map;

import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.iothub.client.DeviceClientPool;
import org.apache.camel.component.azure.iothub.operations.DeliveryContext;
import org.apache.camel.component.azure.iothub.operations.DeliveryStrategy;
import org.apache.camel.component.azure.iothub.operations.IoTHubSendOperations;
import org.apache.camel.component.azure.iothub.operations.TelemetryDeliveryStrategy;
import org.apache.camel.component.azure.iothub.operations.TwinDeliveryStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class IoTHubProducer extends DefaultProducer {

    private DeviceClientPool pool;
    private IoTHubSendOperations operations;

    public IoTHubProducer(IoTHubEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        final IoTHubConfiguration config = getConfiguration();

        IoTHubComponent component = (IoTHubComponent) getEndpoint().getComponent();
        pool = component.getOrCreateClientPool();

        List<DeliveryStrategy> strategies = List.of(
                new TelemetryDeliveryStrategy(config.getContentType(), config.getSendTimeout()),
                new TwinDeliveryStrategy());

        operations = new IoTHubSendOperations(pool, strategies);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final IoTHubConfiguration config = getConfiguration();

        String deviceId = header(exchange, IoTHubConstants.DEVICE_ID, config.getDeviceId());
        String connectionString = header(exchange, IoTHubConstants.CONNECTION_STRING, config.getConnectionString());
        IoTHubDeliveryMode mode = exchange.getIn().getHeader(IoTHubConstants.DELIVERY_MODE,
                config.getDeliveryMode(), IoTHubDeliveryMode.class);
        IotHubClientProtocol protocol = config.getProtocol();

        if (ObjectHelper.isEmpty(deviceId)) {
            throw new IllegalArgumentException(
                    "Device id must be set on the endpoint or via the " + IoTHubConstants.DEVICE_ID + " header");
        }
        if (ObjectHelper.isEmpty(connectionString)) {
            throw new IllegalArgumentException(
                    "Connection string must be set on the endpoint or via the "
                                               + IoTHubConstants.CONNECTION_STRING + " header");
        }

        Object body = exchange.getIn().getBody();
        DeliveryContext context = buildContext(exchange, mode, body);
        boolean sent = operations.send(deviceId, connectionString, protocol, mode, context);
        if (!sent) {
            throw new IllegalStateException("IoTHub send operation reported no success for device: " + deviceId);
        }
    }

    /**
     * Resolves the telemetry payload and twin properties for this exchange based on the delivery mode.
     *
     * <ul>
     * <li>{@code TELEMETRY}: the body is the telemetry payload (byte[]/String).</li>
     * <li>{@code TWIN}: the twin properties come from the {@code CamelAzureIoTHubTwinProperties} header, or the body
     * when it is a {@link Map}.</li>
     * <li>{@code BOTH}: the body is the telemetry payload and the twin properties come from the
     * {@code CamelAzureIoTHubTwinProperties} header.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private DeliveryContext buildContext(Exchange exchange, IoTHubDeliveryMode mode, Object body) throws Exception {
        Map<String, Object> twinHeader = exchange.getIn().getHeader(IoTHubConstants.TWIN_PROPERTIES, Map.class);

        return switch (mode) {
            case TELEMETRY -> new DeliveryContext(resolveTelemetryBody(exchange, body), null);
            case TWIN -> {
                Map<String, Object> twin = twinHeader != null
                        ? twinHeader
                        : (body instanceof Map ? (Map<String, Object>) body : null);
                yield new DeliveryContext(null, twin);
            }
            case BOTH -> new DeliveryContext(resolveTelemetryBody(exchange, body), twinHeader);
        };
    }

    private byte[] resolveTelemetryBody(Exchange exchange, Object body) throws Exception {
        if (body instanceof byte[] bytes) {
            return bytes;
        }
        return exchange.getIn().getMandatoryBody(byte[].class);
    }

    private String header(Exchange exchange, String name, String fallback) {
        String value = exchange.getIn().getHeader(name, String.class);
        return ObjectHelper.isNotEmpty(value) ? value : fallback;
    }

    @Override
    protected void doStop() throws Exception {
        // component owns the shared pool lifecycle
        super.doStop();
    }

    @Override
    public IoTHubEndpoint getEndpoint() {
        return (IoTHubEndpoint) super.getEndpoint();
    }

    public IoTHubConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
}
