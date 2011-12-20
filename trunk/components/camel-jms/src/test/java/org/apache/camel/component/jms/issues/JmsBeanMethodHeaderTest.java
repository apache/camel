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

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test for sending the bean method name as a key over the JMS wire, that we now support this.
 */
public class JmsBeanMethodHeaderTest extends CamelTestSupport {

    @Test
    public void testPlainHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "yes");

        template.sendBodyAndHeader("direct:in", "Hello World", "foo", "yes");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUnderscoreHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo_bar", "yes");

        template.sendBodyAndHeader("direct:in", "Hello World", "foo_bar", "yes");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUsingBeanNoJMS() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:approve");
        mock.expectedBodiesReceived("Yes");

        template.sendBodyAndHeader("direct:approve", ExchangePattern.InOut, "James",
            Exchange.BEAN_METHOD_NAME, "approveLoan");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUsingBeanAndJMS() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:approve");
        mock.expectedBodiesReceived("Yes");

        template.sendBodyAndHeader("activemq:approve", ExchangePattern.InOut, "James",
            Exchange.BEAN_METHOD_NAME, "approveLoan");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUsingJMStoJMStoBean() throws Exception {
        // the big one from jms to jms to test that we do not lost the bean method name
        MockEndpoint mock = getMockEndpoint("mock:approve");
        mock.expectedBodiesReceived("No");

        template.sendBodyAndHeader("activemq:queue", ExchangePattern.InOut, "James",
            Exchange.BEAN_METHOD_NAME, "approveSuperLoan");

        mock.assertIsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        reg.bind("approveService", new ApproveService());
        return reg;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("activemq:test.a");
                from("activemq:test.a").to("mock:result");

                from("activemq:queue").to("activemq:approve");

                from("activemq:approve").to("direct:approve");

                from("direct:approve").to("bean:approveService").to("mock:approve");
            }
        };
    }

    public static class ApproveService {

        public void doSomeStuff(String input) {
            // just to confuse Camel with more public methods to choose among
        }

        public String approveLoan(@Body String body) {
            return "Yes";
        }

        public String approveSuperLoan(@Body String body) {
            return "No";
        }

    }

}
