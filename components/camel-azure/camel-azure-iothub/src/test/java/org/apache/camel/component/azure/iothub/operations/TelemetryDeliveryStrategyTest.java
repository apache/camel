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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.Message;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TelemetryDeliveryStrategyTest {

    private final TelemetryDeliveryStrategy strategy
            = new TelemetryDeliveryStrategy("application/x-protobuf", Duration.ofMillis(5000));

    @Test
    void testSupports() {
        assertTrue(strategy.supports(IoTHubDeliveryMode.TELEMETRY));
        assertTrue(strategy.supports(IoTHubDeliveryMode.BOTH));
        assertFalse(strategy.supports(IoTHubDeliveryMode.TWIN));
    }

    @Test
    void testSendByteArrayBody() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        byte[] body = { 1, 2, 3, 4 };

        strategy.execute(client, "dev1", new DeliveryContext(body, null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendEvent(captor.capture(), eq(5000));
        Message sent = captor.getValue();
        assertArrayEquals(body, sent.getBytes());
        assertEquals("application/x-protobuf", sent.getContentType());
    }

    @Test
    void testSendStringBody() throws Exception {
        DeviceClient client = mock(DeviceClient.class);

        strategy.execute(client, "dev1", new DeliveryContext("hello", null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendEvent(captor.capture(), eq(5000));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), captor.getValue().getBytes());
    }

    @Test
    void testSendInputStreamBody() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);

        strategy.execute(client, "dev1", new DeliveryContext(new ByteArrayInputStream(body), null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendEvent(captor.capture(), eq(5000));
        assertArrayEquals(body, captor.getValue().getBytes());
    }

    @Test
    void testUnsupportedBodyThrows() {
        DeviceClient client = mock(DeviceClient.class);
        assertThrows(IllegalArgumentException.class,
                () -> strategy.execute(client, "dev1", new DeliveryContext(42, null)));
    }
}
