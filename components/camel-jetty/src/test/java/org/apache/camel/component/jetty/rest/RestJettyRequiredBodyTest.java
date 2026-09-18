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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.rest.RestParamType;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestJettyRequiredBodyTest extends BaseJettyTest {

    // bytes that are not valid UTF-8, so they would be replaced if the body was turned into a String
    private static final byte[] BINARY_BODY = { 0x00, 0x01, (byte) 0xFF, (byte) 0xFE, (byte) 0x80, 0x7F, (byte) 0xC3, 0x28 };

    @Test
    public void testJettyValid() {
        String out = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader("Accept", "application/json").withHeader(Exchange.HTTP_METHOD, "post")
                .withBody("{ \"name\": \"Donald Duck\" }").to("http://localhost:" + getPort() + "/users/123/update")
                .request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testJettyInvalidNullBody() {
        FluentProducerTemplate requestTemplate = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader("Accept", "application/json")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .to("http://localhost:" + getPort() + "/users/123/update");

        Exception ex = assertThrows(CamelExecutionException.class, () -> requestTemplate.request(String.class));

        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        assertEquals(400, cause.getStatusCode());
        assertEquals("The request body is missing.", cause.getResponseBody());
    }

    @Test
    public void testJettyInvalidEmptyBody() {
        FluentProducerTemplate requestTemplate = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader("Accept", "application/json")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .withBody(" ")
                .to("http://localhost:" + getPort() + "/users/123/update");

        Exception ex = assertThrows(CamelExecutionException.class, () -> requestTemplate.request(String.class));

        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        assertEquals(400, cause.getStatusCode());
        assertEquals("The request body is missing.", cause.getResponseBody());
    }

    @Test
    public void testJettyBinaryBodyNotCorrupted() {
        byte[] out = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/octet-stream")
                .withHeader("Accept", "application/octet-stream")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .withBody(BINARY_BODY)
                .to("http://localhost:" + getPort() + "/users/123/upload")
                .request(byte[].class);

        assertArrayEquals(BINARY_BODY, out);
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
                rest("/users/").post("{id}/update").consumes("application/json").produces("application/json").param()
                        .name("body").required(true).type(RestParamType.body)
                        .endParam().to("direct:update");
                from("direct:update").setBody(constant("{ \"status\": \"ok\" }"));

                // a binary service that echoes back what it received
                rest("/users/").post("{id}/upload").consumes("application/octet-stream")
                        .produces("application/octet-stream").param()
                        .name("body").required(true).type(RestParamType.body)
                        .endParam().to("direct:upload");
                from("direct:upload").setBody(bodyAs(byte[].class));
            }
        };
    }

}
