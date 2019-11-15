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

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Lets test that a number of headers MQSeries doesn't like to be sent are excluded when
 * forwarding a JMS message from one destination to another
 */
public class MQSeriesHeaderTest extends CamelTestSupport {

    @Test
    public void testForwardingJmsMessageIgnoresHeadersMQDoesntLike() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSXAppID", "ABC");

        template.sendBodyAndHeaders("activemq:test.a", "Hello World!", headers);

        endpoint.assertIsSatisfied();

        Exchange exchange = endpoint.getReceivedExchanges().get(0);
        Message in = exchange.getIn();
        assertMessageHeader(in, "JMSXAppID", null);

        log.info("Received message: " + in);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.a").to("activemq:test.b");
                from("activemq:test.b").to("mock:result");
            }
        };
    }
}
