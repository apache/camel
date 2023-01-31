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

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Concurrent consumer with InOnly test.
 */
public class JmsConcurrentConsumerInOnlyTest extends CamelTestSupport {
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createVMService();

    @Test
    public void testConcurrentConsumers() throws Exception {
        // send messages to queue before processing
        int size = 2000;
        for (int i = 0; i < size; i++) {
            template.sendBody("activemq:JmsConcurrentConsumerInOnlyTest", "Hello " + i);
        }

        // start route and process the messages
        getMockEndpoint("mock:foo").expectedMessageCount(size);

        context.getRouteController().startAllRoutes();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = CamelJmsTestHelper.createPooledPersistentConnectionFactory(service.serviceAddress());
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:JmsConcurrentConsumerInOnlyTest?concurrentConsumers=2&maxConcurrentConsumers=5").routeId("foo")
                        .noAutoStartup()
                        .log("${threadName} got ${body}")
                        .delay(simple("${random(0,10)}"))
                        .to("mock:foo");
            }
        };
    }

}
