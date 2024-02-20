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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class RecipientListErrorHandlingIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testUsingInterceptor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");

                interceptSendToEndpoint("direct:*").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String target = exchange.getProperty(Exchange.INTERCEPTED_ENDPOINT, String.class);
                        exchange.getIn().setHeader("target", target);
                    }
                });

                from("direct:start").recipientList(header("foo"));

                from("direct:foo").setBody(constant("Bye World")).to("mock:foo");
                from("direct:kaboom").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).header("target").isEqualTo("direct://kaboom");

        String foo = "direct:foo,direct:kaboom";
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", foo);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUsingExistingHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:start").recipientList(header("foo"));

                from("direct:foo").setBody(constant("Bye World")).to("mock:foo");
                from("direct:kaboom").throwException(new IllegalArgumentException("Damn"));
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:foo").message(0).exchangeProperty(Exchange.TO_ENDPOINT).isEqualTo("mock://foo");
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").message(0).exchangeProperty(Exchange.FAILURE_ENDPOINT).isEqualTo("direct://kaboom");

        String foo = "direct:foo,direct:kaboom";
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", foo);

        assertMockEndpointsSatisfied();
    }

}
