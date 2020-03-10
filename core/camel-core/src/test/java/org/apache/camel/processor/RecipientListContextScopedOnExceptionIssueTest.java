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
package org.apache.camel.processor;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 *
 */
public class RecipientListContextScopedOnExceptionIssueTest extends ContextTestSupport {

    @Test
    public void testUsingInterceptor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String routeId = exchange.getUnitOfWork().getRoute().getRouteId();
                        assertEquals("fail", routeId);
                    }

                    @Override
                    public String toString() {
                        return "AssertRouteId";
                    }
                }).to("mock:error");

                interceptSendToEndpoint("direct*").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String target = exchange.getIn().getHeader(Exchange.INTERCEPTED_ENDPOINT, String.class);
                        exchange.getIn().setHeader("target", target);
                    }

                    public String toString() {
                        return "SetTargetHeader";
                    }
                });

                from("direct:start").routeId("start").recipientList(header("foo"));

                from("direct:foo").routeId("foo").setBody(constant("Bye World")).to("mock:foo");

                from("direct:fail").routeId("fail").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).header("target").isEqualTo("direct://fail");

        String foo = "direct:foo,direct:fail";

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", foo);
        headers.put(Exchange.FILE_NAME, "hello.txt");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUsingExistingHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String routeId = exchange.getUnitOfWork().getRoute().getRouteId();
                        assertEquals("fail", routeId);
                    }
                }).to("mock:error");

                from("direct:start").routeId("start").recipientList(header("foo"));

                from("direct:foo").routeId("foo").setBody(constant("Bye World")).to("mock:foo");

                from("direct:fail").routeId("fail").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").message(0).header(Exchange.TO_ENDPOINT).isEqualTo("mock://foo");
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).header(Exchange.FAILURE_ENDPOINT).isEqualTo("direct://fail");

        String foo = "direct:foo,direct:fail";

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", foo);
        headers.put(Exchange.FILE_NAME, "hello.txt");

        template.sendBodyAndHeaders("direct:start", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

}
