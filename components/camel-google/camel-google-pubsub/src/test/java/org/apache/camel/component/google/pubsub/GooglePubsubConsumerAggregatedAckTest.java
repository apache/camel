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
import java.util.function.BiConsumer;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.consumer.AcknowledgeSync;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With synchronous pull and ackMode=NONE, acknowledging an exchange aggregated from the consumed ones (a
 * {@code List<Exchange>} body) must acknowledge every grouped message, each with its own ack id.
 */
public class GooglePubsubConsumerAggregatedAckTest {

    private static final String SUBSCRIPTION = "projects/test-project/subscriptions/test-subscription";

    private final List<AcknowledgeRequest> ackRequests = new CopyOnWriteArrayList<>();
    private final List<ModifyAckDeadlineRequest> modifyAckDeadlineRequests = new CopyOnWriteArrayList<>();
    private final SettableApiFuture<PullResponse> pending = SettableApiFuture.create();

    @Test
    void ackOfAggregatedExchangeAcksEveryGroupedMessage() throws Exception {
        routeAggregatedExchange(GooglePubsubAcknowledge::ack);

        assertEquals(1, ackRequests.size());
        assertEquals(List.of("ack-1", "ack-2", "ack-3"), ackRequests.get(0).getAckIdsList());
        assertEquals(SUBSCRIPTION, ackRequests.get(0).getSubscription());
        assertTrue(modifyAckDeadlineRequests.isEmpty());
    }

    @Test
    void nackOfAggregatedExchangeNacksEveryGroupedMessage() throws Exception {
        routeAggregatedExchange(GooglePubsubAcknowledge::nack);

        assertEquals(1, modifyAckDeadlineRequests.size());
        ModifyAckDeadlineRequest nackRequest = modifyAckDeadlineRequests.get(0);
        assertEquals(List.of("ack-1", "ack-2", "ack-3"), nackRequest.getAckIdsList());
        assertEquals(0, nackRequest.getAckDeadlineSeconds());
        assertEquals(SUBSCRIPTION, nackRequest.getSubscription());
        assertTrue(ackRequests.isEmpty());
    }

    @Test
    void ackIdOfAggregatedExchangeThatIsAlsoGroupedIsSentOnce() {
        // an aggregation strategy may keep the first grouped exchange as the aggregated one
        CamelContext context = new DefaultCamelContext();
        Exchange aggregated = exchange(context, "ack-1");
        aggregated.getIn().setBody(List.of(aggregated, exchange(context, "ack-2"), exchange(context, "ack-3")));

        new AcknowledgeSync(this::stub, SUBSCRIPTION).ack(aggregated);

        assertEquals(1, ackRequests.size());
        assertEquals(List.of("ack-1", "ack-2", "ack-3"), ackRequests.get(0).getAckIdsList());
    }

    @Test
    void ackOfSingleExchangeAcksItsOwnMessage() {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = exchange(context, "ack-1");
        exchange.getIn().setBody("hello".getBytes());

        new AcknowledgeSync(this::stub, SUBSCRIPTION).ack(exchange);

        assertEquals(1, ackRequests.size());
        assertEquals(List.of("ack-1"), ackRequests.get(0).getAckIdsList());
    }

    /**
     * One pull returns three messages, the route aggregates them into one exchange and acknowledges that exchange with
     * the acknowledge header of the first grouped exchange.
     */
    private void routeAggregatedExchange(BiConsumer<GooglePubsubAcknowledge, Exchange> acknowledgement) throws Exception {
        SubscriberStub stub = stub();
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
                    from("google-pubsub:test-project:test-subscription?synchronousPull=true&ackMode=NONE"
                         + "&maxMessagesPerPoll=10&maxDeliveryAttempts=0")
                            .aggregate(constant(true), new GroupedExchangeAggregationStrategy()).completionSize(3)
                            .process(exchange -> {
                                List<Exchange> grouped = exchange.getIn().getBody(List.class);
                                GooglePubsubAcknowledge acknowledge = grouped.get(0).getIn().getHeader(
                                        GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE, GooglePubsubAcknowledge.class);
                                acknowledgement.accept(acknowledge, exchange);
                            })
                            .to("mock:acknowledged");
                }
            });
            MockEndpoint acknowledged = context.getEndpoint("mock:acknowledged", MockEndpoint.class);
            acknowledged.expectedMessageCount(1);

            context.start();

            acknowledged.assertIsSatisfied();
        } finally {
            pending.cancel(true);
            context.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private SubscriberStub stub() {
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(pullCallable.futureCall(any(PullRequest.class)))
                .thenReturn(ApiFutures.immediateFuture(PullResponse.newBuilder()
                        .addReceivedMessages(message("ack-1", "one"))
                        .addReceivedMessages(message("ack-2", "two"))
                        .addReceivedMessages(message("ack-3", "three"))
                        .build()))
                // no more messages; cancelled when the test ends
                .thenReturn(pending);

        UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable = mock(UnaryCallable.class);
        when(acknowledgeCallable.call(any(AcknowledgeRequest.class))).thenAnswer(invocation -> {
            ackRequests.add(invocation.getArgument(0));
            return Empty.getDefaultInstance();
        });

        UnaryCallable<ModifyAckDeadlineRequest, Empty> modifyAckDeadlineCallable = mock(UnaryCallable.class);
        when(modifyAckDeadlineCallable.call(any(ModifyAckDeadlineRequest.class))).thenAnswer(invocation -> {
            modifyAckDeadlineRequests.add(invocation.getArgument(0));
            return Empty.getDefaultInstance();
        });

        SubscriberStub stub = mock(SubscriberStub.class);
        when(stub.pullCallable()).thenReturn(pullCallable);
        when(stub.acknowledgeCallable()).thenReturn(acknowledgeCallable);
        when(stub.modifyAckDeadlineCallable()).thenReturn(modifyAckDeadlineCallable);
        return stub;
    }

    private static ReceivedMessage message(String ackId, String body) {
        return ReceivedMessage.newBuilder()
                .setAckId(ackId)
                .setMessage(PubsubMessage.newBuilder()
                        .setMessageId(ackId)
                        .setData(ByteString.copyFromUtf8(body)))
                .build();
    }

    private static Exchange exchange(CamelContext context, String ackId) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(GooglePubsubConstants.ACK_ID, ackId);
        return exchange;
    }
}
