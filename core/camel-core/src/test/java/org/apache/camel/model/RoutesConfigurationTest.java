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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

public class RoutesConfigurationTest extends ContextTestSupport {

    @Test
    public void testRoutesConfiguration() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Hello World", "Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:start")
                                .throwException(new IllegalArgumentException("Foo"));

                        from("direct:start2")
                                .throwException(new IllegalArgumentException("Foo2"));
                    }
                },
                new RouteConfigurationBuilder() {
                    @Override
                    public void configuration() throws Exception {
                        // global routes configuration
                        routeConfiguration().onException(Exception.class).handled(true).to("mock:error");
                    }
                }
        };
    }

}
