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
package org.apache.camel.component.hivemq;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two toD to hivemq in the same exchange must publish to two topics: the topic header that the toD optimisation sets
 * for the first send must not be used by the second send.
 */
class HiveMQToDTwiceTest {

    private final List<String> published = new CopyOnWriteArrayList<>();

    @Test
    void secondToDPublishesToItsOwnTopic() throws Exception {
        try (CamelContext context = createContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .toD("hivemq:${header.first}")
                            .toD("hivemq:${header.second}");
                }
            });
            context.start();

            context.createProducerTemplate().sendBodyAndHeaders("direct:start", "Hello",
                    Map.of("first", "status/1", "second", "audit/1"));

            assertThat(published).containsExactly("status/1", "audit/1");
        }
    }

    @Test
    void loopWithToDPublishesEachIterationToItsOwnTopic() throws Exception {
        try (CamelContext context = createContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:loop")
                            .loop(3)
                            .toD("hivemq:devices/${exchangeProperty.CamelLoopIndex}/cmd")
                            .end();
                }
            });
            context.start();

            context.createProducerTemplate().sendBody("direct:loop", "Hello");

            assertThat(published).containsExactly("devices/0/cmd", "devices/1/cmd", "devices/2/cmd");
        }
    }

    private CamelContext createContext() {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("hivemq", new HiveMQComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                    throws Exception {
                HiveMQEndpoint endpoint = new HiveMQEndpoint(uri, this, getConfiguration().copy(), remaining) {
                    @Override
                    HiveMQClientAdapter createClient() {
                        return new RecordingClient();
                    }
                };
                setProperties(endpoint, parameters);
                return endpoint;
            }
        });
        return context;
    }

    private final class RecordingClient implements HiveMQClientAdapter {
        @Override
        public CompletableFuture<?> connect(boolean cleanStart) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isConnectedOrReconnecting() {
            return true;
        }

        @Override
        public CompletableFuture<?> subscribe(String topicFilter, MqttQos qos, Consumer<HiveMQMessage> callback) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> unsubscribe(String topicFilter) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> publish(String topic, byte[] payload, MqttQos qos, boolean retained) {
            published.add(topic);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Optional<T> getClient(Class<T> clazz) {
            return Optional.empty();
        }
    }
}
