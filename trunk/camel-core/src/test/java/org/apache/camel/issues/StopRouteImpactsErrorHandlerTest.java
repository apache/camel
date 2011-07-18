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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;

/**
 *
 */
public class StopRouteImpactsErrorHandlerTest extends ContextTestSupport {

    public void testIssue() throws Exception {
        RouteDefinition testRoute = context.getRouteDefinition("TestRoute");
        testRoute.adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("seda:*")
                    .skipSendToOriginalEndpoint()
                    .to("log:seda")
                    .throwException(new IllegalArgumentException("Forced"));
            }
        });

        RouteDefinition smtpRoute = context.getRouteDefinition("smtpRoute");
        smtpRoute.adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("smtp*")
                    .to("log:smtp")
                    .skipSendToOriginalEndpoint()
                    .to("mock:smtp");
            }
        });

        // we should fail and end up sending to smtp
        getMockEndpoint("mock:smtp").expectedMessageCount(1);

        // stopping a route after advice with causes problem with error handlers
        context.stopRoute("pollRoute");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("smtp", context.getComponent("mock"));

                errorHandler(deadLetterChannel("direct:emailSupport")
                        .maximumRedeliveries(2)
                        .redeliveryDelay(0));

                from("direct:emailSupport")
                        .routeId("smtpRoute")
                        .errorHandler(deadLetterChannel("log:dead?level=ERROR"))
                        .to("smtp://smtpServer");

                from("timer://someTimer?delay=15000&fixedRate=true&period=5000")
                        .routeId("pollRoute")
                        .to("log:level=INFO");

                from("direct:start")
                        .routeId("TestRoute")
                        .to("seda:foo");
            }
        };
    }

}

