/**
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

public class RandomLoadBalanceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock://x");
        y = getMockEndpoint("mock://y");
        z = getMockEndpoint("mock://z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start").loadBalance().
                random().to("mock://x", "mock://y", "mock://z");
            }
        };
    }

    @Test
    public void testRandom() throws Exception {
        // it should be safe to assume that they should at least each get > 5 messages
        x.expectedMinimumMessageCount(5);
        y.expectedMinimumMessageCount(5);
        z.expectedMinimumMessageCount(5);

        for (int i = 0; i < 100; i++) {
            template.sendBody("direct:start", "Hello World");
        }

        assertMockEndpointsSatisfied();
    }

}