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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
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
 * ActiveMQ specific unit test
 */
public class ActiveMQConsumeWildcardQueuesTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Test
    public void testWildcard() throws Exception {
        MockEndpoint chelsea = getMockEndpoint("mock:chelsea");
        chelsea.expectedBodiesReceived("B");
        MockEndpoint first = getMockEndpoint("mock:1st");
        first.expectedBodiesReceived("D");
        MockEndpoint other = getMockEndpoint("mock:other");
        other.expectedBodiesReceivedInAnyOrder("A", "C");

        template.sendBody("activemq:queue:sport.pl.manu", "A");
        template.sendBody("activemq:queue:sport.pl.chelsea", "B");
        template.sendBody("activemq:queue:sport.pl.arsenal", "C");
        template.sendBody("activemq:queue:sport.1st.leeds", "D");

        chelsea.assertIsSatisfied();
        other.assertIsSatisfied();
        first.assertIsSatisfied();
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // use wildcard to consume from all sports
                from("activemq:queue:sport.#")
                        .to("log:received?showHeaders=true")
                        .choice()
                        // the JMSDestination contains from which queue the message was consumed from
                        .when(header("JMSDestination").isEqualTo("ActiveMQQueue[sport.pl.chelsea]"))
                        .to("mock:chelsea")
                        // we can use a reg exp to match any message from 1st division
                        .when(header("JMSDestination").regex("ActiveMQQueue\\[sport.1st.*\\]"))
                        .to("mock:1st")
                        .otherwise()
                        .to("mock:other")
                        .end();
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
