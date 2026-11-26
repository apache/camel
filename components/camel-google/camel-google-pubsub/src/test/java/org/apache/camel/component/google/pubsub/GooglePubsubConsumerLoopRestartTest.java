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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.ApiService;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import io.grpc.Status;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A failure that ends a pull or a subscription (a non-retryable error, credentials that cannot be loaded, an Error of a
 * client library that does not match) must not end the subscriber loop: the loop subscribes or pulls again after the
 * retry delay, and the health check of the consumer is DOWN with the failure until it gets messages again.
 */
class GooglePubsubConsumerLoopRestartTest {

    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final CamelContext context = new DefaultCamelContext();
    // a logger of its own, so the log lines of other tests running at the same time are not captured
    private final String loggerId = "pubsub-loop-restart-" + UUID.randomUUID();
    private final LogEventCaptor captor = new LogEventCaptor();
    private final SettableApiFuture<PullResponse> pending = SettableApiFuture.create();
    private final AtomicBoolean failing = new AtomicBoolean(true);
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void tearDown() {
        pending.cancel(true);
        context.stop();
        ((Logger) LogManager.getLogger(loggerId)).removeAppender(captor);
    }

    @Test
    void syncPullFailingWithNonRetryableErrorIsPulledAgain() throws Exception {
        SubscriberStub stub = stub(() -> failing.get()
                ? ApiFutures.immediateFailedFuture(new PermissionDeniedException(
                        "the IAM binding is being changed", null, GrpcStatusCode.of(Status.Code.PERMISSION_DENIED), false))
                : ApiFutures.immediateFuture(message()));
        GooglePubsubConsumer consumer = startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                return stub;
            }
        }, "synchronousPull=true", RETRY_DELAY);

        // the loop pulls again after the retry delay, and the consumer is DOWN meanwhile
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 2);
        HealthCheck.Result down = consumer.getHealthCheck().call();
        assertThat(down.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(down.getError()).containsInstanceOf(PermissionDeniedException.class);
        assertThat(down.getDetails()).containsEntry("failing.loops", 1);

        failing.set(false);
        assertReceivedAndUp(consumer);
        // the first failure in a row with its stack trace at ERROR, the next ones in one WARN line
        assertThat(captor.events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrown()).isInstanceOf(PermissionDeniedException.class);
        });
        assertThat(captor.events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getMessage().getFormattedMessage()).contains("failed attempts in a row");
        });
    }

    @Test
    void syncPullFailingWithAnErrorIsLoggedAndPulledAgain() throws Exception {
        SubscriberStub stub = stub(() -> ApiFutures.immediateFuture(message()));
        GooglePubsubConsumer consumer = startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                if (failing.get()) {
                    attempts.incrementAndGet();
                    // a client library that does not match the one the component was built with
                    throw new NoClassDefFoundError("io/grpc/internal/SomethingRemoved");
                }
                return stub;
            }
        }, "synchronousPull=true", RETRY_DELAY);

        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(captor.events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrown()).isInstanceOf(NoClassDefFoundError.class);
        }));
        assertThat(consumer.getHealthCheck().call().getState()).isEqualTo(HealthCheck.State.DOWN);

        failing.set(false);
        assertReceivedAndUp(consumer);
    }

    @Test
    void asyncSubscriberThatCannotBeCreatedOrFailsAfterTheStartIsSubscribedAgain() throws Exception {
        List<CountDownLatch> stops = new CopyOnWriteArrayList<>();
        GooglePubsubConsumer consumer = startRoute(new GooglePubsubComponent() {
            @Override
            public Subscriber getSubscriber(
                    String subscriptionName, MessageReceiver messageReceiver, GooglePubsubEndpoint endpoint)
                    throws IOException {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    // credentials that cannot be loaded at that moment
                    throw new FileNotFoundException("the service account key file was removed");
                }
                // the subscriber of the second attempt fails right after it started (the subscription is created
                // again), the next ones work once the failure is gone
                try {
                    return subscriber(messageReceiver, stops, attempt == 2 || failing.get());
                } catch (TimeoutException e) {
                    // not thrown by the stubbing
                    throw new IllegalStateException(e);
                }
            }
        }, "bridgeErrorHandler=true", RETRY_DELAY);

        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 3);
        HealthCheck.Result down = consumer.getHealthCheck().call();
        assertThat(down.getState()).isEqualTo(HealthCheck.State.DOWN);
        // the failure of the subscriber, not the IllegalStateException of awaitTerminated
        assertThat(down.getError()).containsInstanceOf(NotFoundException.class);
        // with bridgeErrorHandler, the failures go to the error handler of the route
        MockEndpoint errors = context.getEndpoint("mock:error", MockEndpoint.class);
        assertThat(errors.getReceivedExchanges()).anySatisfy(
                exchange -> assertThat(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class))
                        .isInstanceOf(FileNotFoundException.class));

        failing.set(false);
        assertReceivedAndUp(consumer);
    }

    @Test
    void asyncStopDuringTheRetryDelayEndsTheWait() throws Exception {
        startRoute(new GooglePubsubComponent() {
            @Override
            public Subscriber getSubscriber(
                    String subscriptionName, MessageReceiver messageReceiver, GooglePubsubEndpoint endpoint)
                    throws IOException {
                attempts.incrementAndGet();
                throw new FileNotFoundException("the service account key file was removed");
            }
        }, "", Duration.ofMinutes(1));

        await().atMost(Duration.ofSeconds(10)).until(this::waitingForRetry);
        context.getRouteController().stopRoute("pubsub", 10, TimeUnit.SECONDS);

        // the loop does not wait the rest of the minute: it ends with the stop
        await().atMost(Duration.ofSeconds(5)).until(() -> !waitingForRetry());
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void virtualMachineErrorStopsTheConsumerUntilTheRouteIsRestarted() throws Exception {
        GooglePubsubConsumer consumer = startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                attempts.incrementAndGet();
                throw new OutOfMemoryError("thrown by the test");
            }
        }, "synchronousPull=true", RETRY_DELAY);

        await().atMost(Duration.ofSeconds(10)).until(() -> consumer.getFatalError() != null);
        HealthCheck.Result down = consumer.getHealthCheck().call();
        assertThat(down.getState()).isEqualTo(HealthCheck.State.DOWN);
        assertThat(down.getError()).containsInstanceOf(OutOfMemoryError.class);
        // the loop does not pull again after the retry delay
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> attempts.get() == 1);
        assertThat(captor.events).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrown()).isInstanceOf(OutOfMemoryError.class);
        });
    }

    @Test
    void healthCheckIsReadinessOnly() throws Exception {
        GooglePubsubConsumer consumer = startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                return stub(() -> pending);
            }
        }, "synchronousPull=true", RETRY_DELAY);

        HealthCheck check = consumer.getHealthCheck();
        assertThat(check.getId()).isEqualTo("consumer:google-pubsub-pubsub");
        assertThat(check.isReadiness()).isTrue();
        assertThat(check.isLiveness()).isFalse();
        assertThat(check.call().getState()).isEqualTo(HealthCheck.State.UP);
    }

    private void assertReceivedAndUp(GooglePubsubConsumer consumer) throws InterruptedException {
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("hello");
        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> consumer.getHealthCheck().call().getState() == HealthCheck.State.UP);
        assertThat(captor.events).anySatisfy(event -> assertThat(event.getMessage().getFormattedMessage())
                .contains("again after"));
    }

    private boolean waitingForRetry() {
        return PluginHelper.getTaskManagerRegistry(context.getCamelContextExtension()).getTasks().stream()
                .anyMatch(task -> "PubSubRetryDelay".equals(task.getName()));
    }

    private GooglePubsubConsumer startRoute(GooglePubsubComponent component, String options, Duration retryDelay)
            throws Exception {
        ((Logger) LogManager.getLogger(loggerId)).addAppender(captor);
        context.addComponent("google-pubsub", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("google-pubsub:test-project:test-subscription?maxDeliveryAttempts=5&loggerId=" + loggerId
                     + (options.isEmpty() ? "" : "&" + options))
                        .routeId("pubsub").autoStartup(false)
                        .to("mock:result");
            }
        });
        context.start();
        GooglePubsubConsumer consumer = (GooglePubsubConsumer) context.getRoute("pubsub").getConsumer();
        consumer.setRetryDelay(retryDelay);
        context.getRouteController().startRoute("pubsub");
        return consumer;
    }

    /**
     * An asynchronous subscriber: one that fails right after the start, or one that delivers a message and runs until
     * it is stopped.
     */
    private Subscriber subscriber(MessageReceiver receiver, List<CountDownLatch> stops, boolean fails)
            throws TimeoutException {
        Subscriber subscriber = mock(Subscriber.class);
        CountDownLatch stopped = new CountDownLatch(1);
        stops.add(stopped);
        when(subscriber.startAsync()).thenReturn(subscriber);
        doAnswer(invocation -> {
            stopped.countDown();
            return subscriber;
        }).when(subscriber).stopAsync();
        if (fails) {
            NotFoundException failure = new NotFoundException(
                    "the subscription is being created again", null, GrpcStatusCode.of(Status.Code.NOT_FOUND), false);
            when(subscriber.state()).thenReturn(ApiService.State.FAILED);
            when(subscriber.failureCause()).thenReturn(failure);
            IllegalStateException terminated = new IllegalStateException("Expected the service to be TERMINATED", failure);
            doAnswer(invocation -> {
                throw terminated;
            }).when(subscriber).awaitTerminated();
            doAnswer(invocation -> {
                throw terminated;
            }).when(subscriber).awaitTerminated(anyLong(), any(TimeUnit.class));
        } else {
            when(subscriber.state()).thenReturn(ApiService.State.RUNNING);
            doAnswer(invocation -> {
                receiver.receiveMessage(PubsubMessage.newBuilder().setMessageId("m1")
                        .setData(ByteString.copyFromUtf8("hello")).build(), mock(AckReplyConsumer.class));
                return null;
            }).when(subscriber).awaitRunning();
            doAnswer(invocation -> {
                stopped.await();
                return null;
            }).when(subscriber).awaitTerminated();
            doAnswer(invocation -> {
                if (!stopped.await(invocation.getArgument(0), invocation.getArgument(1))) {
                    throw new TimeoutException();
                }
                return null;
            }).when(subscriber).awaitTerminated(anyLong(), any(TimeUnit.class));
        }
        return subscriber;
    }

    @SuppressWarnings("unchecked")
    private SubscriberStub stub(PullAnswer firstPulls) {
        AtomicBoolean received = new AtomicBoolean();
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(pullCallable.futureCall(any(PullRequest.class))).thenAnswer(invocation -> {
            if (received.get()) {
                // no more messages; cancelled when the consumer stops
                return pending;
            }
            attempts.incrementAndGet();
            ApiFuture<PullResponse> response = firstPulls.answer();
            if (!failing.get()) {
                received.set(true);
            }
            return response;
        });
        UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable = mock(UnaryCallable.class);
        when(acknowledgeCallable.call(any(AcknowledgeRequest.class))).thenReturn(Empty.getDefaultInstance());

        SubscriberStub stub = mock(SubscriberStub.class);
        when(stub.pullCallable()).thenReturn(pullCallable);
        when(stub.acknowledgeCallable()).thenReturn(acknowledgeCallable);
        return stub;
    }

    private static PullResponse message() {
        return PullResponse.newBuilder()
                .addReceivedMessages(ReceivedMessage.newBuilder()
                        .setAckId("ack-1")
                        .setMessage(PubsubMessage.newBuilder()
                                .setMessageId("m1")
                                .setData(ByteString.copyFromUtf8("hello"))))
                .build();
    }

    @FunctionalInterface
    private interface PullAnswer {
        ApiFuture<PullResponse> answer();
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("LoopRestartLogEventCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
