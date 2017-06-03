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
package org.apache.camel.component.netty4.http.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty4.http.BaseNettyTest;
import org.junit.Test;

public class RestNettyHttpContextPathMatchGetTest extends BaseNettyTest {

    @Test
    public void testProducerGet() throws Exception {
        String out = template.requestBody("netty4-http:http://localhost:{{port}}/users/123", null, String.class);
        assertEquals("123;Donald Duck", out);

        out = template.requestBody("netty4-http:http://localhost:{{port}}/users/list", null, String.class);
        assertEquals("123;Donald Duck\n456;John Doe", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use netty4-http on localhost with the given port
                restConfiguration().component("netty4-http").host("localhost").port(getPort());

                // use the rest DSL to define the rest services
                rest("/users/")
                    .get("{id}")
                        .route()
                        .to("mock:input")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);
                                exchange.getOut().setBody(id + ";Donald Duck");
                            }
                        })
                    .endRest()
                    .get("list")
                        .route()
                        .to("mock:input")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                exchange.getOut().setBody("123;Donald Duck\n456;John Doe");
                            }
                        });
            }
        };
    }

}
