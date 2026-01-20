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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.iggy.services.IggyService;
import org.apache.camel.test.infra.iggy.services.IggyServiceFactory;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.client.blocking.tcp.IggyTcpClient;
import org.apache.iggy.consumergroup.Consumer;
import org.apache.iggy.identifier.ConsumerId;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.Message;
import org.apache.iggy.message.Partitioning;
import org.apache.iggy.message.PollingStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class IggyTestBase {

    @Order(1)
    @RegisterExtension
    protected static IggyService iggyService = IggyServiceFactory.createService();

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    private static IggyBaseClient client;

    protected static final String TOPIC = "test-topic";
    protected static final String STREAM = "test-stream";
    protected static final String CONSUMER_GROUP = "test-consumer-group";

    @BeforeAll
    public static void setup() {
        client = new IggyTcpClient(iggyService.host(), iggyService.port());
        client.users().login(iggyService.username(), iggyService.password());
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected void sendMessage(String message) {
        client.messages().sendMessages(
                StreamId.of(STREAM),
                TopicId.of(TOPIC),
                Partitioning.balanced(),
                Collections.singletonList(Message.of(message)));
    }

    protected Stream<String> pollMessagesPayloadsAsStringFromCustomStreamTopic(String stream, String topic) {
        return pollMessages(stream, topic).stream().map(message -> new String(message.payload()));
    }

    protected Stream<String> pollMessagesPayloadsAsString() {
        return pollMessages(null, null).stream().map(message -> new String(message.payload()));
    }

    protected List<Message> pollMessages(String stream, String topic) {
        // Create consumer group if needed
        stream = stream == null ? STREAM : stream;
        topic = topic == null ? TOPIC : topic;
        try {
            client.consumerGroups().createConsumerGroup(
                    StreamId.of(stream),
                    TopicId.of(topic),
                    CONSUMER_GROUP);
        } catch (Exception e) {
            // already created
        }

        client.consumerGroups().joinConsumerGroup(
                StreamId.of(stream),
                TopicId.of(topic),
                ConsumerId.of(CONSUMER_GROUP));

        List<Message> polledMessages = client.messages()
                .pollMessages(
                        StreamId.of(stream),
                        TopicId.of(topic),
                        Optional.empty(),
                        Consumer.group(ConsumerId.of(CONSUMER_GROUP)),
                        PollingStrategy.next(),
                        1000L, // for safety, not all the tests pollMessages
                        true)
                .messages();

        return polledMessages;
    }

    protected abstract RoutesBuilder createRouteBuilder() throws Exception;
}
