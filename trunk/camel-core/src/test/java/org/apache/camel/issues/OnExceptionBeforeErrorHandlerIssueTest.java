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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class OnExceptionBeforeErrorHandlerIssueTest extends ContextTestSupport {

    public void testOk() throws Exception {
        context.startRoute("foo");

        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testKabom() throws Exception {
        context.startRoute("foo");

        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "kabom");

        assertMockEndpointsSatisfied();
    }

    public void testIllegal() throws Exception {
        context.startRoute("foo");

        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "illegal");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                        .handled(true)
                        .setBody().constant("Handled")
                        .to("mock:error")
                        .end();

                // usually error handler should be defined first (before onException),
                // but its not enforced
                errorHandler(deadLetterChannel("mock:dead").useOriginalMessage());

                from("direct:start").routeId("foo").noAutoStartup()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("illegal".equals(body)) {
                                    throw new IllegalArgumentException("I cannot do this");
                                } else if ("kabom".equals(body)) {
                                    throw new RuntimeException("Kabom");
                                }
                            }
                        }).to("mock:result");
            }
        };
    }
}
