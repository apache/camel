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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An AsyncProducer that breaks the async contract by throwing from process, before or after it called the callback,
 * called by the optimised {@code to} (SendProcessor) when the send needs its own callback (here because the exchange
 * pattern of the send differs from the one of the exchange).
 */
class SendProcessorProducerThrowsTest extends ContextTestSupport {

    private final AtomicInteger afterSend = new AtomicInteger();

    @Test
    void testCallbackThenThrowRoutesOnce() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Done");

        Exchange out = template.send("direct:start", e -> e.getMessage().setHeader("mode", "after"));

        // the rest of the route runs once, and without the exception the producer threw after it completed
        mock.assertIsSatisfied();
        assertEquals(1, mock.getReceivedCounter());
        assertNull(out.getException());
        assertEquals(ExchangePattern.InOnly, out.getPattern());
    }

    @Test
    void testThrowBeforeCallbackFailsAndRestoresPattern() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        Exchange out = template.send("direct:start", e -> e.getMessage().setHeader("mode", "before"));

        mock.assertIsSatisfied();
        assertInstanceOf(IllegalStateException.class, out.getException());
        // the send is completed as when the producer sets the exception: the exchange pattern is restored
        assertEquals(ExchangePattern.InOnly, out.getPattern());
    }

    @Test
    void testCallbackThenThrowWithSendHookRoutesOnce() throws Exception {
        SendHook hook = new SendHook() {
            @Override
            public long beforeSend(Exchange exchange, Endpoint endpoint) {
                return PROCEED;
            }

            @Override
            public void afterSend(Exchange exchange, Endpoint endpoint) {
                if (endpoint.getEndpointUri().startsWith("throwing:")) {
                    afterSend.incrementAndGet();
                }
            }
        };
        context.getCamelContextExtension().addSendHook(hook);
        try {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Done");

            Exchange out = template.send("direct:start", e -> e.getMessage().setHeader("mode", "after"));

            // the hook does not set the exception the producer threw after it completed, and runs after the send once
            mock.assertIsSatisfied();
            assertEquals(1, mock.getReceivedCounter());
            assertNull(out.getException());
            assertEquals(1, afterSend.get());
        } finally {
            context.getCamelContextExtension().removeSendHook(hook);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addEndpoint("throwing:producer", new ThrowingEndpoint());
                from("direct:start").to(ExchangePattern.InOut, "throwing:producer").to("mock:result");
            }
        };
    }

    private final class ThrowingEndpoint extends DefaultEndpoint {

        ThrowingEndpoint() {
            super("throwing:producer", null);
            setCamelContext(context);
        }

        @Override
        public Producer createProducer() {
            return new DefaultAsyncProducer(this) {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    if ("after".equals(exchange.getMessage().getHeader("mode"))) {
                        exchange.getMessage().setBody("Done");
                        callback.done(true);
                    }
                    throw new IllegalStateException("Simulated producer that throws");
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }
    }
}
