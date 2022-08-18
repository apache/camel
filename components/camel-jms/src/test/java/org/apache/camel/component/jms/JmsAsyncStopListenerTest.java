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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;

/**
 * Testing with async stop listener
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Tags({ @Tag("not-parallel") })
@Timeout(60)
public class JmsAsyncStopListenerTest extends AbstractJMSTest {

    protected String componentName = "activemq";

    @Test
    public void testAsyncStopListener() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:JmsAsyncStopListenerTest");
        result.expectedMessageCount(2);

        template.sendBody("activemq:queue:JmsAsyncStopListenerTest", "Hello World");
        template.sendBody("activemq:queue:JmsAsyncStopListenerTest", "Goodbye World");

        result.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = createConnectionFactory(service);
        JmsComponent jms = jmsComponentAutoAcknowledge(connectionFactory);
        jms.getConfiguration().setAsyncStopListener(true);
        camelContext.addComponent(componentName, jms);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsAsyncStopListenerTest").to("mock:JmsAsyncStopListenerTest");
            }
        };
    }
}
