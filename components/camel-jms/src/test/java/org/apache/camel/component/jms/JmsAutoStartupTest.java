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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(60)
public class JmsAutoStartupTest extends AbstractPersistentJMSTest {

    private JmsEndpoint endpoint;

    @Test
    public void testAutoStartup() throws Exception {
        Service service = context.getRoutes().get(0).getServices().get(0);
        JmsConsumer consumer = (JmsConsumer) service;

        assertFalse(consumer.getListenerContainer().isRunning());

        MockEndpoint mock = getMockEndpoint("mock:result");
        // should be stopped by default
        mock.expectedMessageCount(0);

        template.sendBody("activemq:queue:JmsAutoStartupTest", "Hello World");

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        mock.reset();
        mock.expectedBodiesReceived("Hello World");

        // then start the listener so we can consume the persistent message
        consumer.startListenerContainer();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                endpoint = context.getEndpoint("activemq:queue:JmsAutoStartupTest?autoStartup=false", JmsEndpoint.class);

                from(endpoint).to("mock:result");
            }
        };
    }

}
