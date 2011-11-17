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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.WaitForTaskToComplete;

/**
 * @version 
 */
public class PredicateBinaryCoerceRouteTest extends ContextTestSupport {

    public void testNoHeader() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(0);
        getMockEndpoint("mock:456").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsNumber123() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(1);
        getMockEndpoint("mock:456").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 123);

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsNumber456() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(0);
        getMockEndpoint("mock:456").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 456);

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsNumber999() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(0);
        getMockEndpoint("mock:456").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 999);

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsString123() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(1);
        getMockEndpoint("mock:456").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsString456() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(0);
        getMockEndpoint("mock:456").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "456");

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsString999() throws Exception {
        getMockEndpoint("mock:123").expectedMessageCount(0);
        getMockEndpoint("mock:456").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "999");

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsEnum() throws Exception {
        getMockEndpoint("mock:enum").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", WaitForTaskToComplete.Always);

        assertMockEndpointsSatisfied();
    }

    public void testHeaderAsEnumString() throws Exception {
        getMockEndpoint("mock:enum").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "Always");

        assertMockEndpointsSatisfied();
    }

    public void testOtherMax() throws Exception {
        getMockEndpoint("mock:max").expectedMessageCount(1);
        getMockEndpoint("mock:min").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "250");

        assertMockEndpointsSatisfied();
    }

    public void testOtherMin() throws Exception {
        getMockEndpoint("mock:max").expectedMessageCount(0);
        getMockEndpoint("mock:min").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "foo", "200");

        assertMockEndpointsSatisfied();
    }

    public void testOtherAlways() throws Exception {
        getMockEndpoint("mock:max").expectedMessageCount(0);
        getMockEndpoint("mock:min").expectedMessageCount(0);
        getMockEndpoint("mock:enum").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "enum", "Always");

        assertMockEndpointsSatisfied();
    }

    public void testOtherNewer() throws Exception {
        getMockEndpoint("mock:max").expectedMessageCount(0);
        getMockEndpoint("mock:min").expectedMessageCount(0);
        getMockEndpoint("mock:enum").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "enum", "Never");

        assertMockEndpointsSatisfied();
    }

    public void testOtherIfReplyExpected() throws Exception {
        getMockEndpoint("mock:max").expectedMessageCount(0);
        getMockEndpoint("mock:min").expectedMessageCount(0);
        getMockEndpoint("mock:enum").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:foo", "Hello World", "enum", WaitForTaskToComplete.IfReplyExpected);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when(header("foo").isEqualTo("123")).to("mock:123")
                        .when(header("foo").isEqualTo(456)).to("mock:456")
                        .when(header("foo").isEqualTo(WaitForTaskToComplete.Always)).to("mock:enum")
                        .otherwise().to("mock:other");

                from("direct:foo")
                    .choice()
                        .when(header("enum").isGreaterThanOrEqualTo(WaitForTaskToComplete.IfReplyExpected)).to("mock:enum")
                        .when(header("foo").isGreaterThan("200")).to("mock:max")
                        .when(header("foo").isLessThanOrEqualTo(200)).to("mock:min")
                        .otherwise().to("mock:other");
            }
        };
    }
}
