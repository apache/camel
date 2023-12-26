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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestJettyRedirectTest extends CamelTestSupport {

    private int port;

    @Test
    void testRedirectInvocation() {
        String response = template.requestBody("http://localhost:" + port + "/metadata/profile/tag", "<hello>Camel</hello>",
                String.class);
        assertEquals("Mock profile", response, "It should support the redirect out of box.");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                // enable follow redirects
                HttpComponent http = context.getComponent("http", HttpComponent.class);
                http.setFollowRedirects(true);

                restConfiguration().component("jetty").host("localhost").scheme("http").port(port).producerComponent("http");
                rest("/metadata/profile")
                        .get("/{id}").to("direct:profileLookup")
                        .post("/tag").to("direct:tag");

                from("direct:profileLookup").transform().constant("Mock profile");
                from("direct:tag").log("${headers}").process(ex -> {
                    ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 303);
                    ex.getMessage().setHeader("Location", "/metadata/profile/1");
                }).log("${headers}").transform().constant("Redirecting...");
            }
        };
    }

}
