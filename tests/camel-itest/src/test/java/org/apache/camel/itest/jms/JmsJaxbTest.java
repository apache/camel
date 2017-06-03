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
package org.apache.camel.itest.jms;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsJaxbTest extends CamelTestSupport {

    @Test
    public void testOk() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setName("Wine");
        order.setAmount(123.45);
        order.setPrice(2.22);

        MockEndpoint result = getMockEndpoint("mock:wine");
        result.expectedBodiesReceived(order);

        template.sendBody("jms:queue:in", "<purchaseOrder name='Wine' amount='123.45' price='2.22'/>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshalError() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("<foo/>");
        getMockEndpoint("mock:invalid").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("jms:queue:in", "<foo/>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotWine() throws Exception {
        PurchaseOrder order = new PurchaseOrder();
        order.setName("Beer");
        order.setAmount(2);
        order.setPrice(1.99);

        MockEndpoint error = getMockEndpoint("mock:invalid");
        error.expectedBodiesReceived(order);
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("jms:queue:in", "<purchaseOrder name='Beer' amount='2.0' price='1.99'/>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        answer.bind("jms", amq);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("jms:queue:error").redeliveryDelay(0));

                onException(InvalidOrderException.class).maximumRedeliveries(0).handled(true)
                    .to("jms:queue:invalid");

                DataFormat jaxb = new JaxbDataFormat("org.apache.camel.itest.jms");

                from("jms:queue:in")
                    .unmarshal(jaxb)
                    .choice()
                        .when().method(JmsJaxbTest.class, "isWine").to("jms:queue:wine")
                        .otherwise().throwException(new InvalidOrderException("We only like wine"))
                    .end();

                from("jms:queue:wine").to("mock:wine");
                from("jms:queue:error").to("mock:error");
                from("jms:queue:invalid").to("mock:invalid");
            }
        };
    }

    public static boolean isWine(PurchaseOrder order) {
        return "Wine".equalsIgnoreCase(order.getName());
    }

}
