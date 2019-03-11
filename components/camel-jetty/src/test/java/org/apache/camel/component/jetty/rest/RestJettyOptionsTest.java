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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

public class RestJettyOptionsTest extends BaseJettyTest {

    @Test
    public void testJettyServerOptions() throws Exception {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/users/v1/customers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals(200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("GET,OPTIONS", exchange.getOut().getHeader("ALLOW"));
        assertEquals("", exchange.getOut().getBody(String.class));

        exchange = fluentTemplate.to("http://localhost:" + getPort() + "/users/v1/id/123").withHeader(Exchange.HTTP_METHOD, "OPTIONS").send();
        assertEquals(200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("PUT,OPTIONS", exchange.getOut().getHeader("ALLOW"));
        assertEquals("", exchange.getOut().getBody(String.class));
    }

    @Test
    public void testJettyServerMultipleOptions() throws Exception {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/users/v2/options", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals(200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("GET,POST,OPTIONS", exchange.getOut().getHeader("ALLOW"));
        assertEquals("", exchange.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                restConfiguration().component("jetty").host("localhost").port(getPort());

                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("v1/customers")
                        .to("mock:customers")
                    .put("v1/id/{id}")
                        .to("mock:id")
                    .get("v2/options")
                        .to("mock:options")
                    .post("v2/options")
                        .to("mock:options");
            }
        };
    }

}
