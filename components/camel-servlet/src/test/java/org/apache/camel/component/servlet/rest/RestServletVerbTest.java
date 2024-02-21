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
package org.apache.camel.component.servlet.rest;

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletVerbTest extends ServletCamelRouterTestSupport {

    @Test
    public void testGetAll() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/users");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]", response.getText());
    }

    @Test
    public void testGetOne() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/users/1");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("{ \"id\":\"1\", \"name\":\"Scott\" }", response.getText());
    }

    @Test
    public void testPost() throws Exception {
        final String body = "{ \"id\":\"1\", \"name\":\"Scott\" }";

        MockEndpoint mock = getMockEndpoint("mock:create");
        mock.expectedBodiesReceived(body);
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "POST");

        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users",
                new ByteArrayInputStream(body.getBytes()), "application/json");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPut() throws Exception {
        final String body = "{ \"id\":\"1\", \"name\":\"Scott\" }";

        MockEndpoint mock = getMockEndpoint("mock:update");
        mock.expectedBodiesReceived(body);
        mock.expectedHeaderReceived("id", "1");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "PUT");

        WebRequest req = new PutMethodWebRequest(
                contextUrl + "/services/users/1",
                new ByteArrayInputStream(body.getBytes()), "application/json");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:delete");
        mock.expectedHeaderReceived("id", "1");
        mock.expectedHeaderReceived(Exchange.HTTP_METHOD, "DELETE");

        WebRequest req = new HeaderOnlyWebRequest(contextUrl + "/services/users/1") {
            @Override
            public String getMethod() {
                return "DELETE";
            }
        };
        WebResponse response = query(req, false);

        assertEquals(204, response.getResponseCode());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("servlet")
                        .endpointProperty("eagerCheckContentAvailable", "true");

                rest()
                        .get("/users").to("direct:users")
                        .get("/users/{id}").to("direct:id")
                        .put("/users/{id}").to("mock:update")
                        .post("/users").to("mock:create")
                        .delete("/users/{id}").to("mock:delete");

                from("direct:users")
                        .transform()
                        .constant("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]");
                from("direct:id")
                        .transform().simple("{ \"id\":\"${header.id}\", \"name\":\"Scott\" }");
            }
        };
    }
}
