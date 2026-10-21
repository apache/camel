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

import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.grpc.GrpcStatusCode;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.api.gax.rpc.UnavailableException;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import io.grpc.Status;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With synchronous pull, a failure to nack a message that reached maxDeliveryAttempts must not end the pull loop: the
 * consumer must keep receiving the next messages.
 */
public class GooglePubsubConsumerNackFailureTest {

    @Test
    void failedNackOfMessageAtMaxDeliveryAttemptsDoesNotStopPolling() throws Exception {
        AtomicInteger pulls = new AtomicInteger();
        SettableApiFuture<PullResponse> pending = SettableApiFuture.create();

        @SuppressWarnings("unchecked")
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(pullCallable.futureCall(any(PullRequest.class))).thenAnswer(invocation -> {
            int pull = pulls.incrementAndGet();
            if (pull == 1) {
                // a poison message that reached the max delivery attempts: nacked without routing
                return ApiFutures.immediateFuture(response("ack-1", "poison", 3));
            } else if (pull == 2) {
                return ApiFutures.immediateFuture(response("ack-2", "hello", 1));
            }
            // no more messages; cancelled when the consumer stops
            return pending;
        });

        @SuppressWarnings("unchecked")
        UnaryCallable<ModifyAckDeadlineRequest, Empty> modifyAckDeadlineCallable = mock(UnaryCallable.class);
        when(modifyAckDeadlineCallable.call(any(ModifyAckDeadlineRequest.class)))
                .thenThrow(new UnavailableException(
                        "Pub/Sub unavailable", null, GrpcStatusCode.of(Status.Code.UNAVAILABLE), true));

        @SuppressWarnings("unchecked")
        UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable = mock(UnaryCallable.class);
        when(acknowledgeCallable.call(any(AcknowledgeRequest.class))).thenReturn(Empty.getDefaultInstance());

        SubscriberStub stub = mock(SubscriberStub.class);
        when(stub.pullCallable()).thenReturn(pullCallable);
        when(stub.modifyAckDeadlineCallable()).thenReturn(modifyAckDeadlineCallable);
        when(stub.acknowledgeCallable()).thenReturn(acknowledgeCallable);

        GooglePubsubComponent component = new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint googlePubsubEndpoint) {
                return stub;
            }
        };

        CamelContext context = new DefaultCamelContext();
        try {
            context.addComponent("google-pubsub", component);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("google-pubsub:test-project:test-subscription?synchronousPull=true&maxDeliveryAttempts=3")
                            .routeId("pubsub")
                            .to("mock:result");
                }
            });
            MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
            result.expectedBodiesReceived("hello");
            result.setResultWaitTime(10000);

            context.start();

            result.assertIsSatisfied();
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("pubsub"));
        } finally {
            pending.cancel(true);
            context.stop();
        }
    }

    private static PullResponse response(String ackId, String body, int deliveryAttempt) {
        return PullResponse.newBuilder()
                .addReceivedMessages(ReceivedMessage.newBuilder()
                        .setAckId(ackId)
                        .setDeliveryAttempt(deliveryAttempt)
                        .setMessage(PubsubMessage.newBuilder()
                                .setMessageId(ackId)
                                .setData(ByteString.copyFromUtf8(body))))
                .build();
    }
}
