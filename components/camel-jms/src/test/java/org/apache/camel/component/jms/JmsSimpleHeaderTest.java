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

public class JmsSimpleHeaderTest extends AbstractJMSTest {

    protected final String componentName = "activemq";

    @Test
    public void testByteJMSHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("data").isEqualTo((byte) 40);

        template.sendBodyAndHeader("activemq:queue:JmsSimpleHeaderTest", "Hello World", "data", (byte) 40);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCharJMSHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("data").isEqualTo('A');

        template.sendBodyAndHeader("activemq:queue:JmsSimpleHeaderTest", "Hello World", "data", 'A');

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCharSequenceJMSHeaders() throws Exception {
        CharSequence cs = new StringBuilder("Bye World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("data").isEqualTo(cs);

        template.sendBodyAndHeader("activemq:queue:JmsSimpleHeaderTest", "Hello World", "data", cs);

        assertMockEndpointsSatisfied();
    }

    @Override
    public String getComponentName() {
        return componentName;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsSimpleHeaderTest").to("mock:result");
            }
        };
    }
}
