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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.twin.DesiredPropertiesCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The protocol, contentEncoding and sendTimeout options of the producer, with a mocked DeviceClient that throws on the
 * subscription to desired properties over HTTPS, as the Azure IoT SDK does.
 */
class IoTHubProducerProtocolOptionsTest extends CamelTestSupport {

    private static final String CONNECTION_STRING = "HostName=h;DeviceId=myDevice;SharedAccessKey=k";
    private static final String TWIN_OVER_HTTPS
            = "Twin operations are only supported over MQTT, MQTT_WS, AMQPS, and AMQPS_WS";

    private final AtomicInteger created = new AtomicInteger();
    private DeviceClient client;

    @BeforeEach
    void mockDeviceClient() throws Exception {
        client = mock(DeviceClient.class);
        IoTHubComponent component = context.getComponent("azure-iothub", IoTHubComponent.class);
        component.getOrCreateClientPool().setDeviceClientFactory((cs, protocol) -> {
            created.incrementAndGet();
            if (protocol == IotHubClientProtocol.HTTPS) {
                doThrow(new UnsupportedOperationException(TWIN_OVER_HTTPS)).when(client)
                        .subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), any());
                doThrow(new UnsupportedOperationException(TWIN_OVER_HTTPS)).when(client)
                        .subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), any(), anyInt());
            }
            return client;
        });
    }

    private Exchange send(String options, Object body) throws Exception {
        Producer producer = context.getEndpoint(
                "azure-iothub:myDevice?connectionString=" + CONNECTION_STRING + options).createProducer();
        producer.start();
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(body);
            producer.process(exchange);
            return exchange;
        } finally {
            producer.stop();
        }
    }

    private Message sentMessage() throws Exception {
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(client).sendEvent(message.capture(), anyInt());
        return message.getValue();
    }

    @Test
    void telemetryIsSentOverHttps() throws Exception {
        // failed with "DeviceClient creation failed", as the subscription to desired properties threw
        send("&protocol=HTTPS", "{\"temperature\":21}".getBytes(StandardCharsets.UTF_8));

        assertEquals("{\"temperature\":21}", new String(sentMessage().getBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void twinOverHttpsFailsNamingTheProtocols() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> send("&protocol=HTTPS&deliveryMode=TWIN", Map.of("firmware", "1.0")));

        assertTrue(e.getMessage().contains("not over HTTPS"), e.getMessage());
        assertEquals(0, created.get(), "no device client should be opened");
    }

    @Test
    void sendTimeoutLimitsTheSubscriptionToDesiredProperties() throws Exception {
        send("&sendTimeout=3s", "{}".getBytes(StandardCharsets.UTF_8));

        // the subscription used the default timeout of the Azure IoT SDK
        verify(client).subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), isNull(), eq(3000));
        verify(client, never()).subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), any());
    }

    @Test
    void contentEncodingIsUtf8ByDefault() throws Exception {
        send("", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals("UTF-8", sentMessage().getContentEncoding());
    }

    @Test
    void contentEncodingCanBeChanged() throws Exception {
        send("&contentEncoding=UTF-16", "{}".getBytes(StandardCharsets.UTF_16));

        assertEquals("UTF-16", sentMessage().getContentEncoding());
    }

    @Test
    void emptyContentEncodingSendsNone() throws Exception {
        // a binary body was always labeled as UTF-8 text
        send("&contentType=application/octet-stream&contentEncoding=", new byte[] { 1, 2, 3 });

        assertNull(sentMessage().getContentEncoding());
    }
}
