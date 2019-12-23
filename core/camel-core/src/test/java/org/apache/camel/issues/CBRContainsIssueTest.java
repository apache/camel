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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class CBRContainsIssueTest extends ContextTestSupport {

    @Test
    public void testNot13() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIs13() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("direct:start", "13");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIs13Number() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("direct:start", 13);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testContains13() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("direct:start", "Hi 13 how are you?");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testContains13Number() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBody("direct:start", 221344);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testContainsNot13Number() throws Exception {
        getMockEndpoint("mock:13").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("direct:start", 22);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(body().convertToString().contains("13")).to("mock:13").otherwise().to("mock:other");
            }
        };
    }
}
