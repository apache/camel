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
package org.apache.camel.component.direct;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectEndpointRouteInlinedTest extends ContextTestSupport {

    @Test
    public void testDirect() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        });
        context.start();

        // invoke start a 2nd time wont break stuff
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDirectExistingExists() throws Exception {

        FailedToStartRouteException e = assertThrows(FailedToStartRouteException.class,
                () -> {
                    context.addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("direct:start").to("mock:result");

                            from("direct:start").to("mock:bar");
                        }
                    });
                }, "Should have thrown exception");

        assertTrue(e.getMessage().matches(
                "Failed to start route route[0-9]+ because of Multiple consumers for the same endpoint is not allowed: direct://start"));
    }

}
