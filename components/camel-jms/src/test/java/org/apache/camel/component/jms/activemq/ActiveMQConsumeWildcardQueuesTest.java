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
package org.apache.camel.component.jms.activemq;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * ActiveMQ specific unit test
 */
public class ActiveMQConsumeWildcardQueuesTest extends CamelTestSupport {

    @Test
    public void testWildcard() throws Exception {
        getMockEndpoint("mock:chelsea").expectedBodiesReceived("B");
        getMockEndpoint("mock:1st").expectedBodiesReceived("D");
        getMockEndpoint("mock:other").expectedBodiesReceivedInAnyOrder("A", "C");

        template.sendBody("activemq:queue:sport.pl.manu", "A");
        template.sendBody("activemq:queue:sport.pl.chelsea", "B");
        template.sendBody("activemq:queue:sport.pl.arsenal", "C");
        template.sendBody("activemq:queue:sport.1st.leeds", "D");

        assertMockEndpointsSatisfied();
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
                // use wildcard to consume from all sports
                from("activemq:queue:sport.>")
                    .to("log:received?showHeaders=true")
                    .choice()
                        // the JMSDestination contains from which queue the message was consumed from
                        .when(header("JMSDestination").isEqualTo("queue://sport.pl.chelsea"))
                            .to("mock:chelsea")
                        // we can use a reg exp to match any message from 1st division
                        .when(header("JMSDestination").regex("queue://sport.1st.*"))
                            .to("mock:1st")
                        .otherwise()
                            .to("mock:other")
                    .end();
            }
        };
    }

}
