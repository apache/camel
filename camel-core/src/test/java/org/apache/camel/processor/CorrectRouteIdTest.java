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

public class CorrectRouteIdTest extends ContextTestSupport {

    public void testCorrectRouteId() throws Exception {
        getMockEndpoint("mock:foo").expectedHeaderReceived("foo", "foo");
        getMockEndpoint("mock:bar").expectedHeaderReceived("bar", "bar");
        getMockEndpoint("mock:baz").expectedHeaderReceived("baz", "baz");

        template.requestBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").routeId("foo")
                    .setHeader("foo").simple("routeId")
                    .to("mock:foo")
                    .to("seda:bar")
                    .to("mock:result");

                from("seda:bar").routeId("bar")
                    .setHeader("bar").simple("routeId")
                    .to("mock:bar")
                    .to("direct:baz");

                from("direct:baz").routeId("baz")
                    .setHeader("baz").simple("routeId")
                    .to("mock:baz");
            }
        };
    }
}
