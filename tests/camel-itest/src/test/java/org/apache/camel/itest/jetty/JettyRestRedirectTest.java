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
package org.apache.camel.itest.jetty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JettyRestRedirectTest extends CamelTestSupport {

    private int port;

    @Test
    public void testRedirectInvocation() throws Exception {
        String response = template.requestBody("http://localhost:" + port + "/metadata/profile/tag", "<hello>Camel</hello>", String.class);
        assertEquals("It should support the redirect out of box.", "Mock profile", response);
    }

    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                restConfiguration().component("jetty").host("localhost").scheme("http").port(port).producerComponent("http");
                rest("/metadata/profile")
                    .get("/{id}").to("direct:profileLookup")
                    .post("/tag").to("direct:tag");

                from("direct:profileLookup").transform().constant("Mock profile");
                from("direct:tag").log("${headers}").process(new Processor() {
                    @Override
                    public void process(Exchange ex) throws Exception {
                        ex.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 303);
                        ex.getOut().setHeader("Location", "/metadata/profile/1");
                    }
                }).log("${headers}").transform().constant("Redirecting...");
            }
        };
    }
    
}

