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
package org.apache.camel.component.jetty.rest.producer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HttpRestProducerGetTest extends BaseJettyTest {

    @Test
    public void testHttpProducerGetWithHeader() {
        String out = fluentTemplate.withHeader("id", "123").to("direct:start").request(String.class);
        assertEquals("123;Donald Duck", out);
    }

    @Test
    public void testHttpProducerGetWithVariable() {
        String out = fluentTemplate.withVariable("id", "456").to("direct:start").request(String.class);
        assertEquals("456;Donald Duck", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("jetty").host("localhost").port(getPort())
                        // use camel-http as rest client
                        .producerComponent("http");

                from("direct:start").to("rest:get:users/{id}/basic");

                // use the rest DSL to define the rest services
                rest("/users/").get("{id}/basic").to("direct:basic");
                from("direct:basic").to("mock:input").process(new Processor() {
                    public void process(Exchange exchange) {
                        String id = exchange.getIn().getHeader("id", String.class);
                        exchange.getMessage().setBody(id + ";Donald Duck");
                    }
                });
            }
        };
    }

}
