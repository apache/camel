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

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.junit.jupiter.api.Test;

/**
 * With synchronous pull and ackMode=NONE, acknowledging an aggregated exchange acknowledges every grouped message.
 */
public class AggregatedManualAcknowledgementIT extends PubsubTestSupport {

    private static final String ACK_TOPIC = "aggregatedAckTopic";
    private static final String ACK_SUBSCRIPTION = "aggregatedAckSubscription";
    private static final String NACK_TOPIC = "aggregatedNackTopic";
    private static final String NACK_SUBSCRIPTION = "aggregatedNackSubscription";
    private static final String CONSUMER_OPTIONS
            = "?synchronousPull=true&ackMode=NONE&maxMessagesPerPoll=10&maxDeliveryAttempts=0";

    @EndpointInject("mock:ackReceived")
    private MockEndpoint ackReceived;

    @EndpointInject("mock:nackReceived")
    private MockEndpoint nackReceived;

    @Override
    public void createTopicSubscription() {
        createTopicSubscriptionPair(ACK_TOPIC, ACK_SUBSCRIPTION, 10);
        // a long ack deadline: a redelivery during the test can only come from the nack
        createTopicSubscriptionPair(NACK_TOPIC, NACK_SUBSCRIPTION, 600);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("google-pubsub:{{project.id}}:" + ACK_SUBSCRIPTION + CONSUMER_OPTIONS)
                        .routeId("ack").autoStartup(false)
                        .to("mock:ackReceived")
                        .aggregate(constant(true), new GroupedExchangeAggregationStrategy()).completionSize(3)
                        .process(exchange -> acknowledge(exchange).ack(exchange));

                from("google-pubsub:{{project.id}}:" + NACK_SUBSCRIPTION + CONSUMER_OPTIONS)
                        .routeId("nack").autoStartup(false)
                        .to("mock:nackReceived")
                        .aggregate(constant(true), new GroupedExchangeAggregationStrategy()).completionSize(3)
                        .process(exchange -> acknowledge(exchange).nack(exchange));
            }
        };
    }

    private static GooglePubsubAcknowledge acknowledge(Exchange aggregated) {
        List<Exchange> grouped = aggregated.getIn().getBody(List.class);
        return grouped.get(0).getIn().getHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE,
                GooglePubsubAcknowledge.class);
    }

    @Test
    public void ackOfAggregatedExchangeAcksEveryGroupedMessage() throws Exception {
        for (int i = 0; i < 3; i++) {
            template.sendBody("google-pubsub:{{project.id}}:" + ACK_TOPIC, "ack-" + i);
        }
        ackReceived.expectedBodiesReceivedInAnyOrder("ack-0", "ack-1", "ack-2");
        // the ack deadline of the subscription is 10 seconds: a message that is not acked is delivered again by then
        ackReceived.setAssertPeriod(15000);

        context.getRouteController().startRoute("ack");

        ackReceived.assertIsSatisfied();
    }

    @Test
    public void nackOfAggregatedExchangeNacksEveryGroupedMessage() throws Exception {
        for (int i = 0; i < 3; i++) {
            template.sendBody("google-pubsub:{{project.id}}:" + NACK_TOPIC, "nack-" + i);
        }
        // the 3 messages are delivered again right after the nack, long before their ack deadline
        nackReceived.expectedMinimumMessageCount(6);

        context.getRouteController().startRoute("nack");

        nackReceived.assertIsSatisfied();
        context.getRouteController().stopRoute("nack");
    }
}
