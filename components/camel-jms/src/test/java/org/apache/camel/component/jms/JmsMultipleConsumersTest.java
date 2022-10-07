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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class JmsMultipleConsumersTest extends AbstractJMSTest {

    @Test
    public void testMultipleConsumersTopic() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:topic:JmsMultipleConsumersTest").to("mock:foo");

                from("direct:JmsMultipleConsumersTest").to("mock:result");

                from("jms:topic:JmsMultipleConsumersTest").to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("jms:topic:JmsMultipleConsumersTest", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMultipleConsumersQueue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:JmsMultipleConsumersTest").to("mock:result");

                from("direct:JmsMultipleConsumersTest").to("mock:result");

                from("jms:queue:JmsMultipleConsumersTest").to("mock:result");
            }
        });

        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("jms:queue:JmsMultipleConsumersTest", "Hello World");
        template.sendBody("jms:queue:JmsMultipleConsumersTest", "Bye World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "jms";
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
