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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.twin.DesiredPropertiesCallback;
import com.microsoft.azure.sdk.iot.device.twin.Twin;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
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
import static org.mockito.Mockito.when;

/**
 * The protocol, contentEncoding and sendTimeout options of the producer, with a mocked DeviceClient that throws on the
 * subscription to desired properties over HTTPS, as the Azure IoT SDK does.
 */
class IoTHubProducerProtocolOptionsTest extends CamelTestSupport {

    private static final String CONNECTION_STRING = "HostName=h;DeviceId=myDevice;SharedAccessKey=k";
    private static final String TWIN_OVER_HTTPS
            = "Twin operations are only supported over MQTT, MQTT_WS, AMQPS, and AMQPS_WS";

    // the device clients the pool created, a new mock for each
    private final List<DeviceClient> created = new CopyOnWriteArrayList<>();
    private DeviceClient client;

    @BeforeEach
    void mockDeviceClient() throws Exception {
        IoTHubComponent component = context.getComponent("azure-iothub", IoTHubComponent.class);
        component.getOrCreateClientPool().setDeviceClientFactory((cs, protocol) -> {
            DeviceClient mock = mock(DeviceClient.class);
            if (protocol == IotHubClientProtocol.HTTPS) {
                doThrow(new UnsupportedOperationException(TWIN_OVER_HTTPS)).when(mock)
                        .subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), any());
                doThrow(new UnsupportedOperationException(TWIN_OVER_HTTPS)).when(mock)
                        .subscribeToDesiredProperties(any(DesiredPropertiesCallback.class), any(), anyInt());
                doThrow(new UnsupportedOperationException(TWIN_OVER_HTTPS)).when(mock).getTwin(anyInt());
            } else {
                Twin twin = mock(Twin.class);
                when(twin.getReportedProperties()).thenReturn(new TwinCollection());
                when(mock.getTwin(anyInt())).thenReturn(twin);
            }
            created.add(mock);
            client = mock;
            return mock;
        });
    }

    private Exchange send(String options, Object body) throws Exception {
        return send(options, body, null);
    }

    private Exchange send(String options, Object body, IoTHubDeliveryMode modeHeader) throws Exception {
        Producer producer = createProducer(options);
        producer.start();
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(body);
            if (modeHeader != null) {
                exchange.getIn().setHeader(IoTHubConstants.DELIVERY_MODE, modeHeader);
            }
            producer.process(exchange);
            return exchange;
        } finally {
            producer.stop();
        }
    }

    private Producer createProducer(String options) throws Exception {
        return context.getEndpoint("azure-iothub:myDevice?connectionString=" + CONNECTION_STRING + options).createProducer();
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
    void aTwinEndpointOverHttpsFailsToStart() throws Exception {
        for (String mode : List.of("TWIN", "BOTH")) {
            Producer producer = createProducer("&protocol=HTTPS&deliveryMode=" + mode);

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, producer::start);

            assertTrue(e.getMessage().contains("not over HTTPS"), e.getMessage());
        }
        assertEquals(0, created.size(), "no device client should be opened");
    }

    @Test
    void aTwinDeliveryModeHeaderOverHttpsFailsNamingTheProtocols() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> send("&protocol=HTTPS", Map.of("firmware", "1.0"), IoTHubDeliveryMode.TWIN));

        assertTrue(e.getMessage().contains("not over HTTPS"), e.getMessage());
        assertEquals(0, created.size(), "no device client should be opened");
    }

    @Test
    void anHttpsClientIsNotReusedForATwinUpdateOverMqtt() throws Exception {
        send("&protocol=HTTPS", "{\"temperature\":21}".getBytes(StandardCharsets.UTF_8));
        // failed with the UnsupportedOperationException of the SDK: the MQTT endpoint got the pooled HTTPS client
        send("&deliveryMode=TWIN", Map.of("firmware", "1.0"));

        assertEquals(2, created.size(), "the MQTT endpoint reused the device client of the HTTPS endpoint");
        verify(created.get(1)).updateReportedProperties(any(TwinCollection.class), anyInt());
    }

    @Test
    void mqttAndAmqpsShareOneClient() throws Exception {
        // IoT Hub allows one MQTT or AMQP connection per device: a second client would disconnect the first
        send("", "{}".getBytes(StandardCharsets.UTF_8));
        send("&protocol=AMQPS", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, created.size());
    }

    @Test
    void sendTimeoutLimitsTheSubscriptionToDesiredProperties() throws Exception {
        send("&sendTimeout=3s", "{}".getBytes(StandardCharsets.UTF_8));

        // failed on main: the subscription used the SDK default timeout
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
