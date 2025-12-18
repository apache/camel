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
package org.apache.camel.component.netty.http.rest;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.http.BaseNettyTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestNettyProducerThrowExceptionErrorTest extends BaseNettyTestSupport {

    @Test
    public void testUndertowProducerOk() {
        String out = fluentTemplate.withHeader("id", "123").to("direct:start").request(String.class);
        assertEquals("123;Donald Duck", out);
    }

    @Test
    public void testUndertowProducerFail() {
        Exchange out = fluentTemplate.withHeader("id", "777").to("direct:start").request(Exchange.class);
        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not have thrown exception");
        assertEquals(500, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use localhost with the given port
                restConfiguration().component("netty-http").host("localhost").port(getPort())
                        .endpointProperty("throwExceptionOnFailure", "false");

                from("direct:start")
                        .to("rest:get:users/{id}/basic");

                // use the rest DSL to define the rest services
                rest("/users/")
                        .get("{id}/basic").to("direct:basic");

                from("direct:basic")
                        .to("mock:input")
                        .process(exchange -> {
                            String id = exchange.getIn().getHeader("id", String.class);
                            if ("777".equals(id)) {
                                throw new IllegalArgumentException("Bad id number");
                            }
                            exchange.getMessage().setBody(id + ";Donald Duck");
                        });
            }
        };
    }

}
