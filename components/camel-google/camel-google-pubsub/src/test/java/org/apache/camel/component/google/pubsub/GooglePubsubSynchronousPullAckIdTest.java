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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With synchronousPull and the default ackMode, the consumer acks or nacks the message it received with the ack id it
 * received it with, whatever the route did with the {@link GooglePubsubConstants#ACK_ID} header.
 */
class GooglePubsubSynchronousPullAckIdTest extends CamelTestSupport {

    private static final String SUBSCRIPTION = "projects/test-project/subscriptions/test-subscription";
    private static final String URI = "google-pubsub:test-project:test-subscription"
                                      + "?synchronousPull=true&concurrentConsumers=1&maxMessagesPerPoll=1"
                                      + "&maxDeliveryAttempts=0";

    // the messages the next pulls return, one per pull; a pull with none left waits until it is cancelled, as a pull
    // with returnImmediately=false waits for a message
    private final Queue<ReceivedMessage> toDeliver = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<String> acked = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<String> nacked = new LinkedBlockingQueue<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GooglePubsubComponent component = new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint endpoint) {
                return new FakeSubscriberStub();
            }
        };
        component.setAuthenticate(false);
        context.addComponent("google-pubsub", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(URI).routeId("removeHeaders").autoStartup(false)
                        .removeHeaders("*")
                        .to("mock:result");

                from(URI).routeId("changeHeader").autoStartup(false)
                        .setHeader(GooglePubsubConstants.ACK_ID, constant("not-the-received-ack-id"))
                        .to("mock:result");

                from(URI).routeId("removeHeadersAndFail").autoStartup(false)
                        .removeHeaders("*")
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }

    @Test
    void acksTheReceivedMessageWhenTheRouteRemovedTheHeaders() throws Exception {
        toDeliver.add(received("ack-1", "m1"));
        context.getRouteController().startRoute("removeHeaders");

        assertEquals("ack-1", acked.poll(10, TimeUnit.SECONDS));
        context.getRouteController().stopRoute("removeHeaders");
        assertEquals(List.of(), List.copyOf(nacked));
        assertEquals(List.of(), List.copyOf(acked));
    }

    @Test
    void acksTheReceivedMessageWhenTheRouteChangedTheHeader() throws Exception {
        toDeliver.add(received("ack-1", "m1"));
        context.getRouteController().startRoute("changeHeader");

        assertEquals("ack-1", acked.poll(10, TimeUnit.SECONDS));
        context.getRouteController().stopRoute("changeHeader");
        assertEquals(List.of(), List.copyOf(acked));
    }

    @Test
    void nacksTheReceivedMessageWhenTheRouteRemovedTheHeadersAndFailed() throws Exception {
        toDeliver.add(received("ack-1", "m1"));
        context.getRouteController().startRoute("removeHeadersAndFail");

        assertEquals("ack-1", nacked.poll(10, TimeUnit.SECONDS));
        context.getRouteController().stopRoute("removeHeadersAndFail");
        assertEquals(List.of(), List.copyOf(acked));
    }

    @Test
    void acksEachMessageWithItsOwnAckId() throws Exception {
        toDeliver.add(received("ack-1", "m1"));
        toDeliver.add(received("ack-2", "m2"));
        context.getRouteController().startRoute("removeHeaders");

        await().atMost(10, TimeUnit.SECONDS).until(() -> acked.size() >= 2);
        context.getRouteController().stopRoute("removeHeaders");
        assertEquals(List.of("ack-1", "ack-2"), List.copyOf(acked));
    }

    private static ReceivedMessage received(String ackId, String messageId) {
        return ReceivedMessage.newBuilder()
                .setAckId(ackId)
                .setMessage(PubsubMessage.newBuilder().setMessageId(messageId)
                        .setData(ByteString.copyFromUtf8("body of " + messageId)))
                .build();
    }

    /**
     * The stub the consumer gets from {@link GooglePubsubComponent#getSubscriberStub}: Pull answers with at most one
     * message (fewer than max_messages, as the server may), Acknowledge and ModifyAckDeadline (0 seconds is the nack of
     * the consumer) record the ack ids of the request.
     */
    private final class FakeSubscriberStub extends SubscriberStub {

        @Override
        public UnaryCallable<PullRequest, PullResponse> pullCallable() {
            return new UnaryCallable<>() {
                @Override
                public ApiFuture<PullResponse> futureCall(PullRequest request, ApiCallContext context) {
                    assertEquals(SUBSCRIPTION, request.getSubscription());
                    ReceivedMessage next = toDeliver.poll();
                    if (next == null) {
                        // cancelled by the consumer when it stops
                        return SettableApiFuture.create();
                    }
                    return ApiFutures.immediateFuture(PullResponse.newBuilder().addReceivedMessages(next).build());
                }
            };
        }

        @Override
        public UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable() {
            return new UnaryCallable<>() {
                @Override
                public ApiFuture<Empty> futureCall(AcknowledgeRequest request, ApiCallContext context) {
                    assertEquals(SUBSCRIPTION, request.getSubscription());
                    acked.addAll(request.getAckIdsList());
                    return ApiFutures.immediateFuture(Empty.getDefaultInstance());
                }
            };
        }

        @Override
        public UnaryCallable<ModifyAckDeadlineRequest, Empty> modifyAckDeadlineCallable() {
            return new UnaryCallable<>() {
                @Override
                public ApiFuture<Empty> futureCall(ModifyAckDeadlineRequest request, ApiCallContext context) {
                    assertEquals(SUBSCRIPTION, request.getSubscription());
                    if (request.getAckDeadlineSeconds() == 0) {
                        nacked.addAll(request.getAckIdsList());
                    }
                    return ApiFutures.immediateFuture(Empty.getDefaultInstance());
                }
            };
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public void shutdown() {
            // nothing to release
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public void shutdownNow() {
            // nothing to release
        }

        @Override
        public boolean awaitTermination(long duration, TimeUnit unit) {
            return true;
        }
    }
}
