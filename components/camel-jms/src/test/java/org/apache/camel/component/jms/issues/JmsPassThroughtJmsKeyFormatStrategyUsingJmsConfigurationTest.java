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

import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.PassThroughJmsKeyFormatStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsPassThroughtJmsKeyFormatStrategyUsingJmsConfigurationTest extends CamelTestSupport {

    private String uri = "activemq:queue:hello";

    @Test
    public void testSendWithHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo("Hello World");
        mock.message(0).header("HEADER_1").isEqualTo("VALUE_1");
        mock.message(0).header("HEADER_2").isEqualTo("VALUE_2");
        mock.message(0).header("HEADER_3").isEqualTo("VALUE_3");

        template.sendBodyAndHeader(uri, "Hello World", "HEADER_1", "VALUE_1");

        assertMockEndpointsSatisfied();

        assertEquals("VALUE_1", mock.getReceivedExchanges().get(0).getIn().getHeader("HEADER_1"));
        assertEquals("VALUE_2", mock.getReceivedExchanges().get(0).getIn().getHeader("HEADER_2"));
        assertEquals("VALUE_3", mock.getReceivedExchanges().get(0).getIn().getHeader("HEADER_3"));

        assertEquals("VALUE_1", mock.getReceivedExchanges().get(0).getIn().getHeaders().get("HEADER_1"));
        assertEquals("VALUE_2", mock.getReceivedExchanges().get(0).getIn().getHeaders().get("HEADER_2"));
        assertEquals("VALUE_3", mock.getReceivedExchanges().get(0).getIn().getHeaders().get("HEADER_3"));
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        JmsComponent jms = camelContext.getComponent("activemq", JmsComponent.class);
        jms.getConfiguration().setJmsKeyFormatStrategy(new PassThroughJmsKeyFormatStrategy());

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(uri)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Map<String, Object> headers = exchange.getIn().getHeaders();
                            assertEquals("VALUE_1", headers.get("HEADER_1"));
                            assertEquals("VALUE_1", exchange.getIn().getHeader("HEADER_1"));
                        }
                    })
                    .setHeader("HEADER_3", constant("START"))
                    .setHeader("HEADER_2", constant("VALUE_2"))
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Map<String, Object> headers = exchange.getIn().getHeaders();
                            assertEquals("START", headers.get("HEADER_3"));
                            assertEquals("START", exchange.getIn().getHeader("HEADER_3"));
                        }
                    })
                    .setHeader("HEADER_3", constant("VALUE_3"))
                    .to("mock:result");
            }
        };
    }
}