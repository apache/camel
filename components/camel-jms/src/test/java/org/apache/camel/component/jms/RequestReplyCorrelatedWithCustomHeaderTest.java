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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class RequestReplyCorrelatedWithCustomHeaderTest extends CamelTestSupport {

    private ConnectionFactory connectionFactory;

    public static void processRequest(@Body final String body,
            @Header("CustomCorrelation") final String customCorrelation,
            @Header("JMSCorrelationId") final String jmsCorrelationId, final Exchange exchange) throws Exception {
        assertNotNull(customCorrelation);
        assertNull(jmsCorrelationId);
        exchange.getIn().setBody("Hi, " + body + ", " + customCorrelation);
    }

    @Test
    public void shouldCorrelateRepliesWithCustomCorrelationProperty() throws Exception {
        final String reply = template.requestBody("activemq:queue:request",
                "Bobby", String.class);

        assertTrue(reply.matches("Hi, Bobby, Camel-.*"));
    }

    @Test
    public void shouldCorrelateRepliesWithCustomCorrelationPropertyAndValue() throws Exception {
        final String reply = template.requestBodyAndHeader(
                "activemq:queue:request", "Bobby", "CustomCorrelation",
                "custom-id", String.class);

        assertEquals("Hi, Bobby, custom-id", reply);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();

        connectionFactory = CamelJmsTestHelper.createConnectionFactory();

        final JmsComponent activeMq = jmsComponentAutoAcknowledge(connectionFactory);
        activeMq.getConfiguration().setCorrelationProperty("CustomCorrelation");

        camelContext.addComponent("activemq", activeMq);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:request").bean(RequestReplyCorrelatedWithCustomHeaderTest.class, "processRequest");
            }
        };
    }
}
