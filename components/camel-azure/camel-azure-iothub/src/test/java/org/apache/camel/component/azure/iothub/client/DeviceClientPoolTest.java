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
package org.apache.camel.component.azure.iothub.client;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeviceClientPoolTest {

    private static final String CS = "HostName=h;DeviceId=d;SharedAccessKey=k";

    @Test
    void testGetOrCreateCreatesOnceAndReuses() throws Exception {
        AtomicInteger created = new AtomicInteger();
        DeviceClient client = mock(DeviceClient.class);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            created.incrementAndGet();
            return client;
        });

        DeviceClient first = pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        DeviceClient second = pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);

        assertSame(client, first);
        assertSame(first, second);
        assertEquals(1, created.get(), "client should be created once and reused");
        assertEquals(1, pool.getActiveClientCount());
        verify(client, times(1)).open(true);
    }

    @Test
    void testRejectsMismatchedDeviceId() {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> mock(DeviceClient.class));

        assertThrows(IllegalArgumentException.class, () -> pool.getOrCreate("dev1", CS, IotHubClientProtocol.MQTT));
    }

    @Test
    void testRemoveClosesClient() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        pool.remove("d");

        assertEquals(0, pool.getActiveClientCount());
        verify(client, times(1)).close();
    }

    @Test
    void testShutdownClosesAllAndBlocksCreation() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> client);

        pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
        pool.shutdown();

        assertEquals(0, pool.getActiveClientCount());
        verify(client, times(1)).close();
        assertThrows(IllegalStateException.class, () -> pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT));
    }

    @Test
    void testIdleClientsAreEvicted() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        AtomicLong clock = new AtomicLong(0);

        DeviceClientPool pool = new DeviceClientPool(Duration.ofMillis(10), Duration.ofMillis(20));
        pool.setDeviceClientFactory((cs, protocol) -> client);
        pool.setNowMillisSupplier(clock::get);
        pool.start();
        try {
            pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT);
            assertEquals(1, pool.getActiveClientCount());

            clock.set(10_000);
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> pool.getActiveClientCount() == 0);
            assertEquals(0, pool.getActiveClientCount(), "idle client should have been evicted");
            verify(client, Mockito.atLeastOnce()).close();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void testCreationFailurePropagates() {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            throw new IllegalArgumentException("bad connection string");
        });

        assertThrows(RuntimeException.class, () -> pool.getOrCreate("d", CS, IotHubClientProtocol.MQTT));
        assertEquals(0, pool.getActiveClientCount());
    }
}
