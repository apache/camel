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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class AdviceWithInvalidConfiguredTest extends ContextTestSupport {

    public void testNoErrorHandler() throws Exception {
        try {
            context.getRouteDefinition("route-a").adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    errorHandler(loggingErrorHandler());

                    interceptSendToEndpoint("direct:bar")
                        .skipSendToOriginalEndpoint()
                        .throwException(new IllegalArgumentException("Forced"));
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You can not advice with error handlers. Remove the error handlers from the route builder.", e.getMessage());
        }
    }

    public void testNoExtraRoutes() throws Exception {
        try {
            context.getRouteDefinition("route-a").adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:foo").to("mock:foo");

                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("You can only advice from a RouteBuilder which has no existing routes. Remove all routes from the route builder.", e.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error"));

                from("direct:start").routeId("route-a").to("direct:bar");

                from("direct:bar").routeId("route-b").to("mock:bar");
            }
        };
    }
}
