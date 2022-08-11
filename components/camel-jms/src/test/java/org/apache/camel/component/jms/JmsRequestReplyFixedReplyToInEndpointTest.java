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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsRequestReplyFixedReplyToInEndpointTest extends AbstractJMSTest {

    @Test
    public void testJmsRequestReplyTempReplyTo() {
        Exchange reply = template.request("activemq:queue:foo", exchange -> exchange.getIn().setBody("World"));
        assertEquals("Hello World", reply.getMessage().getBody());
        assertTrue(reply.getMessage().hasHeaders(), "Should have headers");
        String replyTo = reply.getMessage().getHeader("JMSReplyTo", String.class);
        assertTrue(replyTo.startsWith("temp-queue"), "Should be a temp queue");
    }

    @Test
    public void testJmsRequestReplyFixedReplyToInEndpoint() {
        Exchange reply = template.request("activemq:queue:foo?replyTo=bar", exchange -> exchange.getIn().setBody("World"));
        assertEquals("Hello World", reply.getMessage().getBody());
        assertTrue(reply.getMessage().hasHeaders(), "Should have headers");
        assertEquals("queue://bar", reply.getMessage().getHeader("JMSReplyTo", String.class));
    }

    @Test
    public void testJmsRequestReplyFixedReplyToInEndpointTwoMessages() {
        Exchange reply = template.request("activemq:queue:foo?replyTo=bar", exchange -> exchange.getIn().setBody("World"));
        assertEquals("Hello World", reply.getMessage().getBody());
        assertTrue(reply.getMessage().hasHeaders(), "Should have headers");
        assertEquals("queue://bar", reply.getMessage().getHeader("JMSReplyTo", String.class));

        reply = template.request("activemq:queue:foo?replyTo=bar", exchange -> exchange.getIn().setBody("Moon"));
        assertEquals("Hello Moon", reply.getMessage().getBody());
        assertTrue(reply.getMessage().hasHeaders(), "Should have headers");
        assertEquals("queue://bar", reply.getMessage().getHeader("JMSReplyTo", String.class));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:foo")
                        .transform(body().prepend("Hello "));
            }
        };
    }
}
