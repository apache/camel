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
package org.apache.camel.impl;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.PollingConsumerPollingStrategy;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventDrivenPollingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A receive() that waits for a message on an idle endpoint must not prevent the polling consumer from being stopped,
 * nor make other receive calls wait.
 */
class EventDrivenPollingConsumerStopTest extends ContextTestSupport {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<EventDrivenPollingConsumer> pollingConsumers = new CopyOnWriteArrayList<>();
    // whether the delegate consumer is between beforePoll and afterPoll
    private final AtomicBoolean polling = new AtomicBoolean();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        context.addComponent("idle", new IdleComponent());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // give the receive calls that are still waiting a message, so nothing stays blocked
        for (EventDrivenPollingConsumer pollingConsumer : pollingConsumers) {
            for (int i = 0; i < 5; i++) {
                pollingConsumer.process(pollingConsumer.getEndpoint().createExchange());
            }
        }
        executor.shutdownNow();
        super.tearDown();
    }

    @Override
    protected int getShutdownTimeout() {
        return 1;
    }

    @Test
    void testStopContextWhilePollEnrichWaits() throws Exception {
        // pollEnrich with the default timeout waits in receive() for a message that never comes
        Future<?> future = executor.submit(() -> template.sendBody("direct:start", "Hello"));
        awaitReceiveWaiting();

        // the forced shutdown stops the pollEnrich consumer cache, and so the polling consumer
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> context.stop());

        // and the waiting receive() returns, so the exchange completes
        await().atMost(10, TimeUnit.SECONDS).until(future::isDone);
    }

    @Test
    void testStopConsumerTemplateWhileReceiveWaits() throws Exception {
        ConsumerTemplate consumerTemplate = context.createConsumerTemplate();
        Future<Exchange> future = executor.submit(() -> consumerTemplate.receive("idle:foo"));
        awaitReceiveWaiting();

        assertTimeoutPreemptively(Duration.ofSeconds(10), consumerTemplate::stop);

        // the waiting receive() returns as the polling consumer is stopped
        assertNull(future.get(10, TimeUnit.SECONDS));
    }

    @Test
    void testReceiveNoWaitWhileReceiveWaits() throws Exception {
        ConsumerTemplate consumerTemplate = context.createConsumerTemplate();
        Future<Exchange> future = executor.submit(() -> consumerTemplate.receive("idle:foo"));
        awaitReceiveWaiting();

        assertTrue(polling.get());

        // the polling consumer is shared, and the other receive calls must not wait for the receive() in progress
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertNull(consumerTemplate.receiveNoWait("idle:foo")));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertNull(consumerTemplate.receive("idle:foo", 100)));
        // and they must not invoke afterPoll while the receive() is still in progress
        assertTrue(polling.get());

        // the waiting receive() still gets the next message
        EventDrivenPollingConsumer pollingConsumer = pollingConsumers.get(0);
        Exchange exchange = pollingConsumer.getEndpoint().createExchange();
        exchange.getMessage().setBody("Bye");
        pollingConsumer.process(exchange);
        assertEquals("Bye", future.get(10, TimeUnit.SECONDS).getMessage().getBody());
        assertFalse(polling.get());

        consumerTemplate.stop();
    }

    private void awaitReceiveWaiting() {
        await().atMost(10, TimeUnit.SECONDS).until(EventDrivenPollingConsumerStopTest::isReceiveWaiting);
    }

    private static boolean isReceiveWaiting() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread.State state = entry.getKey().getState();
            if (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
                continue;
            }
            for (StackTraceElement element : entry.getValue()) {
                if (EventDrivenPollingConsumer.class.getName().equals(element.getClassName())
                        && "receive".equals(element.getMethodName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").pollEnrich("idle:enrich").to("mock:result");
            }
        };
    }

    /**
     * An endpoint that never has a message, and uses the default {@link EventDrivenPollingConsumer}.
     */
    private final class IdleComponent extends DefaultComponent {

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new IdleEndpoint(uri, this);
        }
    }

    private final class IdleEndpoint extends DefaultEndpoint {

        private IdleEndpoint(String endpointUri, Component component) {
            super(endpointUri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new IdleConsumer(this, processor);
        }

        @Override
        public PollingConsumer createPollingConsumer() throws Exception {
            PollingConsumer answer = super.createPollingConsumer();
            pollingConsumers.add((EventDrivenPollingConsumer) answer);
            return answer;
        }
    }

    private final class IdleConsumer extends DefaultConsumer implements PollingConsumerPollingStrategy {

        private IdleConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        public void onInit() {
            // noop
        }

        @Override
        public long beforePoll(long timeout) {
            polling.set(true);
            return timeout;
        }

        @Override
        public void afterPoll() {
            polling.set(false);
        }
    }
}
