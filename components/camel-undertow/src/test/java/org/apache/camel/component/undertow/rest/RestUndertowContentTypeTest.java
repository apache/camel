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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestUndertowContentTypeTest extends BaseUndertowTest {

    @Test
    public void testProducerNoContentType() {
        String out = fluentTemplate.withHeader(Exchange.HTTP_METHOD, "post").withBody("{ \"name\": \"Donald Duck\" }")
                .to("http://localhost:" + getPort() + "/users/123/update")
                .request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testProducerContentTypeValid() {
        String out = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader(Exchange.HTTP_METHOD, "post").withBody("{ \"name\": \"Donald Duck\" }")
                .to("http://localhost:" + getPort() + "/users/123/update").request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testProducerContentTypeInvalid() {
        fluentTemplate = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/xml")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .withBody("<name>Donald Duck</name>")
                .to("http://localhost:" + getPort() + "/users/123/update");

        Exception ex = assertThrows(CamelExecutionException.class, () -> fluentTemplate.request(String.class));

        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        assertEquals(415, cause.getStatusCode());
        assertEquals("", cause.getResponseBody());
    }

    @Test
    public void testProducerMultiContentTypeValid() {
        String out = fluentTemplate.withHeader("Accept", "application/csv")
                .withHeader(Exchange.HTTP_METHOD, "get")
                .to("http://localhost:" + getPort() + "/users").request(String.class);

        assertEquals("Email,FirstName,LastName\ndonald.duck@disney.com,Donald,Duck", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("undertow").host("localhost").port(getPort())
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
