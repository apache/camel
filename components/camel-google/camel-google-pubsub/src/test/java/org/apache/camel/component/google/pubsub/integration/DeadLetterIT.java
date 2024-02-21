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
package org.apache.camel.component.google.pubsub.integration;

import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.Topic;
import com.google.pubsub.v1.TopicName;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

public class DeadLetterIT extends PubsubTestSupport {

    private static final String INPUT_TOPIC_NAME = "camel.input-topic";
    private static final String INPUT_SUBSCRIPTION_NAME = "camel.input-topic-subscription";
    private static final String OUTPUT_TOPIC_NAME = "camel.output-topic";
    private static final String OUTPUT_SUBSCRIPTION_NAME = "camel.output-topic-subscription";
    private static final String DEAD_LETTER_TOPIC_NAME = "camel.dead-letter-topic";
    private static final String DEAD_LETTER_SUBSCRIPTION_NAME = "camel.dead-letter-topic-subscription";
    private static int count = 1;

    @EndpointInject("google-pubsub:{{project.id}}:" + INPUT_SUBSCRIPTION_NAME)
    private Endpoint inputPubSubSubscription;

    @EndpointInject("google-pubsub:{{project.id}}:" + OUTPUT_TOPIC_NAME)
    private Endpoint outputPubsubTopic;

    @EndpointInject("google-pubsub:{{project.id}}:" + OUTPUT_SUBSCRIPTION_NAME)
    private Endpoint outputPubsubSubscription;

    @EndpointInject("google-pubsub:{{project.id}}:" + DEAD_LETTER_SUBSCRIPTION_NAME)
    private Endpoint deadLetterPubsubSubscription;

    @EndpointInject("mock:input")
    private MockEndpoint inputMock;

    @EndpointInject("mock:dead-letter")
    private MockEndpoint deadLetterMock;

    @EndpointInject("mock:output")
    private MockEndpoint outputMock;

    @Produce("google-pubsub:{{project.id}}:" + INPUT_TOPIC_NAME)
    private ProducerTemplate producer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(inputPubSubSubscription)
                        .routeId("receiver")
                        .to(inputMock)
                        .process(e -> {
                            if (count < 3) {
                                count = ++count;
                                throw new Exception("Redeliver please");
                            }
                        })
                        .to(outputPubsubTopic);

                from(outputPubsubSubscription)
                        .routeId("output")
                        .to(outputMock);

                from(deadLetterPubsubSubscription)
                        .routeId("dead-letter")
                        .to(deadLetterMock);
            }
        };
    }

    @Override
    public void createTopicSubscription() {
        TopicName projectInputTopicName = TopicName.of(PROJECT_ID, INPUT_TOPIC_NAME);
        TopicName projectOutputTopicName = TopicName.of(PROJECT_ID, OUTPUT_TOPIC_NAME);
        TopicName projectDeadLetterTopicName = TopicName.of(PROJECT_ID, DEAD_LETTER_TOPIC_NAME);
        ProjectSubscriptionName projectInputSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, INPUT_SUBSCRIPTION_NAME);
        ProjectSubscriptionName projectOutputSubscriptionName
                = ProjectSubscriptionName.of(PROJECT_ID, OUTPUT_SUBSCRIPTION_NAME);
        ProjectSubscriptionName projectDeadLetterSubscriptionName
                = ProjectSubscriptionName.of(PROJECT_ID, DEAD_LETTER_SUBSCRIPTION_NAME);

        Topic inputTopic = Topic.newBuilder().setName(projectInputTopicName.toString()).build();
        Topic outputTopic = Topic.newBuilder().setName(projectOutputTopicName.toString()).build();
        Topic deadLetterTopic = Topic.newBuilder().setName(projectDeadLetterTopicName.toString()).build();
        Subscription inputSubscription = Subscription.newBuilder()
                .setName(projectInputSubscriptionName.toString())
                .setTopic(inputTopic.getName())
                .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                        .setDeadLetterTopic(deadLetterTopic.getName())
                        .setMaxDeliveryAttempts(5).build())
                .build();
        Subscription deadLetterSubscription = Subscription.newBuilder()
                .setName(projectDeadLetterSubscriptionName.toString())
                .setTopic(deadLetterTopic.getName())
                .build();
        Subscription outputSubscription = Subscription.newBuilder()
                .setName(projectOutputSubscriptionName.toString())
                .setTopic(outputTopic.getName())
                .build();

        createTopicSubscriptionPair(deadLetterTopic, deadLetterSubscription);
        createTopicSubscriptionPair(inputTopic, inputSubscription);
        createTopicSubscriptionPair(outputTopic, outputSubscription);
    }

    /**
     * Expecting the route to, on the third attempt, send the message to PubSub without the "googclient_deliveryattempt"
     * attribute. This attribute is set when a message gets redelivered, but it is not allowed to be set when sending.
     * The the PubSub emulator currently doesn't support dead letter topics so this test is only representative when run
     * against the Google Cloud PubSub.
     *
     * @throws InterruptedException
     */
    @Test
    public void redeliverAndSend() throws InterruptedException {

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(exchange.getExchangeId());

        inputMock.expectedMessageCount(3);
        deadLetterMock.expectedMessageCount(0);
        outputMock.expectedMessageCount(1);

        producer.send(exchange);

        outputMock.assertIsSatisfied(2000);
        inputMock.assertIsSatisfied();
        deadLetterMock.assertIsSatisfied();
    }
}
