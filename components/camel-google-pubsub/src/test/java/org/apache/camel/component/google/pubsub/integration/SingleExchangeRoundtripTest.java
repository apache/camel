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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class SingleExchangeRoundtripTest extends PubsubTestSupport {

    private static final String TOPIC_NAME = "singleSend";
    private static final String SUBSCRIPTION_NAME = "singleReceive";

    @EndpointInject("direct:from")
    private Endpoint directIn;

    @EndpointInject("google-pubsub:{{project.id}}:" + TOPIC_NAME)
    private Endpoint pubsubTopic;

    @EndpointInject("mock:sendResult")
    private MockEndpoint sendResult;

    @EndpointInject("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=true")
    private Endpoint pubsubSubscription;

    @EndpointInject("mock:receiveResult")
    private MockEndpoint receiveResult;

    @Produce("direct:from")
    private ProducerTemplate producer;

    @Override
    public void createTopicSubscription() {
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(directIn).routeId("Single_Send").to(pubsubTopic).to(sendResult);

                from(pubsubSubscription).routeId("Single_Receive").to("direct:one");

                from("direct:one").to(receiveResult);
            }
        };
    }

    @Test
    public void testSingleMessageSend() throws Exception {

        Exchange exchange = new DefaultExchange(context);

        String attributeKey = "ATTRIBUTE-TEST-KEY";
        String attributeValue = "ATTRIBUTE-TEST-VALUE";

        Map<String, String> attributes = new HashMap<>();
        attributes.put(attributeKey, attributeValue);

        exchange.getIn().setBody("Single  : " + exchange.getExchangeId());
        exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, attributes);

        receiveResult.expectedMessageCount(1);
        receiveResult.expectedBodiesReceivedInAnyOrder(exchange.getIn().getBody());

        producer.send(exchange);

        List<Exchange> sentExchanges = sendResult.getExchanges();
        assertEquals("Sent exchanges", 1, sentExchanges.size());

        Exchange sentExchange = sentExchanges.get(0);

        assertEquals("Sent ID", exchange.getIn().getHeader(GooglePubsubConstants.MESSAGE_ID), sentExchange.getIn().getHeader(GooglePubsubConstants.MESSAGE_ID));

        receiveResult.assertIsSatisfied(5000);

        List<Exchange> receivedExchanges = receiveResult.getExchanges();

        assertNotNull("Received exchanges", receivedExchanges);

        Exchange receivedExchange = receivedExchanges.get(0);

        assertNotNull("PUBSUB Message ID Property", receivedExchange.getIn().getHeader(GooglePubsubConstants.MESSAGE_ID));
        assertNotNull("PUBSUB Published Time", receivedExchange.getIn().getHeader(GooglePubsubConstants.PUBLISH_TIME));

        assertEquals("PUBSUB Header Attribute", attributeValue, ((Map)receivedExchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES)).get(attributeKey));

        assertEquals(sentExchange.getIn().getHeader(GooglePubsubConstants.MESSAGE_ID), receivedExchange.getIn().getHeader(GooglePubsubConstants.MESSAGE_ID));
    }
}
