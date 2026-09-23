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
package org.apache.camel.component.pulsar;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A receive error used to be reported through the endpoint exception handler, which is null unless the route configures
 * one, so the polling loop died on a NullPointerException and that consumer stopped consuming in silence.
 */
public class PulsarConsumerReceiveErrorTest extends CamelTestSupport {

    private static final String ROUTE_ID = "pulsar-receive-error";

    private final CountDownLatch blockReceive = new CountDownLatch(1);
    private final CountDownLatch secondReceive = new CountDownLatch(1);
    private final AtomicInteger receiveCalls = new AtomicInteger();

    @Test
    public void testReceiveErrorIsHandledAndTheLoopSurvives() throws Exception {
        final PulsarConsumer consumer = (PulsarConsumer) context.getRoute(ROUTE_ID).getConsumer();

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        consumer.setExceptionHandler(exceptionHandler);

        context.getRouteController().startRoute(ROUTE_ID);

        assertTrue(exceptionHandler.latch.await(10, TimeUnit.SECONDS),
                "the receive error should be handed to the consumer exception handler");
        assertNotNull(exceptionHandler.captured.get(), "the failure cause should be reported");
        assertEquals("simulated receive failure", exceptionHandler.captured.get().getMessage());

        // the loop must still be polling: the first call failed, so a second one proves it did not die
        assertTrue(secondReceive.await(10, TimeUnit.SECONDS),
                "the polling loop should have continued after the error, receive calls: " + receiveCalls.get());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        final Consumer<byte[]> pulsarConsumer = mock(Consumer.class);
        when(pulsarConsumer.receive()).thenAnswer(invocation -> {
            if (receiveCalls.incrementAndGet() == 1) {
                throw new PulsarClientException("simulated receive failure");
            }
            secondReceive.countDown();
            // hold the loop here until the route is stopped, the same way a quiet topic would
            try {
                blockReceive.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // what the client reports once the consumer thread is interrupted on shutdown
            throw new PulsarClientException(new InterruptedException());
        });

        final ConsumerBuilder<byte[]> builder = mock(ConsumerBuilder.class, RETURNS_SELF);
        when(builder.subscribe()).thenReturn(pulsarConsumer);

        final PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newConsumer()).thenReturn(builder);

        final PulsarComponent component = new PulsarComponent(context);
        component.setPulsarClient(pulsarClient);
        context.addComponent("pulsar", component);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // started by the test, so that the exception handler is in place before the loop runs;
                // no exceptionHandler option on the endpoint, which is exactly the case that used to NPE
                from("pulsar:persistent://public/default/camel-receive-error"
                     + "?messageListener=false&subscriptionName=camel-subscription")
                        .routeId(ROUTE_ID).autoStartup(false)
                        .to("mock:result");
            }
        };
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Throwable> captured = new AtomicReference<>();

        @Override
        public void handleException(Throwable exception) {
            handleException(null, null, exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(message, null, exception);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            captured.compareAndSet(null, exception);
            latch.countDown();
        }
    }
}
