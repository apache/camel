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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;

/**
 * @version 
 */
public class AdviceWithCBRTest extends ContextTestSupport {

    public void testAdviceCBR() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        route.adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveById("foo").after().to("mock:foo2");
                weaveById("bar").after().to("mock:bar2");
            }
        });

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo2").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:bar2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:baz").expectedBodiesReceived("Hi World");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");
        template.sendBodyAndHeader("direct:start", "Bye World", "bar", "123");
        template.sendBody("direct:start", "Hi World");

        assertMockEndpointsSatisfied();
    }

    public void testAdviceToStringCBR() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        route.adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToString("To[mock:foo]").after().to("mock:foo2");
                weaveByToString("To[mock:bar]").after().to("mock:bar2");
            }
        });

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:foo2").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:bar2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:baz").expectedBodiesReceived("Hi World");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "123");
        template.sendBodyAndHeader("direct:start", "Bye World", "bar", "123");
        template.sendBody("direct:start", "Hi World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice()
                        .when(header("foo")).to("mock:foo").id("foo")
                        .when(header("bar")).to("mock:bar").id("bar")
                    .otherwise()
                         .to("mock:baz").id("baz");
            }
        };
    }

}
