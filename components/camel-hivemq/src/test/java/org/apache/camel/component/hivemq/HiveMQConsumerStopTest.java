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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client acknowledges a message when the consumer's callback returns, and the consumer then processes it on its own
 * thread pool: when the route stops, the messages the consumer already received must still be processed.
 */
class HiveMQConsumerStopTest {

    // released when the consumer shuts its thread pool down, so the first message is being processed at that time
    private final CountDownLatch poolShutdown = new CountDownLatch(1);
    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final FakeClient client = new FakeClient();
    private DefaultCamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        // a single consumer thread, so the second and third message wait in the queue of the pool
        camelContext.getExecutorServiceManager().setThreadPoolFactory(new SingleThreadPoolFactory());

        HiveMQComponent component = new HiveMQComponent();
        component.setCamelContext(camelContext);
        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, new HiveMQConfiguration(), "test") {
            @Override
            HiveMQClientAdapter createClient() {
                return client;
            }
        };
        endpoint.setCamelContext(camelContext);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpoint).routeId("hivemq")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("1".equals(body)) {
                                firstStarted.countDown();
                                assertThat(poolShutdown.await(20, TimeUnit.SECONDS)).isTrue();
                            }
                            processed.add(body);
                        });
            }
        });
        camelContext.start();
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    void receivedMessagesAreProcessedWhenTheRouteStops() throws Exception {
        client.deliver("1");
        client.deliver("2");
        client.deliver("3");
        assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue();

        camelContext.getRouteController().stopRoute("hivemq");

        assertThat(client.unsubscribed).isTrue();
        assertThat(processed).containsExactly("1", "2", "3");
    }

    private final class SingleThreadPoolFactory extends DefaultThreadPoolFactory {

        @Override
        public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory factory) {
            if (!Boolean.TRUE.equals(profile.isDefaultProfile())) {
                return super.newThreadPool(profile, factory);
            }
            return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory) {
                @Override
                public void shutdown() {
                    super.shutdown();
                    poolShutdown.countDown();
                }

                @Override
                public List<Runnable> shutdownNow() {
                    List<Runnable> notStarted = super.shutdownNow();
                    poolShutdown.countDown();
                    return notStarted;
                }
            };
        }
    }

    private static final class FakeClient implements HiveMQClientAdapter {

        private volatile Consumer<HiveMQMessage> callback;
        private volatile boolean unsubscribed;

        void deliver(String payload) {
            callback.accept(new HiveMQMessage(
                    "test", payload.getBytes(StandardCharsets.UTF_8), MqttQos.AT_LEAST_ONCE, false));
        }

        @Override
        public CompletableFuture<?> connect(boolean cleanStart) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void stop() {
            // noop
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
            this.callback = callback;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> unsubscribe(String topicFilter) {
            unsubscribed = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> publish(String topic, byte[] payload, MqttQos qos, boolean retained) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> Optional<T> getClient(Class<T> clazz) {
            return Optional.empty();
        }
    }
}
