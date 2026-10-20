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
package org.apache.camel.component.iggy;

import java.io.Closeable;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.component.iggy.client.IggyClientFactory;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.iggy.client.blocking.ConsumerGroupsClient;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.consumergroup.ConsumerGroupDetails;
import org.apache.iggy.exception.IggyErrorCode;
import org.apache.iggy.exception.IggyResourceNotFoundException;
import org.apache.iggy.identifier.ConsumerId;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.Message;
import org.apache.iggy.message.PolledMessages;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * The consumer group membership belongs to the connection of a client: the Iggy server answers a poll of a client that
 * did not join the group with ConsumerGroupMemberNotFound. After a failed poll the consumer discards its client and the
 * pool creates a new one, which must join the group before it polls. The Iggy clients are mocks that behave like the
 * server in this respect; no Iggy server is needed.
 */
class IggyConsumerRejoinGroupTest {

    private static final String URI
            = "iggy:topic?streamName=stream&autoCreateStream=false&autoCreateTopic=false&consumerGroupName=group";

    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<IggyBaseClient> clients = new CopyOnWriteArrayList<>();

    // how the first client fails its first poll
    private boolean firstClientReconnectedOnItsOwn;

    @Test
    void testNewClientJoinsTheGroupAfterAFailedPoll() throws Exception {
        consumeAfterTheFirstClientFailed();
    }

    @Test
    void testNewClientJoinsTheGroupAfterTheClientReconnectedOnItsOwn() throws Exception {
        // the iggy 0.9.0 TCP client replaces a lost connection and logs in again by itself (a channel pool of one
        // connection that replays the login), but does not join the consumer group again: the server answers its next
        // poll with ConsumerGroupMemberNotFound (5006), although the consumer saw it join
        firstClientReconnectedOnItsOwn = true;
        consumeAfterTheFirstClientFailed();
    }

    private void consumeAfterTheFirstClientFailed() throws Exception {
        try (MockedConstruction<IggyClientFactory> factories = mockConstruction(IggyClientFactory.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS),
                (factory, context) -> doAnswer(invocation -> newClient()).when(factory).create());
             CamelContext context = new DefaultCamelContext()) {
            context.start();
            Consumer consumer = context.getEndpoint(URI).createConsumer(exchange -> {
                received.add(exchange.getIn().getBody(String.class));
            });
            consumer.start();

            // the first client loses its connection at its first poll; the next client must consume
            await().atMost(20, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertEquals(List.of("after the failed poll"), received));

            consumer.stop();
        }
    }

    private IggyBaseClient newClient() throws Exception {
        boolean first = clients.isEmpty();
        AtomicBoolean member = new AtomicBoolean();
        AtomicBoolean delivered = new AtomicBoolean();
        IggyBaseClient client = mock(IggyBaseClient.class,
                withSettings().extraInterfaces(Closeable.class).defaultAnswer(RETURNS_DEEP_STUBS));
        clients.add(client);

        // the group exists; joining makes this client (its connection) a member
        ConsumerGroupDetails group = mock(ConsumerGroupDetails.class);
        ConsumerGroupsClient groups = client.consumerGroups();
        when(groups.getConsumerGroup(any(StreamId.class), any(TopicId.class), any(ConsumerId.class)))
                .thenReturn(Optional.of(group));
        doAnswer(invocation -> {
            member.set(true);
            return null;
        }).when(groups).joinConsumerGroup(any(StreamId.class), any(TopicId.class), any(ConsumerId.class));

        when(client.messages().pollMessages(any(StreamId.class), any(TopicId.class), any(), any(), any(), any(),
                anyBoolean())).thenAnswer(invocation -> {
                    if (first && firstClientReconnectedOnItsOwn) {
                        throw memberNotFound();
                    }
                    if (first) {
                        // any failure of the poll discards the client (iggy throws IggyConnectionException and others)
                        throw new IllegalStateException("Connection reset");
                    }
                    if (!member.get()) {
                        throw memberNotFound();
                    }
                    if (delivered.compareAndSet(false, true)) {
                        return new PolledMessages(
                                0L, BigInteger.ZERO, 1L, List.of(Message.of("after the failed poll")));
                    }
                    return new PolledMessages(0L, BigInteger.ZERO, 0L, List.of());
                });
        return client;
    }

    private static IggyResourceNotFoundException memberNotFound() {
        return new IggyResourceNotFoundException(
                IggyErrorCode.CONSUMER_GROUP_MEMBER_NOT_FOUND, 5006, "consumer_group_member_not_found",
                Optional.empty(), Optional.empty());
    }
}
