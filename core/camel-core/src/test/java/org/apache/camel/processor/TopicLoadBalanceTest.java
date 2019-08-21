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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class TopicLoadBalanceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").loadBalance().topic().to("mock:x", "mock:y", "mock:z");
            }
        };
    }

    @Test
    public void testTopicManyMessages() throws Exception {
        // they should all get the message
        x.expectedMessageCount(10);
        y.expectedMessageCount(10);
        z.expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Hello World");
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTopic() throws Exception {

        // they should all get the message
        x.expectedBodiesReceived("Hello World");
        y.expectedBodiesReceived("Hello World");
        z.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
