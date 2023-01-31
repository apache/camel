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
package org.apache.camel.component.jms;

import java.util.HashMap;
import java.util.Map;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.MapMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConsumeJmsMapMessageTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final Logger LOG = LoggerFactory.getLogger(ConsumeJmsMapMessageTest.class);

    protected JmsTemplate jmsTemplate;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private MockEndpoint endpoint;

    @Test
    public void testConsumeMapMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        jmsTemplate.setPubSubDomain(false);
        jmsTemplate.send("test.map", session -> {
            MapMessage mapMessage = session.createMapMessage();
            mapMessage.setString("foo", "abc");
            mapMessage.setString("bar", "xyz");
            return mapMessage;
        });

        endpoint.assertIsSatisfied();
        assertCorrectMapReceived();
    }

    protected void assertCorrectMapReceived() {
        Exchange exchange = endpoint.getReceivedExchanges().get(0);
        // This should be a JMS Exchange
        assertNotNull(ExchangeHelper.getBinding(exchange, JmsBinding.class));
        JmsMessage in = exchange.getIn(JmsMessage.class);
        assertNotNull(in);

        Map<?, ?> map = exchange.getIn().getBody(Map.class);
        LOG.info("Received map: {}", map);

        assertNotNull(map, "Should have received a map message!");
        assertIsInstanceOf(MapMessage.class, in.getJmsMessage());
        assertEquals("abc", map.get("foo"), "map.foo");
        assertEquals("xyz", map.get("bar"), "map.bar");
        assertEquals(2, map.size(), "map.size");
    }

    @Test
    public void testSendMapMessage() throws Exception {

        endpoint.expectedMessageCount(1);

        Map<String, String> map = new HashMap<>();
        map.put("foo", "abc");
        map.put("bar", "xyz");

        template.sendBody("direct:test", map);

        endpoint.assertIsSatisfied();
        assertCorrectMapReceived();
    }

    @BeforeEach
    public void setUp() throws Exception {
        endpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        jmsTemplate = new JmsTemplate(connectionFactory);

        return super.setupComponent(camelContext, connectionFactory, componentName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:test.map").to("mock:result");
                from("direct:test").to("activemq:test.map");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
