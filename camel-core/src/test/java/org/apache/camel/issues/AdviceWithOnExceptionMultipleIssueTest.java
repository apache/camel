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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class AdviceWithOnExceptionMultipleIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:startA").routeId("RouteA").to("mock:resultA");
                from("direct:startB").routeId("RouteB").to("mock:resultB");
            }
        };
    }

    public void testSimpleMultipleAdvice() throws Exception {
        context.addRoutes(createRouteBuilder());

        context.getRouteDefinition("RouteA").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                    }
                });
            }
        });

        context.getRouteDefinition("RouteB").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(1);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

    public void testMultipleAdviceWithExceptionThrown() throws Exception {
        context.addRoutes(createRouteBuilder());

        context.getRouteDefinition("RouteA").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new Exception("my exception");
                    }
                });
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(0);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

    public void testMultipleAdvice() throws Exception {
        context.addRoutes(createRouteBuilder());

        context.getRouteDefinition("RouteA").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:resultA").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        throw new Exception("my exception");
                    }
                });
            }
        });

        context.getRouteDefinition("RouteB").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
            }
        });

        context.start();

        getMockEndpoint("mock:resultA").expectedMessageCount(0);
        template.sendBody("direct:startA", "a trigger");
        assertMockEndpointsSatisfied();
    }

}
