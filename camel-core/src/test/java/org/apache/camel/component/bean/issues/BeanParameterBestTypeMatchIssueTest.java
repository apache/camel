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
package org.apache.camel.component.bean.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

public class BeanParameterBestTypeMatchIssueTest extends ContextTestSupport {

    public void testNoParam() throws InterruptedException {
        getMockEndpoint("mock:end").expectedBodiesReceived("A");

        template.sendBodyAndHeader("direct:noParam", "body", "key", "value");

        assertMockEndpointsSatisfied();
    }

    public void test1Param() throws InterruptedException {
        getMockEndpoint("mock:end").expectedBodiesReceived("B");

        template.sendBodyAndHeader("direct:1Param", "body", "key", "value");

        assertMockEndpointsSatisfied();
    }

    public void test2ParamString() throws InterruptedException {
        getMockEndpoint("mock:end").expectedBodiesReceived("C");

        template.sendBodyAndHeader("direct:2Param", "body", "key", "value");

        assertMockEndpointsSatisfied();
    }

    public void test2ParamClassB() throws InterruptedException {
        getMockEndpoint("mock:end").expectedBodiesReceived("D");

        template.sendBodyAndHeader("direct:2Param", "body", "key", new ClassB());

        assertMockEndpointsSatisfied();
    }

    public void test2ParamBoolBody() throws InterruptedException {
        getMockEndpoint("mock:end").expectedBodiesReceived("E");

        template.sendBodyAndHeader("direct:2Param", true, "key", "value");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:noParam").bean(ClassA.class, "foo()").to("mock:end");
                from("direct:1Param").bean(ClassA.class, "foo(${body})").to("mock:end");
                from("direct:2Param").bean(ClassA.class, "foo(${body}, ${header.key})").to("mock:end");
            }
        };
    }
}
