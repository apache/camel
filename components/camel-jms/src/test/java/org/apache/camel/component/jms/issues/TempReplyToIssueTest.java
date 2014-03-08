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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.jms.JmsConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class TempReplyToIssueTest extends CamelTestSupport {

    @Test
    public void testReplyToIssue() throws Exception {
        String out = template.requestBody("activemq:queue:test.queue", "World", String.class);
        // we should receive that fixed reply
        assertEquals("Hello Moon", out);
    }

    public String handleMessage(@Header("JMSReplyTo") final Destination jmsReplyTo,
                                @Header("JMSCorrelationID") final String id,
                                @Body String body, Exchange exchange) throws Exception {
        assertNotNull(jmsReplyTo);
        assertTrue("Should be a temp queue", jmsReplyTo.toString().startsWith("temp-queue"));

        // we send the reply manually (notice we just use a bogus endpoint uri)
        ProducerTemplate producer = exchange.getContext().createProducerTemplate();
        producer.send("activemq:queue:xxx", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello Moon");
                // remember to set correlation id
                exchange.getIn().setHeader("JMSCorrelationID", id);
                // this is the real destination we send the reply to
                exchange.getIn().setHeader(JmsConstants.JMS_DESTINATION, jmsReplyTo);
            }
        });
        // stop it after use
        producer.stop();

        // sleep a bit so Camel will send the reply a bit later
        Thread.sleep(1000);

        // this will later cause a problem as the temp queue has been deleted
        // and exceptions will be logged etc
        return "Hello " + body;
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:test.queue").bean(TempReplyToIssueTest.class, "handleMessage");
            }
        };
    }
}
