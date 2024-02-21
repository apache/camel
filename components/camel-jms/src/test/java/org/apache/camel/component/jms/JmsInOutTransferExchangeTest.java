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

import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class JmsInOutTransferExchangeTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    private static final Logger LOG = LoggerFactory.getLogger(JmsInOutTransferExchangeTest.class);

    @EndpointInject("mock:transfer")
    protected MockEndpoint transfer;

    @EndpointInject("mock:result")
    protected MockEndpoint result;
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Test
    public void testTransferExchangeInOut() throws Exception {
        transfer.expectedMessageCount(1);
        result.expectedMessageCount(1);

        template.send("direct:start", exchange -> {
            LOG.debug("Preparing the exchange");
            exchange.getIn().setBody(new SerializableRequestDto("Restless Camel"));

            Map<String, Object> map = new HashMap<>();
            map.put("boolean", Boolean.TRUE);
            map.put("string", "hello");
            map.put("long", 123L);
            map.put("double", 1.23);

            exchange.getIn().setHeaders(map);

            exchange.setProperty("PropertyName", "PropertyValue");
            LOG.debug("Done preparing the exchange");
        });

        LOG.debug("Asserting transfer");
        transfer.assertIsSatisfied();

        LOG.debug("Asserting result");
        result.assertIsSatisfied();

        MockEndpoint.assertIsSatisfied(context);

        Exchange transferExchange = transfer.getExchanges().get(0);
        Exchange exchange = createExchangeWithBody(null);
        assertTrue(transferExchange.getIn() instanceof JmsMessage);

        JmsMessage transferMessage = transferExchange.getIn(JmsMessage.class);
        ActiveMQObjectMessage transferActiveMQMessage = (ActiveMQObjectMessage) transferMessage.getJmsMessage();

        assertTrue(transferActiveMQMessage.getObject() instanceof DefaultExchangeHolder);
        DefaultExchangeHolder exchangeHolder = (DefaultExchangeHolder) transferActiveMQMessage.getObject();
        DefaultExchangeHolder.unmarshal(exchange, exchangeHolder);

        assertNotNull(exchange.getIn().getBody(SerializableRequestDto.class));
        assertEquals(Boolean.TRUE, exchange.getIn().getHeader("boolean", Boolean.class));
        assertEquals((Long) 123L, exchange.getIn().getHeader("long", Long.class));
        assertEquals((Double) 1.23, exchange.getIn().getHeader("double", Double.class));
        assertEquals("hello", exchange.getIn().getHeader("string", String.class));
        assertEquals("PropertyValue", exchange.getProperty("PropertyName"));

        Exchange resultExchange = result.getExchanges().get(0);
        assertTrue(resultExchange.getIn() instanceof JmsMessage);

        JmsMessage resultMessage = resultExchange.getIn(JmsMessage.class);
        ActiveMQObjectMessage resultActiveMQMessage = (ActiveMQObjectMessage) resultMessage.getJmsMessage();
        exchangeHolder = (DefaultExchangeHolder) resultActiveMQMessage.getObject();
        exchange = createExchangeWithBody(null);
        DefaultExchangeHolder.unmarshal(exchange, exchangeHolder);

        assertNotNull(exchange.getIn().getBody(SerializableResponseDto.class));
        assertEquals(Boolean.TRUE, exchange.getIn().getHeader("boolean", Boolean.class));
        assertEquals((Long) 123L, exchange.getIn().getHeader("long", Long.class));
        assertEquals((Double) 1.23, exchange.getIn().getHeader("double", Double.class));
        assertEquals("hello", exchange.getIn().getHeader("string", String.class));
        assertEquals("PropertyValue", exchange.getProperty("PropertyName"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(ExchangePattern.InOut, "activemq:responseGenerator?transferExchange=true")
                        .to("mock:result");

                from("activemq:responseGenerator?transferExchange=true")
                        .to("mock:transfer")
                        .process(exchange -> exchange.getIn().setBody(new SerializableResponseDto(true)));
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
