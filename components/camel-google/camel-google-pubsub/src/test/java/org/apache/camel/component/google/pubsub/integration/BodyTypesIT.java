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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BodyTypesIT extends PubsubTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BodyTypesIT.class);

    private static final String TOPIC_NAME = "typesSend";
    private static final String SUBSCRIPTION_NAME = "TypesReceive";

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
        try {
            createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME);
        } catch (Exception e) {
            // May be ignored because it could have been created.
            LOG.warn("Failed to create the subscription pair {}", e.getMessage());
        }
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
    public void byteArray() throws Exception {

        Exchange exchange = new DefaultExchange(context);

        byte[] body = { 1, 2, 3 };

        exchange.getIn().setBody(body);

        receiveResult.expectedMessageCount(1);

        producer.send(exchange);

        List<Exchange> sentExchanges = sendResult.getExchanges();
        assertEquals(1, sentExchanges.size(), "Sent exchanges");

        Exchange sentExchange = sentExchanges.get(0);

        assertTrue(sentExchange.getIn().getBody() instanceof byte[], "Sent body type is byte[]");

        assertSame(body, sentExchange.getIn().getBody(), "Sent body type is the one sent");

        receiveResult.assertIsSatisfied(5000);

        List<Exchange> receivedExchanges = receiveResult.getExchanges();

        assertNotNull(receivedExchanges, "Received exchanges");

        Exchange receivedExchange = receivedExchanges.get(0);

        assertTrue(receivedExchange.getIn().getBody() instanceof byte[], "Received body is of byte[] type");

        assertTrue(Arrays.equals(body, (byte[]) receivedExchange.getIn().getBody()), "Received body equals sent");

    }

    @Test
    public void objectSerialised() throws Exception {

        Exchange exchange = new DefaultExchange(context);

        Map<String, String> body = new HashMap<>();
        body.put("KEY", "VALUE1212");

        exchange.getIn().setBody(body);

        receiveResult.expectedMessageCount(1);

        producer.send(exchange);

        List<Exchange> sentExchanges = sendResult.getExchanges();
        assertEquals(1, sentExchanges.size(), "Sent exchanges");

        Exchange sentExchange = sentExchanges.get(0);

        assertTrue(sentExchange.getIn().getBody() instanceof Map, "Sent body type is byte[]");

        receiveResult.assertIsSatisfied(5000);

        List<Exchange> receivedExchanges = receiveResult.getExchanges();

        assertNotNull(receivedExchanges, "Received exchanges");

        Exchange receivedExchange = receivedExchanges.get(0);

        assertTrue(receivedExchange.getIn().getBody() instanceof byte[], "Received body is of byte[] type");

        Object bodyReceived = deserialize((byte[]) receivedExchange.getIn().getBody());

        assertEquals("VALUE1212", ((Map) bodyReceived).get("KEY"), "Received body is a Map");

    }

    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
