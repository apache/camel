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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

/**
 * Testing with async stop listener
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Timeout(60)
public class JmsAsyncStopListenerTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testAsyncStopListener() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:JmsAsyncStopListenerTest");
        result.expectedMessageCount(2);

        template.sendBody("activemq:queue:JmsAsyncStopListenerTest", "Hello World");
        template.sendBody("activemq:queue:JmsAsyncStopListenerTest", "Goodbye World");

        result.assertIsSatisfied();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        JmsComponent jms = super.setupComponent(camelContext, service, componentName);
        jms.getConfiguration().setAsyncStopListener(true);
        return jms;
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
