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
import org.apache.camel.component.google.pubsub.GooglePubsubConsumer;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaxDeliveryAttemptsIT extends PubsubTestSupport {

    private static final String TOPIC_NAME = "camel.max-delivery-topic";
    private static final String SUBSCRIPTION_NAME = "camel.max-delivery-subscription";
    private static final String DLQ_TOPIC_NAME = "camel.max-delivery-dlq-topic";
    private static final String DLQ_SUBSCRIPTION_NAME = "camel.max-delivery-dlq-subscription";
    private static final int MAX_DELIVERY_ATTEMPTS = 5;

    // Consumer without explicit maxDeliveryAttempts - should auto-fetch from subscription
    @EndpointInject("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME)
    private Endpoint autoFetchSubscription;

    @EndpointInject("mock:auto-fetch-result")
    private MockEndpoint autoFetchResult;

    @Produce("google-pubsub:{{project.id}}:" + TOPIC_NAME)
    private ProducerTemplate producer;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(autoFetchSubscription)
                        .routeId("auto-fetch-consumer")
                        .to(autoFetchResult);
            }
        };
    }

    @Override
    public void createTopicSubscription() {
        TopicName projectTopicName = TopicName.of(PROJECT_ID, TOPIC_NAME);
        TopicName projectDlqTopicName = TopicName.of(PROJECT_ID, DLQ_TOPIC_NAME);
        ProjectSubscriptionName projectSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_NAME);
        ProjectSubscriptionName projectDlqSubscriptionName = ProjectSubscriptionName.of(PROJECT_ID, DLQ_SUBSCRIPTION_NAME);

        Topic topic = Topic.newBuilder().setName(projectTopicName.toString()).build();
        Topic dlqTopic = Topic.newBuilder().setName(projectDlqTopicName.toString()).build();

        Subscription subscription = Subscription.newBuilder()
                .setName(projectSubscriptionName.toString())
                .setTopic(topic.getName())
                .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                        .setDeadLetterTopic(dlqTopic.getName())
                        .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS).build())
                .build();
        Subscription dlqSubscription = Subscription.newBuilder()
                .setName(projectDlqSubscriptionName.toString())
                .setTopic(dlqTopic.getName())
                .build();

        createTopicSubscriptionPair(dlqTopic, dlqSubscription);
        createTopicSubscriptionPair(topic, subscription);
    }

    /**
     * Tests that the consumer auto-fetches maxDeliveryAttempts from the subscription's dead-letter policy at startup
     * and that messages are processed normally when the delivery attempt is below the threshold. On first delivery the
     * delivery attempt is either not set or 1, which is below maxDeliveryAttempts=5.
     */
    @Test
    public void testAutoFetchMaxDeliveryAttemptsAndProcessBelowThreshold() throws Exception {
        // Verify auto-fetch resolved the correct value
        GooglePubsubConsumer consumer
                = (GooglePubsubConsumer) context.getRoute("auto-fetch-consumer").getConsumer();
        assertEquals(MAX_DELIVERY_ATTEMPTS, consumer.getResolvedMaxDeliveryAttempts(),
                "Consumer should auto-fetch maxDeliveryAttempts from subscription dead-letter policy");

        // Verify messages are processed normally when below the threshold
        autoFetchResult.expectedMessageCount(1);

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("test message");
        producer.send(exchange);

        autoFetchResult.assertIsSatisfied(5000);
    }
}
