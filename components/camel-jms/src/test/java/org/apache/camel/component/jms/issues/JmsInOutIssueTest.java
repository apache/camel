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

import java.util.concurrent.Future;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsInOutIssueTest extends AbstractJMSTest {

    @Test
    public void testInOutWithRequestBody() {
        String reply = template.requestBody("activemq:queue:inJmsInOutIssueTest", "Hello World", String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOutTwoTimes() {
        String reply = template.requestBody("activemq:queue:inJmsInOutIssueTest", "Hello World", String.class);
        assertEquals("Bye World", reply);

        reply = template.requestBody("activemq:queue:inJmsInOutIssueTest", "Hello Camel", String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOutWithAsyncRequestBody() throws Exception {
        Future<String> reply = template.asyncRequestBody("activemq:queue:inJmsInOutIssueTest", "Hello World", String.class);
        assertEquals("Bye World", reply.get());
    }

    @Test
    public void testInOutWithSendExchange() {
        Exchange out = template.send("activemq:queue:inJmsInOutIssueTest", ExchangePattern.InOut,
                exchange -> exchange.getIn().setBody("Hello World"));

        assertEquals("Bye World", out.getMessage().getBody());
    }

    @Test
    public void testInOutWithAsyncSendExchange() throws Exception {
        Future<Exchange> out = template.asyncSend("activemq:queue:inJmsInOutIssueTest", exchange -> {
            exchange.setPattern(ExchangePattern.InOut);
            exchange.getIn().setBody("Hello World");
        });

        assertEquals("Bye World", out.get().getMessage().getBody());
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
            public void configure() {
                from("activemq:queue:inJmsInOutIssueTest").process(exchange -> exchange.getMessage().setBody("Bye World"));
            }
        };
    }

}
