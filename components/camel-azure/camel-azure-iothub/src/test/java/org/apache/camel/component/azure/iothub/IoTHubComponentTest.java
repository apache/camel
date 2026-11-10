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

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.iothub.client.DeviceClientPool;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        assertEquals(Duration.ofSeconds(10), endpoint.getConfiguration().getSendTimeout());
    }

    @Test
    void testComponentPoolSettingsAreApplied() throws Exception {
        IoTHubComponent component = new IoTHubComponent();
        component.setCamelContext(context);
        component.setDeviceClientIdleTimeout(Duration.ofMinutes(2));
        component.setDeviceClientEvictionInterval(Duration.ofSeconds(10));

        DeviceClientPool pool = component.getOrCreateClientPool();

        assertEquals(Duration.ofMinutes(2), getPoolField(pool, "idleTimeout"));
        assertEquals(Duration.ofSeconds(10), getPoolField(pool, "evictionInterval"));
    }

    @Test
    void testCreateEndpointWithConfig() {
        final String uri = "azure-iothub:dev01?connectionString=cs&protocol=AMQPS&deliveryMode=TWIN"
                           + "&contentType=application/x-protobuf&sendTimeout=5s";

        final IoTHubEndpoint endpoint = context.getEndpoint(uri, IoTHubEndpoint.class);

        assertEquals("dev01", endpoint.getConfiguration().getDeviceId());
        assertEquals("cs", endpoint.getConfiguration().getConnectionString());
        assertEquals(IotHubClientProtocol.AMQPS, endpoint.getConfiguration().getProtocol());
        assertEquals(IoTHubDeliveryMode.TWIN, endpoint.getConfiguration().getDeliveryMode());
        assertEquals("application/x-protobuf", endpoint.getConfiguration().getContentType());
        assertEquals(Duration.ofSeconds(5), endpoint.getConfiguration().getSendTimeout());
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

    @Test
    void testProducerProcessTelemetryConversion() throws Exception {
        IoTHubConfiguration configuration = new IoTHubConfiguration();
        configuration.setConnectionString("HostName=h;DeviceId=myDevice;SharedAccessKey=k");
        configuration.setDeviceId("myDevice");
        configuration.setProtocol(IotHubClientProtocol.MQTT);

        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        IoTHubComponent component = new IoTHubComponent();
        component.setCamelContext(context);
        setField(component, "clientPool", pool);

        IoTHubEndpoint endpoint = new IoTHubEndpoint(
                "azure-iothub:myDevice?connectionString=HostName=h;DeviceId=myDevice;SharedAccessKey=k",
                component, configuration);
        IoTHubProducer producer = new IoTHubProducer(endpoint);
        producer.start();

        Exchange exchange = new DefaultExchange(context);
        String payload = "{\"foo\":\"bar\"}";
        exchange.getIn().setBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));

        AtomicReference<Message> sent = new AtomicReference<>();
        doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return null;
        }).when(client).sendEvent(any(Message.class), anyInt());

        producer.process(exchange);

        assertNotNull(sent.get());
        assertEquals("application/json", sent.get().getContentType());
        assertEquals(payload, new String(sent.get().getBytes(), StandardCharsets.UTF_8));
        verify(client).sendEvent(any(Message.class), anyInt());
    }

    @Test
    void testProducerRejectsBothModeWithoutTwinHeader() throws Exception {
        IoTHubConfiguration configuration = new IoTHubConfiguration();
        configuration.setConnectionString("HostName=h;DeviceId=myDevice;SharedAccessKey=k");
        configuration.setDeviceId("myDevice");
        configuration.setProtocol(IotHubClientProtocol.MQTT);
        configuration.setDeliveryMode(IoTHubDeliveryMode.BOTH);

        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        IoTHubComponent component = new IoTHubComponent();
        component.setCamelContext(context);
        setField(component, "clientPool", pool);

        IoTHubEndpoint endpoint = new IoTHubEndpoint(
                "azure-iothub:myDevice?connectionString=HostName=h;DeviceId=myDevice;SharedAccessKey=k",
                component, configuration);
        IoTHubProducer producer = new IoTHubProducer(endpoint);
        producer.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("{\"foo\":\"bar\"}");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> producer.process(exchange));
        assertEquals(
                "BOTH delivery requires twin properties via the CamelAzureIoTHubTwinProperties header before dispatching telemetry",
                ex.getMessage());
        verify(client, org.mockito.Mockito.never()).sendEvent(any(Message.class), anyInt());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPoolField(DeviceClientPool pool, String fieldName) throws Exception {
        Field field = DeviceClientPool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(pool);
    }

    private Producer assertDoesNotThrowProducer(IoTHubEndpoint endpoint) {
        try {
            return endpoint.createProducer();
        } catch (Exception e) {
            throw new org.apache.camel.FailedToCreateProducerException(endpoint, e);
        }
    }
}
