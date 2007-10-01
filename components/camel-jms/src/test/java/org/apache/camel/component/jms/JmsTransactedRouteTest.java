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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

/**
 * @version $Revision$
 */
public class JmsTransactedRouteTest extends ContextTestSupport {
    public void testJmsRouteWithTextMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        String expectedBody = "Hello there!";
        String expectedBody2 = "Goodbye!";


        resultEndpoint.expectedBodiesReceived(expectedBody, expectedBody2);
        resultEndpoint.message(0).header("cheese").isEqualTo(123);

        template.sendBodyAndHeader("activemq:test.a", expectedBody, "cheese", 123);
        template.sendBodyAndHeader("activemq:test.a", expectedBody2, "cheese", 124);

        resultEndpoint.assertIsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
        JmsComponent component = jmsComponentTransacted(connectionFactory);
        //component.getConfiguration().setCacheLevelName("CACHE_CONNECTION");
        //component.getConfiguration().setCacheLevel(DefaultMessageListenerContainer.CACHE_CONNECTION);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.a").to("activemq:test.b");
                from("activemq:test.b").to("mock:result");
            }
        };
    }
}