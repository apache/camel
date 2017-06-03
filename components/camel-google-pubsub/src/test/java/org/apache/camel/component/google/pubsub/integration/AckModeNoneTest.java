/**
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

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AckModeNoneTest extends PubsubTestSupport {

    private static final String TOPIC_NAME = "ackNoneTopic";
    private static final String SUBSCRIPTION_NAME = "ackNoneSub";

    @EndpointInject(uri = "direct:in")
    private Endpoint directIn;

    @EndpointInject(uri = "google-pubsub:{{project.id}}:" + TOPIC_NAME)
    private Endpoint pubsubTopic;

    @EndpointInject(uri = "google-pubsub:{{project.id}}:"
            + SUBSCRIPTION_NAME
            + "?ackMode=NONE")
    private Endpoint pubsubSub;

    @EndpointInject(uri = "mock:receiveResult")
    private MockEndpoint receiveResult;

    @Produce(uri = "direct:in")
    private ProducerTemplate producer;

    @BeforeClass
    public static void createPubSub() throws Exception {
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME, 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(directIn)
                    .routeId("AckNONE_SEND")
                    .to(pubsubTopic);

                from(pubsubSub)
                    .routeId("AckNONE_RECV")
                    .autoStartup(true)
                    .to(receiveResult);
            }
        };
    }
    /**
     * Expecting two messages received for the one sent.
     * With Ack mode set to NONE the same message will be delivered again and again,
     * after the deadline expiration.
     * Setting deadline to 1 second and waiting for more than 2 to ensure the message has been resent.
     * @throws Exception
     */
    @Test
    public void singleMessage() throws Exception {

        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setBody("ACK NONE  : " + exchange.getExchangeId());

        receiveResult.expectedMessageCount(2);
        producer.send(exchange);
        receiveResult.assertIsSatisfied(4000);
    }
}
