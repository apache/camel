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
package org.apache.camel.component.properties;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class PropertiesComponentDefaultFunctionsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static Map.Entry<String, String> anyNonEmptyEnvironmentVariable() {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getValue() != null && !"".equals(entry.getValue())) {
                return entry;
            }
        }
        throw new IllegalStateException();
    }

    @Test
    public void testFunction() throws Exception {
        System.setProperty("FOO", "mock:foo");
        Map.Entry<String, String> env = anyNonEmptyEnvironmentVariable();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("{{sys:FOO}}").transform().constant("{{env:" + env.getKey() + "}}").to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:bar").expectedBodiesReceived(env.getValue());

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        System.clearProperty("FOO");
    }

    @Test
    public void testFunctionGetOrElse() throws Exception {
        System.setProperty("FOO2", "mock:foo");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("{{sys:FOO2}}").to("{{env:BAR2:mock:bar}}");
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        System.clearProperty("FOO2");
    }

}
