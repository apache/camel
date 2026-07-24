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
package org.apache.camel.processor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PollingConsumerSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PollEnrichConcurrentConsumersCacheTest extends ContextTestSupport {
    private static final long TIMEOUT_SECONDS = 10;

    private final Map<String, PollState> states = new ConcurrentHashMap<>();
    private final List<PollingConsumer> consumers = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpConsumers() {
        states.values().forEach(PollState::release);
        ServiceHelper.stopAndShutdownServices(consumers);
    }

    @Test
    void concurrentDynamicPollsStopEvictedConsumer() throws Exception {
        PollState a = registerState("a", true);
        PollState b = registerState("b", false);
        context.addComponent("cachetest", pollingComponent());

        Future<String> resultA
                = template.asyncRequestBodyAndHeader("seda:start", "trigger-a", "pollUri", "cachetest:a", String.class);
        assertThat(a.getEnteredReceive().await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("consumer A entered receive")
                .isTrue();

        Future<String> resultB
                = template.asyncRequestBodyAndHeader("seda:start", "trigger-b", "pollUri", "cachetest:b", String.class);
        assertThat(b.getEnteredReceive().await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("consumer B entered receive")
                .isTrue();

        assertThat(resultB.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("b");
        a.release();
        assertThat(resultA.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("a");

        assertThat(a.getCreated()).hasValue(1);
        assertThat(a.getReceived()).hasValue(1);
        assertThat(b.getCreated()).hasValue(1);
        assertThat(b.getReceived()).hasValue(1);
        assertThat(b.getStopped()).as("cached consumer B remains started").hasValue(0);
        assertThat(a.getStopped()).as("evicted consumer A is stopped when it is released").hasValue(1);
    }

    @Test
    void sequentialDynamicPollsStopIdleConsumer() throws Exception {
        PollState a = registerState("a", false);
        PollState b = registerState("b", false);
        context.addComponent("cachetest", pollingComponent());

        String resultA = template.requestBodyAndHeader("seda:start", "trigger-a", "pollUri", "cachetest:a", String.class);

        assertThat(resultA).isEqualTo("a");
        assertThat(a.getCreated()).hasValue(1);
        assertThat(a.getReceived()).hasValue(1);
        assertThat(a.getStopped()).as("consumer A is idle in its pool").hasValue(0);

        String resultB = template.requestBodyAndHeader("seda:start", "trigger-b", "pollUri", "cachetest:b", String.class);

        assertThat(resultB).isEqualTo("b");
        assertThat(b.getCreated()).hasValue(1);
        assertThat(b.getReceived()).hasValue(1);
        assertThat(a.getStopped()).as("idle consumer A is stopped during eviction").hasValue(1);
        assertThat(b.getStopped()).as("cached consumer B remains started").hasValue(0);
    }

    private PollState registerState(String name, boolean blockReceive) {
        PollState state = new PollState(blockReceive);
        states.put(name, state);
        return state;
    }

    private DefaultComponent pollingComponent() {
        return new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                PollState state = states.get(remaining);
                if (state == null) {
                    throw new IllegalArgumentException("No polling state registered for " + remaining);
                }

                return new DefaultEndpoint(uri, this) {
                    @Override
                    public boolean isSingletonProducer() {
                        return false;
                    }

                    @Override
                    public Producer createProducer() {
                        return null;
                    }

                    @Override
                    public Consumer createConsumer(Processor processor) {
                        return null;
                    }

                    @Override
                    public PollingConsumer createPollingConsumer() {
                        state.getCreated().incrementAndGet();
                        PollingConsumerSupport consumer = new PollingConsumerSupport(this) {
                            @Override
                            public Exchange receive() {
                                return poll();
                            }

                            @Override
                            public Exchange receive(long timeout) {
                                return poll();
                            }

                            @Override
                            public Exchange receiveNoWait() {
                                return poll();
                            }

                            private Exchange poll() {
                                state.getEnteredReceive().countDown();
                                try {
                                    if (!state.getAllowReceiveToFinish().await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timed out waiting to finish polling " + remaining);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeCamelException(e);
                                }

                                state.getReceived().incrementAndGet();
                                Exchange exchange = getEndpoint().createExchange();
                                exchange.getMessage().setBody(remaining);
                                return exchange;
                            }

                            @Override
                            protected void doStop() {
                                state.getStopped().incrementAndGet();
                            }
                        };
                        consumers.add(consumer);
                        return consumer;
                    }
                };
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start?concurrentConsumers=4")
                        .pollEnrich()
                        .header("pollUri")
                        .timeout(5000)
                        .cacheSize(1);
            }
        };
    }

    private static final class PollState {
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger received = new AtomicInteger();
        private final AtomicInteger stopped = new AtomicInteger();
        private final CountDownLatch enteredReceive = new CountDownLatch(1);
        private final CountDownLatch allowReceiveToFinish;

        private PollState(boolean blockReceive) {
            this.allowReceiveToFinish = new CountDownLatch(blockReceive ? 1 : 0);
        }

        private void release() {
            allowReceiveToFinish.countDown();
        }

        public AtomicInteger getCreated() {
            return created;
        }

        public AtomicInteger getReceived() {
            return received;
        }

        public AtomicInteger getStopped() {
            return stopped;
        }

        public CountDownLatch getEnteredReceive() {
            return enteredReceive;
        }

        public CountDownLatch getAllowReceiveToFinish() {
            return allowReceiveToFinish;
        }
    }
}
