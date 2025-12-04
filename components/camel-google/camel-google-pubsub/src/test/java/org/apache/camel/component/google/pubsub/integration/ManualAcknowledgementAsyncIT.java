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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.google.pubsub.consumer.GooglePubsubAcknowledge;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManualAcknowledgementAsyncIT extends PubsubTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ManualAcknowledgementAsyncIT.class);

    private static final String TOPIC_NAME = "manualAcknowledgeAsyncTopic";
    private static final String SUBSCRIPTION_NAME = "manualAcknowledgeAsyncSubscription";
    private static final String ROUTE_ID = "receive-from-subscription";
    private static Boolean ack = true;

    @EndpointInject("mock:receiveResult")
    private MockEndpoint receiveResult;

    @Produce("direct:in")
    private ProducerTemplate producer;

    @Override
    public void createTopicSubscription() {
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME, 1);
    }

    @Override
    public RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").routeId("send-to-topic").to("google-pubsub:{{project.id}}:" + TOPIC_NAME);

                from("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=false&ackMode=NONE")
                        .autoStartup(false)
                        .routeId(ROUTE_ID)
                        .to("mock:receiveResult")
                        .process(exchange -> {
                            GooglePubsubAcknowledge acknowledge = exchange.getIn()
                                    .getHeader(
                                            GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE,
                                            GooglePubsubAcknowledge.class);

                            if (ManualAcknowledgementAsyncIT.ack) {
                                acknowledge.ack(exchange);
                            } else {
                                LOG.debug("Nack!");
                                acknowledge.nack(exchange);
                            }
                        });
            }
        };
    }

    @Test
    public void testManualAcknowledgement() throws Exception {
        // 2. Synchronous consumer with manual acknowledgement.
        // Message should only be received once.
        producer.sendBody("Testing!");
        receiveResult.expectedMessageCount(1);
        context.getRouteController().startRoute(ROUTE_ID);
        receiveResult.assertIsSatisfied(3000);

        receiveResult.reset();

        ack = false;

        // 4. Synchronous consumer with manual negative-acknowledgement.
        // Message should be continuously redelivered after being nacked.
        producer.sendBody("Testing2!");
        receiveResult.expectedMinimumMessageCount(3);
        receiveResult.assertIsSatisfied(3000);
    }
}
