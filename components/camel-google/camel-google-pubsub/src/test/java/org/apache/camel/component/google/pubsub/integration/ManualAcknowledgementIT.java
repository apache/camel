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

public class ManualAcknowledgementIT extends PubsubTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ManualAcknowledgementIT.class);

    private static final String TOPIC_NAME = "manualAcknowledgeTopic";
    private static final String SUBSCRIPTION_NAME = "manualAcknowledgeSubscription";
    private static final String SYNC_ROUTE_ID = "receive-from-subscription-sync";
    private static final String ASYNC_ROUTE_ID = "receive-from-subscription-async";
    private static Boolean ack = true;

    @EndpointInject("mock:receiveResultAsync")
    private MockEndpoint receiveResultAsync;

    @EndpointInject("mock:receiveResultSync")
    private MockEndpoint receiveResultSync;

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
                from("direct:in")
                        .routeId("send-to-topic")
                        .to("google-pubsub:{{project.id}}:" + TOPIC_NAME);

                from("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?ackMode=NONE")
                        .autoStartup(false)
                        .routeId(ASYNC_ROUTE_ID)
                        .to("mock:receiveResultAsync")
                        .process(exchange -> {
                            GooglePubsubAcknowledge acknowledge
                                    = exchange.getIn().getHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE,
                                            GooglePubsubAcknowledge.class);

                            if (ManualAcknowledgementIT.ack) {
                                acknowledge.ack(exchange);
                            } else {
                                LOG.debug("Nack!");
                                acknowledge.nack(exchange);
                            }
                        });

                from("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=true&ackMode=NONE")
                        .autoStartup(false)
                        .routeId(SYNC_ROUTE_ID)
                        .to("mock:receiveResultSync")
                        .process(exchange -> {
                            GooglePubsubAcknowledge acknowledge
                                    = exchange.getIn().getHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE,
                                            GooglePubsubAcknowledge.class);

                            if (ManualAcknowledgementIT.ack) {
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
        // 1. Asynchronous consumer with manual acknowledgement.
        // Message should only be received once.
        producer.sendBody("Testing!");
        receiveResultAsync.expectedMessageCount(1);
        context.getRouteController().startRoute(ASYNC_ROUTE_ID);
        receiveResultAsync.assertIsSatisfied(3000);
        context.getRouteController().stopRoute(ASYNC_ROUTE_ID);

        // 2. Synchronous consumer with manual acknowledgement.
        // Message should only be received once.
        producer.sendBody("Testing!");
        receiveResultSync.expectedMessageCount(1);
        context.getRouteController().startRoute(SYNC_ROUTE_ID);
        receiveResultSync.assertIsSatisfied(3000);
        context.getRouteController().stopRoute(SYNC_ROUTE_ID);

        receiveResultSync.reset();
        receiveResultAsync.reset();
        ack = false;

        // 3. Asynchronous consumer with manual negative-acknowledgement.
        // Message should be continuously redelivered after being nacked.
        producer.sendBody("Testing!");
        receiveResultAsync.expectedMinimumMessageCount(3);
        context.getRouteController().startRoute(ASYNC_ROUTE_ID);
        receiveResultAsync.assertIsSatisfied(3000);
        context.getRouteController().stopRoute(ASYNC_ROUTE_ID);

        // 4. Synchronous consumer with manual negative-acknowledgement.
        // Message should be continuously redelivered after being nacked.
        producer.sendBody("Testing!");
        receiveResultSync.expectedMinimumMessageCount(3);
        context.getRouteController().startRoute(SYNC_ROUTE_ID);
        receiveResultSync.assertIsSatisfied(3000);
        context.getRouteController().stopRoute(SYNC_ROUTE_ID);
    }
}
