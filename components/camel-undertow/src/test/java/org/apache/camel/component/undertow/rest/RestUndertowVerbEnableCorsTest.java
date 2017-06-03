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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.Test;

public class RestUndertowVerbEnableCorsTest extends BaseUndertowTest {

    @Test
    public void testGetAll() throws Exception {
        String out = template.requestBodyAndHeader("http://localhost:" + getPort() + "/users", null, Exchange.HTTP_METHOD, "GET", String.class);
        assertEquals("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]", out);
    }

    @Test
    public void testGetOne() throws Exception {
        String out = template.requestBodyAndHeader("http://localhost:" + getPort() + "/users/1", null, Exchange.HTTP_METHOD, "GET", String.class);
        assertEquals("{ \"id\":\"1\", \"name\":\"Scott\" }", out);
    }

    @Test
    public void testPost() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:create");
        mock.expectedBodiesReceived("{ \"id\":\"1\", \"name\":\"Scott\" }");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        template.requestBodyAndHeader("http://localhost:" + getPort() + "/users", "{ \"id\":\"1\", \"name\":\"Scott\" }", Exchange.HTTP_METHOD, "POST", String.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:update");
        mock.expectedBodiesReceived("{ \"id\":\"1\", \"name\":\"Scott\" }");
        mock.expectedHeaderReceived("id", "1");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "PUT");

        template.requestBodyAndHeader("http://localhost:" + getPort() + "/users/1", "{ \"id\":\"1\", \"name\":\"Scott\" }", Exchange.HTTP_METHOD, "PUT", String.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:delete");
        mock.expectedHeaderReceived("id", "1");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "DELETE");

        template.requestBodyAndHeader("http://localhost:" + getPort() + "/users/1", null, Exchange.HTTP_METHOD, "DELETE", String.class);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("undertow").host("localhost").port(getPort()).enableCORS(true);

                rest()
                    .get("/users").route().transform().constant("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]").endRest()
                    .get("/users/{id}").route().transform().simple("{ \"id\":\"${header.id}\", \"name\":\"Scott\" }").endRest()
                    .post("/users").to("mock:create")
                    .put("/users/{id}").to("mock:update")
                    .delete("/users/{id}").to("mock:delete");
            }
        };
    }
}
