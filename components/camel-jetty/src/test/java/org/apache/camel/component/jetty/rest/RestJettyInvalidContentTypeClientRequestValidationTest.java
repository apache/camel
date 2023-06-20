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
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestJettyInvalidContentTypeClientRequestValidationTest extends BaseJettyTest {

    @Test
    public void testJettyInvalidContentType() {
        fluentTemplate = fluentTemplate.withHeader(Exchange.CONTENT_TYPE, "application/json")
                .withHeader(Exchange.HTTP_METHOD, "post")
                .withBody("{\"name\": \"Donald\"}")
                .to("http://localhost:" + getPort() + "/users/123/update");

        Exception ex = assertThrows(CamelExecutionException.class, () -> fluentTemplate.request(String.class));

        // we send json but the service accepts only text/plain, so we get a 415
        HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
        assertEquals(415, cause.getStatusCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort())
                        .bindingMode(RestBindingMode.json)
                        // turn on client request validation
                        .clientRequestValidation(true);

                // use the rest DSL to define the rest services
                rest("/users/").post("{id}/update")
                        .consumes("text/json").produces("application/json")
                        .to("direct:update");
                from("direct:update").setBody(constant("{ \"status\": \"ok\" }"));
            }
        };
    }

}
