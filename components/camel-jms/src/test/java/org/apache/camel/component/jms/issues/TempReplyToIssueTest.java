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

import jakarta.jms.Destination;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TempReplyToIssueTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testReplyToIssue() {
        String out = template.requestBody("activemq:queue:TempReplyToIssueTest", "World", String.class);
        // we should receive that fixed reply
        assertEquals("Hello Moon", out);
    }

    public String handleMessage(
            @Header("JMSReplyTo") final Destination jmsReplyTo,
            @Header("JMSCorrelationID") final String id,
            @Body String body, Exchange exchange)
            throws Exception {
        assertNotNull(jmsReplyTo);
        assertTrue(jmsReplyTo.toString().startsWith("ActiveMQTemporaryQueue"), "Should be a temp queue");

        // we send the reply manually (notice we just use a bogus endpoint uri)
        ProducerTemplate producer = exchange.getContext().createProducerTemplate();
        producer.send("activemq:queue:xxx", exchange1 -> {
            exchange1.getIn().setBody("Hello Moon");
            // remember to set correlation id
            exchange1.getIn().setHeader("JMSCorrelationID", id);
            // this is the real destination we send the reply to
            exchange1.getIn().setHeader(JmsConstants.JMS_DESTINATION, jmsReplyTo);
        });
        // stop it after use
        producer.stop();

        // sleep a bit so Camel will send the reply a bit later
        Thread.sleep(1000);

        // this will later cause a problem as the temp queue has been deleted
        // and exceptions will be logged etc
        return "Hello " + body;
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:TempReplyToIssueTest").bean(TempReplyToIssueTest.class, "handleMessage");
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
