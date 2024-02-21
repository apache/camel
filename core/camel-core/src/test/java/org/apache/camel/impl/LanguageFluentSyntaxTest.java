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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

/**
 * A unit test class ensuring that the fluent syntax to create language works as expected.
 */
class LanguageFluentSyntaxTest extends ContextTestSupport {

    @Test
    void testAsSplitExpression() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("A", "B");

        template.sendBody("direct:a", "A\nB");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testAsOuterExpression() throws Exception {
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World Out");

        template.sendBody("direct:b", "foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testAsInnerExpression() throws Exception {
        getMockEndpoint("mock:c").expectedBodiesReceived("Hello World In");

        template.sendBody("direct:c", "bar");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testAsFiler() throws Exception {
        getMockEndpoint("mock:d").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:d", "Hello World", "foo", "bar");
        template.sendBodyAndHeader("direct:d", "Bye World", "foo", "other");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").split(
                    expression().tokenize().token("\n").end()
                ).to("mock:a");

                from("direct:b").setBody().expression(expression().simple().expression("Hello World Out").end()).to("mock:b");
                from("direct:c").setBody(expression().simple().expression("Hello World In").end()).to("mock:c");
                from("direct:d").filter(expression(expression().header().expression("foo").end()).isEqualTo("bar")).to("mock:d");
            }
        };
    }
}
