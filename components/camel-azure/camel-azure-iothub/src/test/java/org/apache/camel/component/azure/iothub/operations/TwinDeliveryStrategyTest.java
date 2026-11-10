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

import java.util.HashMap;
import java.util.Map;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.twin.Twin;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwinDeliveryStrategyTest {

    private final TwinDeliveryStrategy strategy = new TwinDeliveryStrategy();

    @Test
    void testSupports() {
        assertTrue(strategy.supports(IoTHubDeliveryMode.TWIN));
        assertTrue(strategy.supports(IoTHubDeliveryMode.BOTH));
        assertFalse(strategy.supports(IoTHubDeliveryMode.TELEMETRY));
    }

    @Test
    void testReportedPropertiesSentAsDelta() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        Twin twin = mock(Twin.class);
        TwinCollection reported = new TwinCollection();
        when(client.getTwin(anyInt())).thenReturn(twin);
        when(twin.getReportedProperties()).thenReturn(reported);

        Map<String, Object> body = new HashMap<>();
        body.put("firmware", "1.2.3");
        body.put("battery", 95);

        strategy.execute(client, "dev1", new DeliveryContext(null, body));

        ArgumentCaptor<TwinCollection> captor = ArgumentCaptor.forClass(TwinCollection.class);
        verify(client).updateReportedProperties(captor.capture(), anyInt());
        TwinCollection sent = captor.getValue();
        assertEquals("1.2.3", sent.get("firmware"));
        assertEquals(95, sent.get("battery"));
    }

    @Test
    void testNoChangesSkipsUpdate() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        Twin twin = mock(Twin.class);
        TwinCollection reported = new TwinCollection();
        reported.put("firmware", "1.2.3");
        when(client.getTwin(anyInt())).thenReturn(twin);
        when(twin.getReportedProperties()).thenReturn(reported);

        Map<String, Object> body = new HashMap<>();
        body.put("firmware", "1.2.3");

        strategy.execute(client, "dev1", new DeliveryContext(null, body));

        verify(client, never()).updateReportedProperties(any(), anyInt());
    }

    @Test
    void testNullValuesAreCleaned() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        Twin twin = mock(Twin.class);
        TwinCollection reported = new TwinCollection();
        when(client.getTwin(anyInt())).thenReturn(twin);
        when(twin.getReportedProperties()).thenReturn(reported);

        Map<String, Object> body = new HashMap<>();
        body.put("keep", "yes");
        body.put("drop", null);

        strategy.execute(client, "dev1", new DeliveryContext(null, body));

        ArgumentCaptor<TwinCollection> captor = ArgumentCaptor.forClass(TwinCollection.class);
        verify(client).updateReportedProperties(captor.capture(), anyInt());
        TwinCollection sent = captor.getValue();
        assertTrue(sent.containsKey("keep"));
        assertFalse(sent.containsKey("drop"));
    }

    @Test
    void testNullTwinPropertiesThrows() {
        DeviceClient client = mock(DeviceClient.class);
        assertThrows(IllegalArgumentException.class,
                () -> strategy.execute(client, "dev1", new DeliveryContext("telemetry-only", null)));
    }

    @Test
    void testNumberTypesAreNormalized() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        Twin twin = mock(Twin.class);
        TwinCollection reported = new TwinCollection();
        reported.put("battery", 95L);
        when(client.getTwin(anyInt())).thenReturn(twin);
        when(twin.getReportedProperties()).thenReturn(reported);

        Map<String, Object> body = new HashMap<>();
        body.put("battery", 95);

        strategy.execute(client, "dev1", new DeliveryContext(null, body));

        verify(client, never()).updateReportedProperties(any(), anyInt());
    }

    @Test
    void testEmptyNestedMapsArePreserved() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        Twin twin = mock(Twin.class);
        TwinCollection reported = new TwinCollection();
        when(client.getTwin(anyInt())).thenReturn(twin);
        when(twin.getReportedProperties()).thenReturn(reported);

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        body.put("status", nested);

        strategy.execute(client, "dev1", new DeliveryContext(null, body));

        ArgumentCaptor<TwinCollection> captor = ArgumentCaptor.forClass(TwinCollection.class);
        verify(client).updateReportedProperties(captor.capture(), anyInt());
        TwinCollection sent = captor.getValue();
        assertTrue(sent.containsKey("status"));
        assertEquals(new TwinCollection(), sent.get("status"));
    }

}
