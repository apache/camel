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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.component.servlet.ServletRestHttpBinding;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServletContentTypeTest extends ServletCamelRouterTestSupport {

    @BindToRegistry("myBinding")
    private ServletRestHttpBinding restHttpBinding = new ServletRestHttpBinding();

    @Test
    public void testProducerNoContentType() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/123/update",
                IOUtils.toInputStream("{ \"name\": \"Donald Duck\" }", "UTF-8"),
                null);
        WebResponse response = query(req, false);

        assertEquals(415, response.getResponseCode());
    }

    @Test
    public void testProducerContentTypeValid() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/123/update",
                IOUtils.toInputStream("{ \"name\": \"Donald Duck\" }", "UTF-8"),
                "application/json");
        WebResponse response = query(req, false);

        assertEquals("{ \"status\": \"ok\" }", response.getText());
    }

    @Test
    public void testProducerContentTypeInvalid() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/users/123/update",
                IOUtils.toInputStream("<name>Donald Duck</name>", "UTF-8"),
                "application/xml");
        WebResponse response = query(req, false);

        assertEquals(415, response.getResponseCode());
    }

    @Test
    public void testProducerMultiContentTypeValid() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/users");
        req.setHeaderField("Accept", "application/csv");
        WebResponse response = query(req, false);

        assertEquals("Email,FirstName,LastName\ndonald.duck@disney.com,Donald,Duck", response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("servlet").host("localhost")
                        // turn on client request validation
                        .clientRequestValidation(true);

                // use the rest DSL to define the rest services
                rest("/users").post("/{id}/update").consumes("application/json").produces("application/json").to("direct:update");
                from("direct:update")
                        .setBody(constant("{ \"status\": \"ok\" }"));

                rest("/users").get().produces("application/json,application/csv").to("direct:users");
                from("direct:users")
                        .choice()
                        .when(simple("${header.Accept} == 'application/csv'"))
                        .setBody(constant("Email,FirstName,LastName\ndonald.duck@disney.com,Donald,Duck"))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/csv"))
                        .otherwise()
                        .setBody(constant(
                                "{\"email\": \"donald.duck@disney.com\", \"firstname\": \"Donald\", \"lastname\": \"Duck\"}"));

            }
        };
    }
}
