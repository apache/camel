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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StepIdAware;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DoTryEdgeCasesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnWhenThrowsException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                        .throwException(new IOException("Forced"))
                        .doCatch(IOException.class).onWhen(e -> {
                            throw new IllegalArgumentException("onWhen failed");
                        })
                        .to("mock:catch")
                        .doFinally()
                        .to("mock:finally")
                        .end();
            }
        });
        context.start();

        getMockEndpoint("mock:catch").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        // the exchange must complete (and not hang)
        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello"))
                .get(10, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, out.getException());
        assertEquals("onWhen failed", e.getMessage());
        assertInstanceOf(IOException.class, e.getSuppressed()[0]);
    }

    @Test
    public void testOnWhenOnEachCatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                            .throwException(new IOException("Forced"))
                        .doCatch(IOException.class).onWhen(header("code").isEqualTo("A"))
                            .to("mock:io")
                        .doCatch(IllegalStateException.class).onWhen(header("code").isEqualTo("B"))
                            .to("mock:ise")
                        .end();
            }
        });
        context.start();

        // the onWhen of the 2nd doCatch must not replace the onWhen of the 1st doCatch
        getMockEndpoint("mock:io").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello", "code", "A");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedDoTryKeepsTryBlock() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start")
                        .doTry()
                            .to("direct:sub")
                            .recipientList(constant("direct:fail")).end()
                        .doCatch(Exception.class)
                            .to("mock:catch")
                        .end();

                from("direct:sub")
                        .doTry()
                            .to("mock:inner")
                        .doCatch(IOException.class)
                            .to("mock:subCatch")
                        .end();

                from("direct:fail").errorHandler(noErrorHandler())
                        .throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        // the outer doCatch handles the failure (and not the dead letter channel)
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(0);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDoFinallyThrowsKeepsOriginal() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                            .throwException(new IOException("original"))
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:catch")
                        .doFinally()
                            .throwException(new IllegalStateException("finally"))
                        .end();
            }
        });
        context.start();

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        IOException e = assertInstanceOf(IOException.class, out.getException());
        assertEquals("original", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("finally", e.getSuppressed()[0].getMessage());
    }

    @Test
    public void testDoFinallyKeepsEarlierFailureDetails() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        // simulate the failure details of an earlier (handled) failure
                        .setProperty(Exchange.FAILURE_ROUTE_ID, constant("earlier"))
                        .doTry()
                            .to("mock:try")
                        .doCatch(Exception.class)
                            .to("mock:catch")
                        .end()
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedPropertyReceived(Exchange.FAILURE_ROUTE_ID, "earlier");
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDoTryWithoutCatchOrFinally() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                            .to("mock:try")
                        .end();
            }
        });
        Exception e = assertThrows(Exception.class, () -> context.start());
        FailedToCreateRouteException fe = assertInstanceOf(FailedToCreateRouteException.class, e);
        assertTrue(fe.getCause().getMessage().startsWith("doTry must have one or more doCatch or doFinally blocks"),
                fe.getCause().getMessage());
    }

    @Test
    public void testOnWhenNotEvaluatedWhenHandled() throws Exception {
        AtomicInteger evaluated = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .doTry()
                        .throwException(new IOException("Forced"))
                        .doCatch(IOException.class)
                        .to("mock:io")
                        .doCatch(Exception.class).onWhen(e -> {
                            evaluated.incrementAndGet();
                            return true;
                        })
                        .to("mock:other")
                        .end();
            }
        });
        context.start();

        getMockEndpoint("mock:io").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
        // the exception was handled by the first doCatch so the next doCatch should not be evaluated
        assertEquals(0, evaluated.get());
    }

    @Test
    public void testStepIdOnCatchAndFinally() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .step("s1")
                            .doTry()
                                .to("mock:try")
                            .doCatch(Exception.class).id("myCatch")
                                .to("mock:catch")
                            .doFinally().id("myFinally")
                                .to("mock:finally")
                            .end()
                        .end();
            }
        });
        context.start();

        for (String id : new String[] { "myCatch", "myFinally" }) {
            Processor p = context.getProcessor(id);
            assertNotNull(p, id);
            StepIdAware aware = assertInstanceOf(StepIdAware.class, p, id);
            assertEquals("s1", aware.getStepId(), id);
        }
    }
}
