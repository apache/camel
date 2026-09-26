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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A route failure used to leave the message neither acknowledged nor negatively acknowledged, so it was only
 * redelivered once the acknowledgement timeout expired (CAMEL-16073), and a failing acknowledgement was reported
 * without its cause.
 */
public class PulsarMessageListenerAcknowledgementTest extends CamelTestSupport {

    private static final String ENDPOINT_URI
            = "pulsar:persistent://public/default/camel-ack-test?subscriptionName=camel-subscription";

    private final MessageId messageId = mock(MessageId.class);

    @Test
    public void testSuccessfulExchangeIsAcknowledged() throws Exception {
        final Consumer<byte[]> pulsarConsumer = mock(Consumer.class);

        listener().received(pulsarConsumer, message("ok"));

        verify(pulsarConsumer, timeout(5000)).acknowledge(messageId);
        verify(pulsarConsumer, never()).negativeAcknowledge(messageId);
    }

    @Test
    public void testFailedExchangeIsNegativelyAcknowledged() throws Exception {
        final Consumer<byte[]> pulsarConsumer = mock(Consumer.class);
        final Message<byte[]> message = message("fail");

        listener().received(pulsarConsumer, message);

        // the Message overload, not the MessageId one: only that carries the redelivery count into
        // NegativeAcksTracker, so a configured negativeAckRedeliveryBackoff can escalate
        verify(pulsarConsumer, timeout(5000)).negativeAcknowledge(message);
        verify(pulsarConsumer, never()).negativeAcknowledge(messageId);
        verify(pulsarConsumer, never()).acknowledge(messageId);
    }

    @Test
    public void testManualAcknowledgementLeavesTheFailedMessageToTheRoute() throws Exception {
        final Consumer<byte[]> pulsarConsumer = mock(Consumer.class);
        final Message<byte[]> message = message("fail");

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        pulsarConsumer().setExceptionHandler(exceptionHandler);

        context.getEndpoint(ENDPOINT_URI, PulsarEndpoint.class).getPulsarConfiguration()
                .setAllowManualAcknowledgement(true);
        try {
            listener().received(pulsarConsumer, message);

            // wait for the callback to have run before asserting that nothing was sent to the broker
            assertTrue(exceptionHandler.latch.await(5, TimeUnit.SECONDS),
                    "the route failure should still be reported");
            verify(pulsarConsumer, never()).negativeAcknowledge(message);
            verify(pulsarConsumer, never()).negativeAcknowledge(messageId);
            verify(pulsarConsumer, never()).acknowledge(messageId);
        } finally {
            context.getEndpoint(ENDPOINT_URI, PulsarEndpoint.class).getPulsarConfiguration()
                    .setAllowManualAcknowledgement(false);
        }
    }

    @Test
    public void testAcknowledgeFailureIsReportedWithItsCause() throws Exception {
        final Consumer<byte[]> pulsarConsumer = mock(Consumer.class);
        final PulsarClientException failure = new PulsarClientException("cannot acknowledge");
        doThrow(failure).when(pulsarConsumer).acknowledge(messageId);

        final CapturingExceptionHandler exceptionHandler = new CapturingExceptionHandler();
        pulsarConsumer().setExceptionHandler(exceptionHandler);

        listener().received(pulsarConsumer, message("ok"));

        assertTrue(exceptionHandler.latch.await(5, TimeUnit.SECONDS), "the acknowledgement failure should be reported");
        assertNotNull(exceptionHandler.captured.get(), "the acknowledgement failure should carry its cause");
        assertEquals("cannot acknowledge", exceptionHandler.captured.get().getMessage());
    }

    private PulsarMessageListener listener() {
        return new PulsarMessageListener(context.getEndpoint(ENDPOINT_URI, PulsarEndpoint.class), pulsarConsumer());
    }

    private PulsarConsumer pulsarConsumer() {
        return (PulsarConsumer) context.getRoutes().get(0).getConsumer();
    }

    private Message<byte[]> message(String body) {
        final Message<byte[]> message = mock(Message.class);
        when(message.getValue()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(message.getMessageId()).thenReturn(messageId);
        when(message.getProperties()).thenReturn(Collections.emptyMap());
        return message;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        final ConsumerBuilder<byte[]> builder = mock(ConsumerBuilder.class, RETURNS_SELF);
        when(builder.subscribe()).thenReturn(mock(Consumer.class));

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
                from(ENDPOINT_URI)
                        .process(exchange -> {
                            if ("fail".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalStateException("simulated route failure");
                            }
                        })
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
