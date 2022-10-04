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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
public class JmsMessageIDNotOverridenAMQTest extends AbstractJMSTest {

    @Test
    public void testJmsInOnlyIncludeSentJMSMessageID() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:done");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        Exchange done = mock.getReceivedExchanges().get(0);
        assertNotNull(done);

        Object body = done.getIn().getBody();
        assertEquals("Hello World", body);

        String id = done.getIn().getHeader("JMSMessageID", String.class);
        assertNotEquals("id1", id);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent jms = super.setupComponent(camelContext, service, componentName);
        jms.setMessageCreatedStrategy(new MyMessageCreatedStrategy());
        return jms;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("activemq:queue:JmsMessageIDNotOverridenAMQTest?includeSentJMSMessageID=true")
                        .to("mock:done");
            }
        };
    }

    private static class MyMessageCreatedStrategy implements MessageCreatedStrategy {

        @Override
        public void onMessageCreated(Message message, Session session, Exchange exchange, Throwable cause) {
            try {
                message.setJMSMessageID("id1");
            } catch (JMSException e) {
                // ignore
            }
        }
    }
}
