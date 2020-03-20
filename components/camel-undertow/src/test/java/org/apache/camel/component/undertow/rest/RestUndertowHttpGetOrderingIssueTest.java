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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.Test;

public class RestUndertowHttpGetOrderingIssueTest extends BaseUndertowTest {
    
    @Test
    public void testProducerGet() throws Exception {
        getMockEndpoint("mock:root").expectedMessageCount(1);
        String out = template.requestBody("undertow:http://localhost:{{port}}", null, String.class);
        assertEquals("Route without name", out);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:pippo").expectedMessageCount(1);
        out = template.requestBody("undertow:http://localhost:{{port}}/Donald", null, String.class);
        assertEquals("Route with name: Donald", out);
        assertMockEndpointsSatisfied();

        resetMocks();

        getMockEndpoint("mock:bar").expectedMessageCount(1);
        out = template.requestBody("undertow:http://localhost:{{port}}/bar", null, String.class);
        assertEquals("Going to the bar", out);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use undertow on localhost with the given port
                restConfiguration().component("undertow").host("localhost").port(getPort());

                rest()
                    .get("/bar")
                        .route()
                        .setBody().constant("Going to the bar")
                        .to("mock:bar")
                        .setHeader("Content-Type", constant("text/plain"));

                rest()
                    .get("/{pippo}")
                        .route()
                        .setBody().simple("Route with name: ${header.pippo}")
                        .to("mock:pippo")
                        .setHeader("Content-Type", constant("text/plain"));

                rest()
                    .get("/")
                        .route()
                        .setBody().constant("Route without name")
                        .to("mock:root")
                        .setHeader("Content-Type", constant("text/plain"));
            }
        };
    }

}
