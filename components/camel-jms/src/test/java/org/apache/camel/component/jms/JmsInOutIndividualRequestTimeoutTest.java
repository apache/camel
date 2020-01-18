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
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class JmsInOutIndividualRequestTimeoutTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String out = template.requestBody("direct:start", "Camel", String.class);
        assertEquals("Bye Camel", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTimeout() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.requestBodyAndHeader("direct:start", "World", JmsConstants.JMS_REQUEST_TIMEOUT, 1500L, String.class);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            ExchangeTimedOutException timeout = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
            assertEquals(1500, timeout.getTimeout());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIndividualTimeout() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        String out = template.requestBodyAndHeader("direct:start", "World", JmsConstants.JMS_REQUEST_TIMEOUT, 8000L, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                        .inOut("activemq:queue:foo?replyTo=queue:bar&requestTimeout=2000")
                        .to("mock:result");

                from("activemq:queue:foo")
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("World".equals(body)) {
                                log.debug("Sleeping for 4 sec to force a timeout");
                                Thread.sleep(4000);
                            }
                        }).transform(body().prepend("Bye ")).to("log:reply");
            }
        };
    }

}
