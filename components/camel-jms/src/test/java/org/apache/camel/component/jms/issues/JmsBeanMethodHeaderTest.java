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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Unit test for sending the bean method name as a key over the JMS wire, that we now support this.
 */
public class JmsBeanMethodHeaderTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;
    @BindToRegistry("approveService")
    private final ApproveService service = new ApproveService();

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

        template.sendBodyAndHeader("direct:approve", ExchangePattern.InOut, "James", Exchange.BEAN_METHOD_NAME, "approveLoan");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUsingBeanAndJMS() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:approve");
        mock.expectedBodiesReceived("Yes");

        template.sendBodyAndHeader("activemq:JmsBeanMethodHeaderTest.approve", ExchangePattern.InOut, "James",
                Exchange.BEAN_METHOD_NAME,
                "approveLoan");

        mock.assertIsSatisfied();
    }

    @Test
    public void testUsingJMStoJMStoBean() throws Exception {
        // the big one from jms to jms to test that we do not lost the bean
        // method name
        MockEndpoint mock = getMockEndpoint("mock:approve");
        mock.expectedBodiesReceived("No");

        template.sendBodyAndHeader("activemq:JmsBeanMethodHeaderTest.queue", ExchangePattern.InOut, "James",
                Exchange.BEAN_METHOD_NAME,
                "approveSuperLoan");

        mock.assertIsSatisfied();
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").to("activemq:JmsBeanMethodHeaderTest.a");
                from("activemq:JmsBeanMethodHeaderTest.a").to("mock:result");

                from("activemq:JmsBeanMethodHeaderTest.queue").to("activemq:JmsBeanMethodHeaderTest.approve");

                from("activemq:JmsBeanMethodHeaderTest.approve").to("direct:approve");

                from("direct:approve").to("bean:approveService").to("mock:approve");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
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
