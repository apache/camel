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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Concurrent consumer with InOnly test.
 */
public class JmsConcurrentConsumerInOnlyTest extends CamelTestSupport {

    private int size = 2000;

    @Test
    public void testConcurrentConsumers() throws Exception {
        // send messages to queue before processing
        for (int i = 0; i < size; i++) {
            template.sendBody("activemq:foo", "Hello " + i);
        }

        // start route and process the messages
        getMockEndpoint("mock:foo").expectedMessageCount(size);

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPooledPersistentConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:foo?concurrentConsumers=2&maxConcurrentConsumers=5").routeId("foo").noAutoStartup()
                    .log("${threadName} got ${body}")
                    .delay(simple("${random(0,10)}"))
                    .to("mock:foo");
            }
        };
    }

}
