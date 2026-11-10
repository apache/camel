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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import org.apache.camel.component.azure.iothub.IoTHubDeliveryMode;
import org.apache.camel.component.azure.iothub.client.DeviceClientPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IoTHubSendOperationsTest {

    private static final String CS = "HostName=h;DeviceId=d;SharedAccessKey=k";

    private DeviceClientPool poolReturning(DeviceClient client, AtomicInteger createCount) {
        DeviceClientPool pool = new DeviceClientPool(Duration.ofMinutes(5), Duration.ofSeconds(30));
        pool.setDeviceClientFactory((cs, protocol) -> {
            if (createCount != null) {
                createCount.incrementAndGet();
            }
            return client;
        });
        return pool;
    }

    @Test
    void testNullBodyIsSkipped() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy strategy = new CountingStrategy(0);
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, null), List.of(strategy));

        assertThrows(IllegalArgumentException.class,
                () -> ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY, null));
        assertEquals(0, strategy.executed());
    }

    @Test
    void testEmptyContextIsSkipped() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy strategy = new CountingStrategy(0);
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, null), List.of(strategy));

        assertThrows(IllegalArgumentException.class,
                () -> ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY,
                        new DeliveryContext(null, null)));
        assertEquals(0, strategy.executed());
    }

    @Test
    void testSuccessfulDispatch() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy strategy = new CountingStrategy(0);
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, null), List.of(strategy));

        assertTrue(ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY, telemetry("payload")));
        assertEquals(1, strategy.executed());
    }

    @Test
    void testBothModeRunsAllSupportingStrategies() throws Exception {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy telemetry = new CountingStrategy(0);
        CountingStrategy twin = new CountingStrategy(0);
        IoTHubSendOperations ops
                = new IoTHubSendOperations(poolReturning(client, null), List.of(telemetry, twin));

        assertTrue(ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.BOTH,
                new DeliveryContext("payload", Map.of("firmware", "1.0"))));
        assertEquals(1, telemetry.executed());
        assertEquals(1, twin.executed());
    }

    @Test
    void testRetryableFailureRetriesOnceWithSameClient() {
        DeviceClient client = mock(DeviceClient.class);
        IotHubClientException retryable = mock(IotHubClientException.class);
        when(retryable.isRetryable()).thenReturn(true);

        CountingStrategy strategy = new CountingStrategy(1, new RuntimeException(retryable));
        AtomicInteger created = new AtomicInteger();
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, created), List.of(strategy));

        assertThrows(RuntimeException.class,
                () -> ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY, telemetry("payload")));
        assertEquals(1, strategy.attempts(), "single delivery attempt retains Azure SDK/Camel retry semantics");
        assertEquals(1, created.get(), "no client recreation for a single delivery attempt");
    }

    @Test
    void testConnectionFailureDoesNotRetryOrRecreateClient() {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy strategy = new CountingStrategy(1, new IllegalStateException("broken connection"));
        AtomicInteger created = new AtomicInteger();
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, created), List.of(strategy));

        assertThrows(IllegalStateException.class,
                () -> ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY, telemetry("payload")));
        assertEquals(1, strategy.attempts());
        assertEquals(1, created.get(), "single send attempt must not recreate the client");
    }

    @Test
    void testNonRetryableFailurePropagates() {
        DeviceClient client = mock(DeviceClient.class);
        CountingStrategy strategy = new CountingStrategy(Integer.MAX_VALUE, new IllegalArgumentException("bad payload"));
        IoTHubSendOperations ops = new IoTHubSendOperations(poolReturning(client, null), List.of(strategy));

        assertThrows(IllegalArgumentException.class,
                () -> ops.send("d", CS, IotHubClientProtocol.MQTT, IoTHubDeliveryMode.TELEMETRY, telemetry("payload")));
    }

    private static DeliveryContext telemetry(Object body) {
        return new DeliveryContext(body, null);
    }

    /**
     * Delivery strategy that throws the given exception for the first {@code failTimes} attempts, then succeeds.
     */
    private static final class CountingStrategy implements DeliveryStrategy {
        private final int failTimes;
        private final RuntimeException failure;
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger executed = new AtomicInteger();

        CountingStrategy(int failTimes) {
            this(failTimes, new RuntimeException("boom"));
        }

        CountingStrategy(int failTimes, RuntimeException failure) {
            this.failTimes = failTimes;
            this.failure = failure;
        }

        @Override
        public void execute(DeviceClient client, String deviceId, DeliveryContext context) {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failTimes) {
                throw failure;
            }
            executed.incrementAndGet();
        }

        @Override
        public boolean supports(IoTHubDeliveryMode mode) {
            return true;
        }

        int attempts() {
            return attempts.get();
        }

        int executed() {
            return executed.get();
        }
    }
}
