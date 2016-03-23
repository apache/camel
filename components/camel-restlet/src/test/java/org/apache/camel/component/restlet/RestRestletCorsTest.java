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
package org.apache.camel.component.restlet;

import java.util.Set;

import com.google.common.base.Splitter;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.CoreMatchers.is;

public class RestRestletCorsTest extends RestletTestSupport {
    Splitter headerSplitter = Splitter.on(",").trimResults();

    @Test
    public void testCors() throws Exception {
        Exchange out = template.request("http://localhost:" + portNum + "/users/123/basic", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertEquals("https://localhost:443", out.getOut().getHeader("Access-Control-Allow-Origin"));
        assertEquals("GET, POST, PUT, DELETE, OPTIONS", out.getOut().getHeader("Access-Control-Allow-Methods"));
        assertEquals("Origin, Accept, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers",
                out.getOut().getHeader("Access-Control-Allow-Headers"));
        assertEquals("1234", out.getOut().getHeader("Access-Control-Max-Age"));
    }

    @Test
    public void testRestletProducerGet() throws Exception {
        Exchange exchange = template.request("http://localhost:" + portNum + "/users/123/basic", null);

        // verify no problems have occurred:
        assertFalse(exchange.isFailed());
        Message message = exchange.getOut();
        assertThat(message.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class), is(200));

        // verify all header values match those specified in restConfiguration:
        assertThat(message.getHeader("Access-Control-Allow-Origin", String.class), is("https://localhost:443"));
        assertHeaderSet(message, "Access-Control-Allow-Methods", "GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertHeaderSet(message, "Access-Control-Allow-Headers",
                "Origin", "Accept", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers");
        assertThat(message.getHeader("Access-Control-Max-Age", Integer.class), is(1234));
    }

    private void assertHeaderSet(Message message, String headerName, String... headerValues) {
        // compare header values as sets: ignore order, all required values are present and nothing more:
        String allowHeaders = message.getHeader(headerName, String.class);
        Set<String> actual = newHashSet(headerSplitter.split(allowHeaders));
        Set<String> expected = newHashSet(headerValues);
        assertThat(actual, is(expected));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                restConfiguration()
                        .component("restlet").host("localhost").port(portNum)
                        .enableCORS(true)
                        .corsHeaderProperty("Access-Control-Allow-Origin", "https://localhost:443")
                        .corsHeaderProperty("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                        .corsHeaderProperty("Access-Control-Allow-Headers",
                                "Origin, Accept, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers")
                        .corsHeaderProperty("Access-Control-Max-Age", "1234");

                // use the rest DSL to define the rest services
                rest("/users/")
                        .get("{id}/basic")
                        .route()
                        .to("mock:input")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String id = exchange.getIn().getHeader("id", String.class);
                                exchange.getOut().setBody(id + ";Donald Duck");
                            }
                        });
            }
        };
    }
}

