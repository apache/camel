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

import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Producer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IoTHubComponentTest extends CamelTestSupport {

    @Test
    void testCreateEndpointWithDefaults() {
        final IoTHubEndpoint endpoint
                = context.getEndpoint("azure-iothub:myDevice?connectionString=HostName=h;DeviceId=myDevice;SharedAccessKey=k",
                        IoTHubEndpoint.class);

        assertEquals("myDevice", endpoint.getConfiguration().getDeviceId());
        assertEquals(IotHubClientProtocol.MQTT, endpoint.getConfiguration().getProtocol());
        assertEquals(IoTHubDeliveryMode.TELEMETRY, endpoint.getConfiguration().getDeliveryMode());
        assertEquals("application/json", endpoint.getConfiguration().getContentType());
        assertEquals(10000, endpoint.getConfiguration().getSendTimeoutMillis());
    }

    @Test
    void testCreateEndpointWithConfig() {
        final String uri = "azure-iothub:dev01?connectionString=cs&protocol=AMQPS&deliveryMode=TWIN"
                           + "&contentType=application/x-protobuf&sendTimeoutMillis=5000"
                           + "&deviceClientIdleTimeout=2m&deviceClientEvictionInterval=10s";

        final IoTHubEndpoint endpoint = context.getEndpoint(uri, IoTHubEndpoint.class);

        assertEquals("dev01", endpoint.getConfiguration().getDeviceId());
        assertEquals("cs", endpoint.getConfiguration().getConnectionString());
        assertEquals(IotHubClientProtocol.AMQPS, endpoint.getConfiguration().getProtocol());
        assertEquals(IoTHubDeliveryMode.TWIN, endpoint.getConfiguration().getDeliveryMode());
        assertEquals("application/x-protobuf", endpoint.getConfiguration().getContentType());
        assertEquals(5000, endpoint.getConfiguration().getSendTimeoutMillis());
        assertEquals(120, endpoint.getConfiguration().getDeviceClientIdleTimeout().getSeconds());
        assertEquals(10, endpoint.getConfiguration().getDeviceClientEvictionInterval().getSeconds());
    }

    @Test
    void testProducerCreationRequiresDeviceId() {
        final IoTHubEndpoint endpoint
                = context.getEndpoint("azure-iothub:?connectionString=cs", IoTHubEndpoint.class);

        // device id resolution happens at send time, but producer creation should still succeed
        assertNotNull(assertDoesNotThrowProducer(endpoint));
    }

    @Test
    void testConsumerNotSupported() {
        final IoTHubEndpoint endpoint = context.getEndpoint("azure-iothub:dev01?connectionString=cs", IoTHubEndpoint.class);
        assertThrows(UnsupportedOperationException.class, () -> endpoint.createConsumer(exchange -> {
        }));
    }

    private Producer assertDoesNotThrowProducer(IoTHubEndpoint endpoint) {
        try {
            return endpoint.createProducer();
        } catch (Exception e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
    }
}
