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
package org.apache.camel.component.jms.issues;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ActiveMQPropagateSerializableHeadersTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected final Object expectedBody = "<time>" + new Date() + "</time>";
    protected ActiveMQQueue replyQueue = new ActiveMQQueue("ActiveMQPropagateSerializableHeadersTest.reply.queue");
    protected String correlationID = "ABC-123";
    protected String messageType = getClass().getName();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    private Calendar calValue;
    private Map<String, Object> mapValue;

    @BeforeEach
    public void setup() {
        calValue = Calendar.getInstance();
        mapValue = new LinkedHashMap<>();
        mapValue.put("myStringEntry", "stringValue");
        mapValue.put("myCalEntry", Calendar.getInstance());
        mapValue.put("myIntEntry", 123);
    }

    @Test
    public void testForwardingAMessageAcrossJMSKeepingCustomJMSHeaders() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedBodiesReceived(expectedBody);
        AssertionClause firstMessageExpectations = resultEndpoint.message(0);
        firstMessageExpectations.header("myCal").isEqualTo(calValue);
        firstMessageExpectations.header("myMap").isEqualTo(mapValue);

        template.sendBody("activemq:ActiveMQPropagateSerializableHeadersTest.a", expectedBody);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        {
            String headerValue = exchange.getIn().getHeader("myString", String.class);
            assertEquals("stringValue", headerValue, "myString");
        }
        {
            Calendar headerValue = exchange.getIn().getHeader("myCal", Calendar.class);
            assertEquals(calValue, headerValue, "myCal");
        }
        {
            Map<?, ?> headerValue = exchange.getIn().getHeader("myMap", Map.class);
            assertEquals(mapValue, headerValue, "myMap");
        }
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @ContextFixture
    public void configureComponent(CamelContext context) {
        // prevent java.io.NotSerializableException: org.apache.camel.support.DefaultMessageHistory
        context.setMessageHistory(false);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:ActiveMQPropagateSerializableHeadersTest.a").process(exchange -> {
                    // set the JMS headers
                    Message in = exchange.getIn();
                    in.setHeader("myString", "stringValue");
                    in.setHeader("myMap", mapValue);
                    in.setHeader("myCal", calValue);
                }).to("activemq:ActiveMQPropagateSerializableHeadersTest.b?transferExchange=true&allowSerializedHeaders=true");

                from("activemq:ActiveMQPropagateSerializableHeadersTest.b").to("mock:result");
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
