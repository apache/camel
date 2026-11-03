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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The producer publishes what converts to a byte array, an empty payload for no body, and fails the exchange for a body
 * that does not convert, instead of publishing it as an empty payload.
 */
class HiveMQProducerBodyTest {

    private final List<byte[]> published = new CopyOnWriteArrayList<>();
    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = createContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("hivemq:test/topic");
            }
        });
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void textBodyIsPublished() {
        Exchange out = send("Hello");

        assertThat(out.getException()).isNull();
        assertThat(published).singleElement()
                .isEqualTo("Hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void noBodyIsPublishedAsEmptyPayload() {
        Exchange out = send(null);

        assertThat(out.getException()).isNull();
        assertThat(published).singleElement()
                .satisfies(payload -> assertThat(payload).isEmpty());
    }

    @Test
    void mapBodyFailsTheExchange() {
        Exchange out = send(Map.of("id", 1));

        assertThat(out.getException()).isInstanceOf(InvalidPayloadException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void numberBodyFailsTheExchange() {
        Exchange out = send(42);

        assertThat(out.getException()).isInstanceOf(InvalidPayloadException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void pojoBodyFailsTheExchange() {
        Exchange out = send(new Order("A-1"));

        assertThat(out.getException()).isInstanceOf(InvalidPayloadException.class);
        assertThat(published).isEmpty();
    }

    @Test
    void invalidQosHeaderFailsTheExchange() {
        Exchange out = context.createProducerTemplate().request("direct:start", e -> {
            e.getIn().setBody("Hello");
            e.getIn().setHeader(HiveMQConstants.MQTT_QOS, "NO_SUCH_QOS");
        });

        assertThat(out.getException()).isInstanceOf(TypeConversionException.class);
        assertThat(published).isEmpty();
    }

    private Exchange send(Object body) {
        return context.createProducerTemplate().request("direct:start", e -> e.getIn().setBody(body));
    }

    private CamelContext createContext() {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addComponent("hivemq", new HiveMQComponent() {
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
        return camelContext;
    }

    private record Order(String id) {
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
            published.add(payload);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Optional<T> getClient(Class<T> clazz) {
            return Optional.empty();
        }
    }
}
