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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test based on user forum problem.
 */
public class ChoicePredicateThrowExceptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testChoiceGlobal() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:start")
                        .choice()
                        .when(e -> {
                            throw new IllegalArgumentException("Forced");
                        })
                        .to("mock:a")
                        .to("mock:b")
                        .to("mock:c")
                        .otherwise()
                        .to("mock:d")
                        .to("mock:e")
                        .to("mock:f")
                        .end()
                        .end()
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:e").expectedMessageCount(0);
        getMockEndpoint("mock:f").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testChoiceSubRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start")
                        .to("direct:sub")
                        .to("mock:result");

                from("direct:sub")
                        .errorHandler(noErrorHandler())
                        .choice()
                        .when(e -> {
                            throw new IllegalArgumentException("Forced");
                        })
                        .to("mock:a")
                        .to("mock:b")
                        .to("mock:c")
                        .otherwise()
                        .to("mock:d")
                        .to("mock:e")
                        .to("mock:f")
                        .end();
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(0);
        getMockEndpoint("mock:d").expectedMessageCount(0);
        getMockEndpoint("mock:e").expectedMessageCount(0);
        getMockEndpoint("mock:f").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello World");
            fail();
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

}
