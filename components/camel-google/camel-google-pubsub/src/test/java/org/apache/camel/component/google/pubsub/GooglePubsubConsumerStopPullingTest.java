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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Once the consumer stops pulling (deferShutdown during a graceful shutdown, or a stop), a subscriber loop must not
 * subscribe or pull again when its subscriber terminates or its pull is cancelled: not while the consumer waits for its
 * pending exchanges, and not after the consumer was started again (route restart), which starts its own loops.
 */
class GooglePubsubConsumerStopPullingTest {

    private final GooglePubsubEndpoint endpoint = mock();
    private final GooglePubsubComponent component = mock();
    private final Processor processor = mock();
    private final CamelContext context = mock();
    private final ExtendedCamelContext ecc = mock();
    private final ExchangeFactory ef = mock();
    private final ExecutorServiceManager executorServiceManager = mock();

    private final List<ExecutorService> executors = new CopyOnWriteArrayList<>();
    // subscribers created and subscribers between awaitRunning and the end of awaitTerminated
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    // the first subscriber completes its termination (stream close, ack flush) only when this is released
    private final CountDownLatch firstTermination = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(ef);
        when(ef.newExchangeFactory(any())).thenReturn(ef);
        when(context.getExecutorServiceManager()).thenReturn(executorServiceManager);
        when(executorServiceManager.newSingleThreadScheduledExecutor(any(), anyString())).thenAnswer(invocation -> {
            ScheduledExecutorService taskExecutor = Executors.newSingleThreadScheduledExecutor();
            executors.add(taskExecutor);
            return taskExecutor;
        });

        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.createExecutor(any())).thenAnswer(invocation -> {
            ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
            executors.add(consumerExecutor);
            return consumerExecutor;
        });
        when(endpoint.getConcurrentConsumers()).thenReturn(1);
        when(endpoint.getMaxMessagesPerPoll()).thenReturn(1);
        when(endpoint.getProjectId()).thenReturn("test-project");
        when(endpoint.getDestinationName()).thenReturn("test-subscription");
        when(endpoint.isMaxDeliveryAttemptsExplicitlySet()).thenReturn(true);
        when(endpoint.getMaxDeliveryAttempts()).thenReturn(0);

        when(component.getSubscriber(anyString(), any(), any()))
                .thenAnswer(invocation -> newSubscriber(created.incrementAndGet() == 1));
    }

    @AfterEach
    void tearDown() {
        firstTermination.countDown();
        executors.forEach(ExecutorService::shutdownNow);
    }

    private Subscriber newSubscriber(boolean first) {
        Subscriber subscriber = mock();
        CountDownLatch stopRequested = new CountDownLatch(1);
        when(subscriber.startAsync()).thenReturn(subscriber);
        doAnswer(invocation -> {
            running.incrementAndGet();
            return null;
        }).when(subscriber).awaitRunning();
        doAnswer(invocation -> {
            stopRequested.countDown();
            return subscriber;
        }).when(subscriber).stopAsync();
        doAnswer(invocation -> {
            stopRequested.await();
            if (first) {
                firstTermination.await();
            }
            running.decrementAndGet();
            return null;
        }).when(subscriber).awaitTerminated();
        return subscriber;
    }

    @Test
    void asyncPullDoesNotSubscribeAgainAfterDeferShutdown() throws Exception {
        firstTermination.countDown();
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);
        consumer.start();
        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> running.get() == 1);

            // graceful shutdown: the consumer stays started while the shutdown strategy waits for its pending exchanges
            consumer.deferShutdown(ShutdownRunningTask.CompleteCurrentTaskOnly);
            // the subscriber ends, and the loop does not start another one while the shutdown waits
            await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(1, created.get(), "subscribers created after the consumer stopped pulling");
                assertEquals(0, running.get(), "subscribers running while the shutdown waits for the pending exchanges");
            });
        } finally {
            consumer.stop();
        }
    }

    @Test
    void asyncPullLoopOfPreviousStartEndsWhenConsumerIsRestarted() throws Exception {
        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);
        consumer.start();
        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> running.get() == 1);

            // route restart: stop, then start again before the first subscriber has terminated
            ExecutorService firstConsumerExecutor = executors.get(0);
            consumer.stop();
            consumer.start();
            await().atMost(5, TimeUnit.SECONDS).until(() -> created.get() == 2);
            // the executor of the first start is released, and the scheduler of the retry delay is kept
            verify(executorServiceManager).shutdown(firstConsumerExecutor);
            verify(executorServiceManager, times(1)).newSingleThreadScheduledExecutor(any(), anyString());

            firstTermination.countDown();
            // the loop of the first start ends once its subscriber terminated, and does not subscribe again
            await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(2, created.get(), "subscribers created by a start and a restart with one concurrent consumer");
                assertEquals(1, running.get(), "subscribers running after the restart");
            });
        } finally {
            consumer.stop();
        }
    }

    @Test
    void syncPullDoesNotPullAgainAfterDeferShutdown() throws Exception {
        when(endpoint.isSynchronousPull()).thenReturn(true);
        AtomicInteger pulls = new AtomicInteger();
        SubscriberStub subscriberStub = mock();
        @SuppressWarnings("unchecked")
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(subscriberStub.pullCallable()).thenReturn(pullCallable);
        doAnswer(invocation -> {
            pulls.incrementAndGet();
            // a pull that waits for messages until it is cancelled
            return SettableApiFuture.<PullResponse> create();
        }).when(pullCallable).futureCall(any(PullRequest.class));
        when(component.getSubscriberStub(any())).thenReturn(subscriberStub);

        GooglePubsubConsumer consumer = new GooglePubsubConsumer(endpoint, processor);
        consumer.start();
        try {
            await().atMost(5, TimeUnit.SECONDS).until(() -> pulls.get() == 1);

            consumer.deferShutdown(ShutdownRunningTask.CompleteCurrentTaskOnly);
            await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(1, pulls.get(), "pulls issued after the consumer stopped pulling"));
        } finally {
            consumer.stop();
        }
    }
}
