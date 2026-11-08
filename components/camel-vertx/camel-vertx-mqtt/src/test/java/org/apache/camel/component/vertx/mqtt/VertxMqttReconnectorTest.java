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
package org.apache.camel.component.vertx.mqtt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VertxMqttReconnectorTest {

    private static final BrokerAddress BROKER = BrokerAddress.parse("tcp://broker.example.com");

    private final Vertx vertx = Vertx.vertx();

    @AfterEach
    void close() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void theDelayDoublesUpToTheMaximumAndStartsAgainAfterASuccess() {
        List<Long> delays = new CopyOnWriteArrayList<>();
        VertxMqttReconnector[] reconnector = new VertxMqttReconnector[1];
        // Every attempt fails and asks for the next one, four times
        reconnector[0] = new VertxMqttReconnector(vertx, BROKER, 10, 35, () -> {
            if (delays.size() < 4) {
                delays.add(reconnector[0].nextDelay());
                reconnector[0].schedule();
            }
        });

        vertx.runOnContext(v -> {
            delays.add(reconnector[0].nextDelay());
            reconnector[0].schedule();
        });
        await().atMost(5, TimeUnit.SECONDS).until(() -> delays.size() == 4);

        assertEquals(List.of(10L, 20L, 35L, 35L), delays);
        reconnector[0].succeeded();
        assertEquals(10, reconnector[0].nextDelay());
    }

    @Test
    void anAttemptConnectsOnceAndNotAfterACancel() {
        List<Long> attempts = new CopyOnWriteArrayList<>();
        VertxMqttReconnector reconnector = new VertxMqttReconnector(vertx, BROKER, 10, 10, () -> attempts.add(1L));

        vertx.runOnContext(v -> {
            reconnector.schedule();
            // A second request while one is scheduled is the same attempt
            reconnector.schedule();
        });
        await().atMost(5, TimeUnit.SECONDS).until(() -> attempts.size() == 1);

        vertx.runOnContext(v -> {
            reconnector.schedule();
            reconnector.cancel();
            reconnector.schedule();
        });
        await().during(200, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS).until(() -> attempts.size() == 1);
    }
}
