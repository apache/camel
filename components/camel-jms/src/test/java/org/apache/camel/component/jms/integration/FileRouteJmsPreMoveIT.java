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
package org.apache.camel.component.jms.integration;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.jms.ClassicJmsHeaderFilterStrategy;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit6.TestSupport.deleteDirectory;

/**
 *
 */
public class FileRouteJmsPreMoveIT extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected final String componentName = "activemq";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/FileRouteJmsPreMoveIT/inbox");
        deleteDirectory("target/FileRouteJmsPreMoveIT/outbox");
    }

    @Test
    public void testPreMove() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedFileExists("target/FileRouteJmsPreMoveIT/outbox/hello.txt", "Hello World");

        template.sendBodyAndHeader("file://target/FileRouteJmsPreMoveIT/inbox", "Hello World", Exchange.FILE_NAME,
                "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file://target/FileRouteJmsPreMoveIT/inbox?preMove=transfer")
                        .to("activemq:queue:FileRouteJmsPreMoveIT");

                from("activemq:queue:FileRouteJmsPreMoveIT")
                        .to("log:outbox")
                        .to("file://target/FileRouteJmsPreMoveIT/outbox")
                        .to("mock:result");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @Override
    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        JmsComponent amq = super.buildComponent(connectionFactory);
        // need to use the classic header filter
        amq.setHeaderFilterStrategy(new ClassicJmsHeaderFilterStrategy());
        return amq;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
