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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsSelectorOptionTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected final String componentName = "activemq";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testJmsMessageWithSelector() throws Exception {
        MockEndpoint endpointA = getMockEndpoint("mock:a");
        MockEndpoint endpointB = getMockEndpoint("mock:b");
        MockEndpoint endpointC = getMockEndpoint("mock:c");

        endpointA.expectedBodiesReceivedInAnyOrder("A blue car!", "A blue car, again!");
        endpointA.expectedHeaderReceived("color", "blue");
        endpointB.expectedHeaderReceived("color", "red");
        endpointB.expectedBodiesReceived("A red car!");

        endpointC.expectedBodiesReceived("Message1", "Message2");
        endpointC.expectedMessageCount(2);

        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "A blue car!", "color", "blue");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "A red car!", "color", "red");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "A blue car, again!", "color", "blue");
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.hello", "Message2", "SIZE_NUMBER", 1600);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConsumerTemplate() {
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.consumer", "Message1", "SIZE_NUMBER", 1505);
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.consumer", "Message3", "SIZE_NUMBER", 1300);
        template.sendBodyAndHeader("activemq:queue:JmsSelectorOptionTest.consumer", "Message2", "SIZE_NUMBER", 1600);

        // process every exchange which is ready. If no exchange is left break
        // the loop
        while (true) {
            Exchange ex = consumer.receiveNoWait("activemq:queue:JmsSelectorOptionTest.consumer?selector=SIZE_NUMBER<1500");
            if (ex != null) {
                Message message = ex.getIn();
                int size = message.getHeader("SIZE_NUMBER", int.class);
                assertTrue(size < 1500, "The message header SIZE_NUMBER should be less than 1500");
                assertEquals("Message3", message.getBody(), "The message body is wrong");
            } else {
                break;
            }
        }
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsSelectorOptionTest.hello?selector=color='blue'").to("mock:a");
                from("activemq:queue:JmsSelectorOptionTest.hello?selector=color='red'").to("mock:b");
                from("activemq:queue:JmsSelectorOptionTest.hello?selector=SIZE_NUMBER>1500").to("mock:c");
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
}
