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
package org.apache.camel.component.jetty.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestJettyAcceptTest extends BaseJettyTest {

    @Test
    public void testJettyProducerNoAccept() {
        String out = fluentTemplate.withHeader(Exchange.HTTP_METHOD, "post").withBody("{ \"name\": \"Donald Duck\" }")
                .to("http://localhost:" + getPort() + "/users/123/update")
                .request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testJettyProducerAcceptValid() {
        String out = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader("Accept", "application/json").withHeader(Exchange.HTTP_METHOD, "post")
                .withBody("{ \"name\": \"Donald Duck\" }").to("http://localhost:" + getPort() + "/users/123/update")
                .request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testJettyProducerAcceptInvalid() {
        FluentProducerTemplate requestTemplate = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader("Accept", "application/xml")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .withBody("{ \"name\": \"Donald Duck\" }")
                .to("http://localhost:" + getPort() + "/users/123/update");

        Exception ex = assertThrows(CamelExecutionException.class, () -> requestTemplate.request(String.class));

        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        assertEquals(406, cause.getStatusCode());
        assertEquals("", cause.getResponseBody());
    }

    @Test
    public void testGetContentTypeHeaderOk() throws Exception {
        // use JDK client as camel-http will drop "Content-Type" header for GET
        HttpRequest request = HttpRequest.newBuilder().header("Content-Type", "application/json")
                .uri(URI.create("http://localhost:" + getPort() + "/hello/scott"))
                .GET().build();

        HttpClient client = HttpClient.newBuilder().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort())
                        // turn on client request validation
                        .clientRequestValidation(true);

                // use the rest DSL to define the rest services
                rest("/users/").post("{id}/update").consumes("application/json").produces("application/json").to("direct:update");
                from("direct:update")
                        .setBody(constant("{ \"status\": \"ok\" }"));

                rest("/hello/").get("{id}").consumes("application/json").produces("application/json").to("direct:hello");
                from("direct:hello")
                        .setBody(simple("{ \"hello\": \"${header.id}\" }"));
            }
        };
    }

}
