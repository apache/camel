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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import java.util.Collection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ErrorHandlerFailurePathsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOnWhenThrowsIsNoMatch() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IOException.class).onWhen(e -> {
                    throw new IllegalArgumentException("onWhen failed");
                }).handled(true).to("mock:when");
                onException(Exception.class).handled(true).to("mock:fallback");

                from("direct:start").throwException(new IOException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:when").expectedMessageCount(0);
        getMockEndpoint("mock:fallback").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDeadLetterChannelRetryWhileThrows() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));
                onException(IOException.class).maximumRedeliveries(2).retryWhile(e -> {
                    throw new IllegalArgumentException("retryWhile failed");
                });

                from("direct:start").throwException(new IOException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertMockEndpointsSatisfied();
        // the dead letter channel handled the exchange
        assertNull(out.getException());
        Exception caught = out.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, Exception.class);
        assertEquals(IOException.class, caught.getClass());
        assertEquals(1, caught.getSuppressed().length);
    }

    @Test
    public void testDeadLetterChannelHandledThrows() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));
                onException(IOException.class).handled(e -> {
                    throw new IllegalArgumentException("handled failed");
                });

                from("direct:start").throwException(new IOException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertMockEndpointsSatisfied();
        assertNull(out.getException());
    }

    @Test
    public void testParallelSplitInsideOnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true)
                        .split(body().tokenize(",")).parallelProcessing().to("direct:x");
                onException(IllegalArgumentException.class).handled(true).to("direct:handler");

                from("direct:start").throwException(new IllegalStateException("Forced"));
                from("direct:x").throwException(new IllegalArgumentException("x failed"));
                from("direct:handler").to("mock:x").delay(100);
            }
        });
        context.start();

        getMockEndpoint("mock:x").expectedMessageCount(4);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("a,b,c,d"));
        assertMockEndpointsSatisfied();
        assertNull(out.getException());
    }

    @Test
    public void testOnPrepareFailureThrows() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .errorHandler(deadLetterChannel("mock:dead").onPrepareFailure(e -> {
                            throw new IllegalStateException("prepare failed");
                        }))
                        .throwException(new IOException("Forced"));

                from("direct:start2")
                        .errorHandler(deadLetterChannel("mock:dead").deadLetterHandleNewException(false)
                                .onPrepareFailure(e -> {
                                    throw new IllegalStateException("prepare failed");
                                }))
                        .throwException(new IOException("Forced"));
            }
        });
        context.start();

        // the exchange is not delivered to the dead letter channel
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        // the new exception is handled (deadLetterHandleNewException=true)
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertNull(out.getException());

        // the new exception is not handled
        out = template.send("direct:start2", e -> e.getMessage().setBody("Hello"));
        assertNotNull(out.getException());
        assertEquals(IllegalStateException.class, out.getException().getClass());
        assertEquals(IOException.class, out.getException().getSuppressed()[0].getClass());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testErrorRegistryNotHandled() throws Exception {
        context.getErrorRegistry().setEnabled(true);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class).to("mock:error");

                from("direct:start").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertNotNull(out.getException());

        Collection<BacklogErrorEventMessage> entries = context.getErrorRegistry().browse();
        assertEquals(1, entries.size());
        assertFalse(entries.iterator().next().isHandled());
    }

    @Test
    public void testOnRedeliveryThrows() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0).onRedelivery(e -> {
                    throw new IllegalStateException("onRedelivery failed");
                }));

                from("direct:start").to("mock:target");
            }
        });
        context.start();

        getMockEndpoint("mock:target").whenAnyExchangeReceived(e -> {
            throw new IOException("Forced");
        });

        // the target is not called again with the exception from the on redelivery processor
        getMockEndpoint("mock:target").expectedMessageCount(1);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertMockEndpointsSatisfied();
        assertEquals(IllegalStateException.class, out.getException().getClass());
    }

    @Test
    public void testContinuedThenUnrelatedFailure() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalArgumentException.class).continued(true);

                from("direct:start")
                        .throwException(new IllegalArgumentException("first"))
                        .throwException(new IllegalStateException("second"));
            }
        });
        context.start();

        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertEquals(IllegalStateException.class, out.getException().getClass());
        // the continued exception is not a previous exception of the new failure
        assertEquals(0, out.getException().getSuppressed().length);
    }

    @Test
    public void testOnCompletionFailureHandledByOnException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:handled");
                onCompletion().onFailureOnly().modeBeforeConsumer().process(e -> {
                    throw new IllegalStateException("onCompletion failed");
                });

                from("direct:start").throwException(new IllegalArgumentException("Forced"));
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedMessageCount(1);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertMockEndpointsSatisfied();
        assertEquals(IllegalArgumentException.class, out.getException().getClass());
    }

    @Test
    public void testOnCompletionHandledFailureNotLeaked() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:handled");
                onCompletion().onCompleteOnly().modeBeforeConsumer().process(e -> {
                    throw new IllegalStateException("onCompletion failed");
                });

                from("direct:start").to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:handled").expectedMessageCount(1);
        Exchange out = template.send("direct:start", e -> e.getMessage().setBody("Hello"));
        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        // the handled failure of the onCompletion is not left on the (successful) exchange
        assertNull(out.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT));
        assertFalse(out.getExchangeExtension().isErrorHandlerHandledSet());
    }
}
