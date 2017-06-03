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
package org.apache.camel.processor.onexception;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Test that exceptions in an onException handler route do not go into recursion
 */
public class OnExceptionRecursionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testRecursionDirect() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .to("mock:c")
                    .to("direct:handle");

                from("direct:test")
                    .to("mock:a")
                    .throwException(new IllegalStateException("Bad state"))
                    .to("mock:b");

                from("direct:handle")
                    .to("mock:d")
                    .log("Handling exception")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        // will be called twice because of the two exceptions because the circular exception is detected to break out
        getMockEndpoint("mock:c").expectedMessageCount(2);
        getMockEndpoint("mock:d").expectedMessageCount(2);

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testRecursionDirectNoErrorHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .to("mock:c")
                    .to("direct:handle");

                from("direct:test")
                    .to("mock:a")
                    .throwException(new IllegalStateException("Bad state"))
                    .to("mock:b");

                from("direct:handle").errorHandler(noErrorHandler())
                    .to("mock:d")
                    .log("Handling exception")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        // we will only be called once because when the route fails its not under error handler
        // and therefore onException wont trigger the 2nd time
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(1);

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

}
