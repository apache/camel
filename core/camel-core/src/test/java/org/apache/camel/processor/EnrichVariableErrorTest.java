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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnrichVariableErrorTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testThrowException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertTrue(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        // TODO: should this be World or Bye World?
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTryCatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .doTry()
                        .throwException(new IllegalArgumentException("Forced"))
                        .doCatch(Exception.class)
                        .setBody(simple("Catch: ${body}"))
                        .end();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(1);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertTrue(out.hasVariables());
        Assertions.assertEquals("World", out.getMessage().getBody());
        Assertions.assertEquals("Catch: Bye World", out.getVariable("bye"));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).setBody(simple("Error: ${body}"));

                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Error: Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionNotHandled() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(false).setBody(simple("Error: ${body}"));

                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertTrue(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Error: Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDefaultErrorHandler() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler());

                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo")
                        .transform()
                        .simple("Bye ${body}")
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertTrue(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo").transform().simple("Bye ${body}").stop();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRollback() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo").transform().simple("Bye ${body}").rollback();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertTrue(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMarkRollbackLast() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo").transform().simple("Bye ${body}").markRollbackOnly();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMarkRollbackOnlyLast() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .enrich()
                        .constant("direct:foo")
                        .variableReceive("bye")
                        .to("mock:result");

                from("direct:foo").transform().simple("Bye ${body}").markRollbackOnlyLast();
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }
}
