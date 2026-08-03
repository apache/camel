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
package org.apache.camel.component.google.pubsub;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A subscriber or pull that is still starting when the consumer stops must still be stopped or cancelled, otherwise the
 * consumer thread parks forever.
 */
public class GooglePubsubConsumerStopRaceTest {

    private final GooglePubsubEndpoint endpoint = mock();
    private final GooglePubsubComponent component = mock();
    private final Processor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();
    private final ExecutorServiceManager executorServiceManager = mock();

    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService taskExecutor = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void setUp() throws Exception {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(context.getExecutorServiceManager()).thenReturn(executorServiceManager);
        when(executorServiceManager.newSingleThreadScheduledExecutor(any(), anyString())).thenReturn(taskExecutor);

        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.createExecutor(any())).thenReturn(consumerExecutor);
        when(endpoint.getConcurrentConsumers()).thenReturn(1);
        when(endpoint.getMaxMessagesPerPoll()).thenReturn(1);
        when(endpoint.getProjectId()).thenReturn("test-project");
        when(endpoint.getDestinationName()).thenReturn("test-subscription");
        when(endpoint.isMaxDeliveryAttemptsExplicitlySet()).thenReturn(true);
        when(endpoint.getMaxDeliveryAttempts()).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        consumerExecutor.shutdownNow();
        taskExecutor.shutdownNow();
    }

    @Test
    void subscriberStillStartingWhenConsumerStopsIsStopped() throws Exception {
        CountDownLatch enteredAwaitRunning = new CountDownLatch(1);
        CountDownLatch startupGate = new CountDownLatch(1);
        CountDownLatch stopAsyncCalled = new CountDownLatch(1);

        Subscriber subscriber = mock();
        when(subscriber.startAsync()).thenReturn(subscriber);
        doAnswer(invocation -> {
            enteredAwaitRunning.countDown();
            startupGate.await();
            return null;
        }).when(subscriber).awaitRunning();
        doAnswer(invocation -> {
            stopAsyncCalled.countDown();
            return subscriber;
        }).when(subscriber).stopAsync();
        doAnswer(invocation -> {
            stopAsyncCalled.await(10, TimeUnit.SECONDS);
            return null;
        }).when(subscriber).awaitTerminated();
        when(component.getSubscriber(anyString(), any(), any())).thenReturn(subscriber);

        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);
        consumer.start();
        try {
            assertTrue(enteredAwaitRunning.await(5, TimeUnit.SECONDS), "subscriber never started");

            // the subscriber is not yet in the consumer's list, so stopping the consumer misses it
            consumer.stop();
            startupGate.countDown();

            assertTrue(stopAsyncCalled.await(5, TimeUnit.SECONDS),
                    "subscriber that finished starting after the consumer stopped was never stopped");
        } finally {
            startupGate.countDown();
            consumer.stop();
        }
    }

    @Test
    void pullStillBeingIssuedWhenConsumerStopsIsCancelled() throws Exception {
        CountDownLatch enteredPull = new CountDownLatch(1);
        CountDownLatch pullGate = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        SettableApiFuture<PullResponse> pullResponseFuture = SettableApiFuture.create();
        pullResponseFuture.addListener(cancelled::countDown, Runnable::run);

        when(endpoint.isSynchronousPull()).thenReturn(true);
        SubscriberStub subscriberStub = mock();
        @SuppressWarnings("unchecked")
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(subscriberStub.pullCallable()).thenReturn(pullCallable);
        doAnswer(invocation -> {
            enteredPull.countDown();
            pullGate.await();
            return pullResponseFuture;
        }).when(pullCallable).futureCall(any(PullRequest.class));
        when(component.getSubscriberStub(any())).thenReturn(subscriberStub);

        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);
        consumer.start();
        try {
            assertTrue(enteredPull.await(5, TimeUnit.SECONDS), "pull was never issued");

            // the pull future is not yet in the consumer's pending set, so stopping the consumer misses it
            consumer.stop();
            pullGate.countDown();

            assertTrue(cancelled.await(5, TimeUnit.SECONDS),
                    "pull issued while the consumer stopped was never cancelled");
        } finally {
            pullGate.countDown();
            pullResponseFuture.cancel(true);
            consumer.stop();
        }
    }
}
