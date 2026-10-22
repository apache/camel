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
package org.apache.camel.component.dapr.consumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.dapr.client.DaprPreviewClient;
import io.dapr.client.Subscription;
import io.dapr.client.SubscriptionListener;
import io.dapr.client.domain.CloudEvent;
import io.dapr.exceptions.DaprException;
import io.dapr.utils.TypeRef;
import io.grpc.Status;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Dapr client does not open the stream of a subscription again when it fails (it only does so when the stream
 * completes): the consumer must subscribe again, else it stays started and receives no further events.
 */
class DaprPubSubConsumerResubscribeTest extends CamelTestSupport {

    private final DaprPreviewClient previewClient = mock(DaprPreviewClient.class);
    private final List<SubscriptionListener<byte[]>> listeners = new CopyOnWriteArrayList<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Semaphore subscribed = new Semaphore(0);
    private final AtomicInteger failingSubscribes = new AtomicInteger();
    private final List<Throwable> handledExceptions = new CopyOnWriteArrayList<>();

    @BindToRegistry("daprPreviewClient")
    public DaprPreviewClient daprPreviewClient() {
        doAnswer(inv -> {
            if (failingSubscribes.getAndUpdate(n -> Math.max(n - 1, 0)) > 0) {
                throw new IllegalStateException("cannot subscribe");
            }
            Subscription subscription = mock(Subscription.class);
            listeners.add(inv.getArgument(2));
            subscriptions.add(subscription);
            subscribed.release();
            return subscription;
        }).when(previewClient)
                .subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class), any(TypeRef.class));
        return previewClient;
    }

    private ExceptionHandler recordingHandler() {
        return new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handledExceptions.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handledExceptions.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handledExceptions.add(exception);
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("dapr:pubSub?pubSubName=myPubSub&topic=myTopic").routeId("pubSub")
                        .to("mock:result");
            }
        };
    }

    @Test
    void subscribesAgainWhenTheStreamFails() throws Exception {
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        listeners.get(0).onError(streamFailure());

        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS),
                "the consumer must subscribe again after the stream failed");
        verify(subscriptions.get(0)).close();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        assertEquals(SubscriptionListener.Status.SUCCESS, listeners.get(1).onEvent(cloudEvent("hello")).block());
        mock.assertIsSatisfied();
        assertEquals("hello", new String(
                mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class),
                StandardCharsets.UTF_8));

        // and again when the new stream fails too
        listeners.get(1).onError(streamFailure());

        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS),
                "the consumer must subscribe again after the second stream failed");
        verify(subscriptions.get(1)).close();
        verify(previewClient, times(3)).subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class),
                any(TypeRef.class));
    }

    @Test
    void aFailureToSubscribeAgainGoesToTheExceptionHandlerAndIsTriedAgain() throws Exception {
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));
        // set on the consumer: DaprEndpoint.createConsumer does not apply the exceptionHandler endpoint option
        ((DaprPubSubConsumer) context.getRoute("pubSub").getConsumer()).setExceptionHandler(recordingHandler());
        failingSubscribes.set(1);

        listeners.get(0).onError(streamFailure());

        // the 2nd call throws, the 3rd one subscribes
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS),
                "the consumer must try again after subscribeToEvents failed");
        verify(previewClient, times(3)).subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class),
                any(TypeRef.class));
        assertEquals(1, handledExceptions.size());
        assertEquals("cannot subscribe", handledExceptions.get(0).getMessage());
    }

    @Test
    void theDelayDoublesWhileTheStreamFailsAndStartsAgainOnAnEvent() throws Exception {
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));
        DaprPubSubConsumer consumer = (DaprPubSubConsumer) context.getRoute("pubSub").getConsumer();
        assertEquals(1000, consumer.getResubscribeDelay());

        // the first attempt is after 1 s, the next one would be after 2 s
        listeners.get(0).onError(streamFailure());
        assertEquals(2000, consumer.getResubscribeDelay());
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        // the new stream fails too: 2 s, then 4 s
        listeners.get(1).onError(streamFailure());
        assertEquals(4000, consumer.getResubscribeDelay());
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        // an event shows that the stream works again: the next failure starts with 1 s
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        assertEquals(SubscriptionListener.Status.SUCCESS, listeners.get(2).onEvent(cloudEvent("hello")).block());
        mock.assertIsSatisfied();
        assertEquals(1000, consumer.getResubscribeDelay());
    }

    @Test
    void aLateErrorOfAReplacedSubscriptionIsIgnored() throws Exception {
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));
        listeners.get(0).onError(streamFailure());
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        // the old listener reports another error after the consumer subscribed again: the new subscription is kept
        listeners.get(0).onError(streamFailure());

        verify(previewClient, after(2000).times(2)).subscribeToEvents(anyString(), anyString(),
                any(SubscriptionListener.class), any(TypeRef.class));
        verify(subscriptions.get(1), never()).close();
    }

    @Test
    void anErrorThatDoesNotEndTheStreamKeepsTheSubscription() throws Exception {
        // control: the Dapr client also calls onError for an event it cannot convert, and the stream stays open
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        listeners.get(0).onError(DaprException.propagate(new IOException("cannot deserialize the event")));

        verify(previewClient, after(2000).times(1)).subscribeToEvents(anyString(), anyString(),
                any(SubscriptionListener.class), any(TypeRef.class));
        verify(subscriptions.get(0), never()).close();
    }

    @Test
    void stoppingTheRouteCancelsAPendingResubscribe() throws Exception {
        // control: a stream that failed just before the route is stopped is not subscribed again
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));

        listeners.get(0).onError(streamFailure());
        context.getRouteController().stopRoute("pubSub");

        verify(previewClient, after(2000).times(1)).subscribeToEvents(anyString(), anyString(),
                any(SubscriptionListener.class), any(TypeRef.class));

        // a start subscribes once
        context.getRouteController().startRoute("pubSub");
        assertTrue(subscribed.tryAcquire(10, TimeUnit.SECONDS));
        verify(previewClient, times(2)).subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class),
                any(TypeRef.class));
    }

    /**
     * What the Dapr client passes to the listener when the gRPC stream fails (Subscription$1.onError calls
     * listener.onError(DaprException.propagate(t)) with the status of the call), for example when the sidecar restarts.
     */
    private static RuntimeException streamFailure() {
        return DaprException.propagate(Status.UNAVAILABLE.withDescription("io exception").asRuntimeException());
    }

    @SuppressWarnings("unchecked")
    private static CloudEvent<byte[]> cloudEvent(String data) {
        CloudEvent<byte[]> cloudEvent = mock(CloudEvent.class);
        when(cloudEvent.getData()).thenReturn(data.getBytes(StandardCharsets.UTF_8));
        when(cloudEvent.getId()).thenReturn("myId");
        return cloudEvent;
    }
}
