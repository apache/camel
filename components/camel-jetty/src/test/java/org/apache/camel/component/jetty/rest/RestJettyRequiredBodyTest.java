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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.rest.RestParamType;
import org.junit.Test;

public class RestJettyRequiredBodyTest extends BaseJettyTest {

    @Test
    public void testJettyValid() throws Exception {
        String out = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json").withHeader("Accept", "application/json").withHeader(Exchange.HTTP_METHOD, "post")
            .withBody("{ \"name\": \"Donald Duck\" }").to("http://localhost:" + getPort() + "/users/123/update").request(String.class);

        assertEquals("{ \"status\": \"ok\" }", out);
    }

    @Test
    public void testJettyInvalid() throws Exception {
        try {
            fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json").withHeader("Accept", "application/json").withHeader(Exchange.HTTP_METHOD, "post")
                .to("http://localhost:" + getPort() + "/users/123/update").request(String.class);

            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(400, cause.getStatusCode());
            assertEquals("The request body is missing.", cause.getResponseBody());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort())
                    // turn on client request validation
                    .clientRequestValidation(true);

                // use the rest DSL to define the rest services
                rest("/users/").post("{id}/update").consumes("application/json").produces("application/json").param().name("body").required(true).type(RestParamType.body)
                    .endParam().route().setBody(constant("{ \"status\": \"ok\" }"));
            }
        };
    }

}
