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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.UnavailableException;
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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With synchronous pull, a pull that fails at once (credentials that cannot be loaded, an error the client library
 * returns without retrying it for long) must be attempted again only after the retry delay, as the asynchronous
 * consumer does, and not in a tight loop; once the error is gone the consumer must receive messages again.
 */
class GooglePubsubConsumerSyncPullRetryDelayTest {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final CamelContext context = new DefaultCamelContext();
    private final SettableApiFuture<PullResponse> pending = SettableApiFuture.create();
    private final AtomicBoolean failing = new AtomicBoolean(true);
    private final AtomicInteger attempts = new AtomicInteger();

    @AfterEach
    void tearDown() {
        pending.cancel(true);
        context.stop();
    }

    @Test
    void credentialsThatCannotBeLoadedAreRetriedAfterTheDelay() throws Exception {
        SubscriberStub stub = stub(request -> ApiFutures.immediateFuture(message()));
        startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) throws IOException {
                attempts.incrementAndGet();
                if (failing.get()) {
                    throw new FileNotFoundException("the service account key file was removed");
                }
                return stub;
            }
        });

        assertAttemptsBoundedThenReceives();
    }

    @Test
    void pullFailingAtOnceWithRetryableErrorIsRetriedAfterTheDelay() throws Exception {
        SubscriberStub stub = stub(request -> {
            attempts.incrementAndGet();
            if (failing.get()) {
                return ApiFutures.immediateFailedFuture(new UnavailableException(
                        "Pub/Sub unavailable", null, GrpcStatusCode.of(Status.Code.UNAVAILABLE), true));
            }
            return ApiFutures.immediateFuture(message());
        });
        startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                return stub;
            }
        });

        assertAttemptsBoundedThenReceives();
    }

    @Test
    void stopDuringTheRetryDelayEndsTheWait() throws Exception {
        startRoute(new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) throws IOException {
                attempts.incrementAndGet();
                throw new FileNotFoundException("the service account key file was removed");
            }
        }, Duration.ofMinutes(1));

        await().atMost(Duration.ofSeconds(10)).until(this::waitingForRetry);
        context.getRouteController().stopRoute("pubsub");

        // the loop does not wait the rest of the minute: it ends with the stop
        await().atMost(Duration.ofSeconds(5)).until(() -> !waitingForRetry());
        assertEquals(1, attempts.get());
    }

    private boolean waitingForRetry() {
        return PluginHelper.getTaskManagerRegistry(context.getCamelContextExtension()).getTasks().stream()
                .anyMatch(task -> "PubSubRetryDelay".equals(task.getName()));
    }

    private void assertAttemptsBoundedThenReceives() throws Exception {
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("hello");
        result.setResultWaitTime(10000);

        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 1);
        // one attempt per retry delay of 1 second, not one after the other: at most 3 more within 2 seconds
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).until(() -> attempts.get() <= 4);

        failing.set(false);
        result.assertIsSatisfied();
    }

    private void startRoute(GooglePubsubComponent component) throws Exception {
        startRoute(component, RETRY_DELAY);
    }

    private void startRoute(GooglePubsubComponent component, Duration retryDelay) throws Exception {
        context.addComponent("google-pubsub", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("google-pubsub:test-project:test-subscription?synchronousPull=true&maxDeliveryAttempts=5")
                        .routeId("pubsub").autoStartup(false)
                        .to("mock:result");
            }
        });
        context.start();
        ((GooglePubsubConsumer) context.getRoute("pubsub").getConsumer()).setRetryDelay(retryDelay);
        context.getRouteController().startRoute("pubsub");
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
            ApiFuture<PullResponse> response = firstPulls.answer(invocation.getArgument(0));
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
        ApiFuture<PullResponse> answer(PullRequest request);
    }
}
