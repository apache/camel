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
import org.junit.jupiter.api.Test;

public class ToDynamicVariableHeadersTest extends ContextTestSupport {

    @Test
    public void testSend() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("World");
        getMockEndpoint("mock:before").expectedVariableReceived("hello", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedVariableReceived("hello", "Camel");
        getMockEndpoint("mock:result").message(0).header("echo").isEqualTo("CamelCamel");

        template.sendBodyAndHeader("direct:send", "World", "where", "foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testReceive() throws Exception {
        getMockEndpoint("mock:after").expectedBodiesReceived("World");
        getMockEndpoint("mock:after").expectedVariableReceived("bye", "Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedVariableReceived("bye", "Bye World");
        getMockEndpoint("mock:result").message(0).header("echo").isNull();

        template.sendBodyAndHeader("direct:receive", "World", "where", "foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendAndReceive() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("World");
        getMockEndpoint("mock:before").expectedVariableReceived("hello", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("World");
        getMockEndpoint("mock:result").expectedVariableReceived("bye", "Bye Camel");
        getMockEndpoint("mock:result").message(0).header("echo").isNull();

        template.sendBodyAndHeader("direct:sendAndReceive", "World", "where", "foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .setVariable("hello", simple("Camel"))
                        .to("mock:before")
                        .toD("direct:${header.where}", "hello", null)
                        .to("mock:result");

                from("direct:receive")
                        .toD("direct:${header.where}", null, "bye")
                        .to("mock:after")
                        .setBody(simple("${variable:bye}"))
                        .to("mock:result");

                from("direct:sendAndReceive")
                        .setVariable("hello", simple("Camel"))
                        .to("mock:before")
                        .toD("direct:${header.where}", "hello", "bye")
                        .to("mock:result");

                from("direct:foo")
                        .setHeader("echo", simple("${body}${body}"))
                        .transform().simple("Bye ${body}");
            }
        };
    }
}
