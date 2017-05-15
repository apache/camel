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
public class OnExceptionGlobalScopedRecursionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testRecursion() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .to("mock:c")
                    .log("onException")
                    .throwException(new NullPointerException("A NPE error here"))
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");
            }
        });
        context.start();

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }
    }

    public void testRecursionHandled() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .handled(true)
                    .to("mock:c")
                    .log("onException")
                    .throwException(new NullPointerException("A NPE error here"))
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");
            }
        });
        context.start();

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }
    }

    public void testRecursionDirectNoErrorHandler() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .to("mock:c")
                    .log("onException")
                    .to("direct:error")
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");

                // need to turn off error handler when linked with direct, in case you want the same as inlined
                from("direct:error").errorHandler(noErrorHandler())
                    .to("mock:d")
                    .log("error")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }
    }

    public void testRecursionHandledDirectNoErrorHandler() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .handled(true)
                    .to("mock:c")
                    .log("onException")
                    .to("direct:error")
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");

                // need to turn off error handler when linked with direct, in case you want the same as inlined
                from("direct:error").errorHandler(noErrorHandler())
                    .to("mock:d")
                    .log("error")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());

            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, npe.getSuppressed()[0]);
            assertEquals("Bad state", ise.getMessage());
        }
    }

    public void testRecursionDirect() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .to("mock:c")
                    .log("onException")
                    .to("direct:error")
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");

                from("direct:error")
                    .to("mock:d")
                    .log("error")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());
            // we can only see the NPE from the direct route
        }
    }

    public void testRecursionHandledDirect() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);
        getMockEndpoint("mock:d").expectedMessageCount(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Throwable.class)
                    .handled(true)
                    .to("mock:c")
                    .log("onException")
                    .to("direct:error")
                    .end();

                from("direct:test")
                    .to("mock:a")
                    .log("test")
                    .throwException(new IllegalStateException("Bad state")).to("log:test")
                    .to("mock:b");

                from("direct:error")
                    .to("mock:d")
                    .log("error")
                    .throwException(new NullPointerException("A NPE error here"));
            }
        });
        context.start();

        // TODO: figure out why it does not throw exception (handle = true, new exception -> handle true?)
        // TODO: and why route scoped seems to work
        try {
            template.sendBody("direct:test", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            NullPointerException npe = assertIsInstanceOf(NullPointerException.class, e.getCause());
            assertEquals("A NPE error here", npe.getMessage());
            // we can only see the NPE from the direct route
        }
    }

}
