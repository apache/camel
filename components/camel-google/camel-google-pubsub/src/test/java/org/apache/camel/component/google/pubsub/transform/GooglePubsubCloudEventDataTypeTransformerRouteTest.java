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
package org.apache.camel.component.google.pubsub.transform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubConsumer;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.consumer.CamelMessageReceiver;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CloudEvent source of the messages of the google-pubsub consumer, when the transformer runs in another route, as
 * with the output data type of a Kamelet: that route gets a copy of the exchange with the headers of the consumer, and
 * its own from endpoint.
 */
class GooglePubsubCloudEventDataTypeTransformerRouteTest extends CamelTestSupport {

    private final SettableApiFuture<PullResponse> pending = SettableApiFuture.create();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        AtomicInteger pulls = new AtomicInteger();
        @SuppressWarnings("unchecked")
        UnaryCallable<PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
        when(pullCallable.futureCall(any(PullRequest.class))).thenAnswer(invocation -> {
            if (pulls.incrementAndGet() == 1) {
                return ApiFutures.immediateFuture(PullResponse.newBuilder()
                        .addReceivedMessages(received("1", "Hello"))
                        .addReceivedMessages(received("2", "World"))
                        .build());
            }
            // no more messages; cancelled when the test ends
            return pending;
        });
        @SuppressWarnings("unchecked")
        UnaryCallable<AcknowledgeRequest, Empty> acknowledgeCallable = mock(UnaryCallable.class);
        when(acknowledgeCallable.call(any(AcknowledgeRequest.class))).thenReturn(Empty.getDefaultInstance());

        SubscriberStub stub = mock(SubscriberStub.class);
        when(stub.pullCallable()).thenReturn(pullCallable);
        when(stub.acknowledgeCallable()).thenReturn(acknowledgeCallable);

        context.addComponent("google-pubsub", new GooglePubsubComponent() {
            @Override
            public SubscriberStub getSubscriberStub(GooglePubsubEndpoint googlePubsubEndpoint) {
                return stub;
            }
        });
        return context;
    }

    @AfterEach
    void cancelPull() {
        pending.cancel(true);
    }

    private static ReceivedMessage received(String id, String body) {
        return ReceivedMessage.newBuilder()
                .setAckId("ack-" + id)
                .setMessage(PubsubMessage.newBuilder().setMessageId(id).setData(ByteString.copyFromUtf8(body)))
                .build();
    }

    @Test
    void theSourceIsTheSubscriptionOfTheConsumer() throws Exception {
        // CAMEL-27101: the source was built from the ack id, so every event had a source of its own
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello", "World");
        result.expectedHeaderValuesReceivedInAnyOrder(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "1", "2");
        result.allMessages().header(GooglePubsubConstants.SUBSCRIPTION_NAME).isEqualTo("orders-sub");
        result.allMessages().header(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).isEqualTo("google.pubsub.orders-sub");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void theSourceIsTheSubscriptionOfAnAsynchronousPull() throws Exception {
        // the asynchronous pull, the default, sets no ack id header, so it got no source before
        GooglePubsubEndpoint endpoint = context.getEndpoint("google-pubsub:test-project:orders-sub",
                GooglePubsubEndpoint.class);
        List<Exchange> exchanges = new CopyOnWriteArrayList<>();
        GooglePubsubConsumer consumer = (GooglePubsubConsumer) endpoint.createConsumer(exchanges::add);
        CamelMessageReceiver receiver = new CamelMessageReceiver(consumer, endpoint, exchanges::add);

        receiver.receiveMessage(PubsubMessage.newBuilder().setMessageId("1").setData(ByteString.copyFromUtf8("Hello")).build(),
                mock(AckReplyConsumer.class));

        assertEquals(1, exchanges.size());
        Exchange exchange = exchanges.get(0);
        assertEquals("orders-sub", exchange.getMessage().getHeader(GooglePubsubConstants.SUBSCRIPTION_NAME));
        new GooglePubsubCloudEventDataTypeTransformer().transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals("google.pubsub.orders-sub", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // seda hands a copy of the exchange to the next route, as the sink of a Kamelet does
                from("google-pubsub:test-project:orders-sub?synchronousPull=true").to("seda:next");

                from("seda:next")
                        .transformDataType("google-pubsub:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
