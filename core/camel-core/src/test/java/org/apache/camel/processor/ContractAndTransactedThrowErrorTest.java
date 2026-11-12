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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.CamelInternalProcessor;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follow-up of CAMEL-26741 and CAMEL-26824: an Error that is not a VirtualMachineError, thrown by a transformer of the
 * input or output type of a route (ContractAdvice) or by the processor of a transacted exchange
 * (CamelInternalProcessor.processTransacted), fails the exchange as an exception does. A VirtualMachineError is left as
 * before.
 */
class ContractAndTransactedThrowErrorTest extends ContextTestSupport {

    @Test
    void errorOfAnInputTransformerFailsTheExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:input", exchangeOf("other:Failing"));

        assertFailedWithAssertionError(out);
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "the exchange must not stay inflight");
    }

    @Test
    void exceptionOfAnInputTransformerFailsTheExchangeTheSameWay() throws Exception {
        // control: as before, for an exception
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:input", exchangeOf("other:Throwing"));

        assertInstanceOf(IllegalStateException.class, out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    void errorOfAnOutputTransformerFailsTheExchange() throws Exception {
        getMockEndpoint("mock:output").expectedMessageCount(1);

        Exchange out = template.send("direct:output", exchangeOf("other:Good"));

        assertFailedWithAssertionError(out);
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "the exchange must not stay inflight");
    }

    @Test
    void doCatchCatchesAnErrorOfAProcessorOfATransactedExchange() throws Exception {
        // a step without an error handler of its own (here in a doTry) calls a plain Processor synchronously when the
        // exchange is transacted: the Error must fail the exchange there, so the doCatch gets it, and the next step of
        // the doTry does not run
        getMockEndpoint("mock:afterError").expectedMessageCount(0);
        getMockEndpoint("mock:caught").expectedMessageCount(1);

        Exchange out = template.asyncSend("direct:transacted", e -> {
            e.getMessage().setBody("Hello");
            e.getExchangeExtension().setTransacted(true);
        }).get(10, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
        assertInstanceOf(AssertionError.class,
                out.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class).getCause());
    }

    @Test
    void errorOfTheProcessorOfATransactedExchangeFailsTheExchange() throws Exception {
        CamelInternalProcessor internal
                = new CamelInternalProcessor(context, new ThrowingProcessor(new AssertionError("in a transaction")));
        ServiceHelper.startService(internal);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getExchangeExtension().setTransacted(true);
            AtomicInteger done = new AtomicInteger();

            assertTrue(internal.process(exchange, doneSync -> done.incrementAndGet()));

            assertEquals(1, done.get(), "the callback must be called once");
            assertFailedWithAssertionError(exchange);
        } finally {
            ServiceHelper.stopService(internal);
        }
    }

    @Test
    void virtualMachineErrorOfTheProcessorOfATransactedExchangeIsLeftAsBefore() throws Exception {
        CamelInternalProcessor internal = new CamelInternalProcessor(context, new ThrowingProcessor(new StackOverflowError()));
        ServiceHelper.startService(internal);
        try {
            Exchange exchange = new DefaultExchange(context);
            exchange.getExchangeExtension().setTransacted(true);
            AtomicInteger done = new AtomicInteger();

            // thrown on after the callback was called, as before
            assertThrows(StackOverflowError.class, () -> internal.process(exchange, doneSync -> done.incrementAndGet()));
            assertEquals(1, done.get());
        } finally {
            ServiceHelper.stopService(internal);
        }
    }

    private static void assertFailedWithAssertionError(Exchange exchange) {
        // the exchange fails with the Error, wrapped as for the processors of CAMEL-26741
        CamelExecutionException e = assertInstanceOf(CamelExecutionException.class, exchange.getException());
        assertInstanceOf(AssertionError.class, e.getCause());
    }

    private Exchange exchangeOf(String type) {
        Exchange exchange = new DefaultExchange(context);
        ((DataTypeAware) exchange.getIn()).setBody("Hello", new DataType(type));
        return exchange;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                transformer().fromType("other:Failing").toType("other:Good").withJava(FailingTransformer.class);
                transformer().fromType("other:Throwing").toType("other:Good").withJava(ThrowingTransformer.class);
                transformer().fromType("other:Good").toType("other:Out").withJava(FailingTransformer.class);

                from("direct:input").inputType("other:Good").to("mock:result");
                from("direct:output").inputType("other:Good").outputType("other:Out").to("mock:output");

                from("direct:transacted")
                        .doTry()
                        .process(e -> {
                            throw new AssertionError("in a transaction");
                        })
                        .to("mock:afterError")
                        .doCatch(AssertionError.class)
                        .to("mock:caught")
                        .end();
            }
        };
    }

    public static class FailingTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) {
            throw new AssertionError("transformer failed");
        }
    }

    public static class ThrowingTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) {
            throw new IllegalStateException("transformer failed");
        }
    }

    /** An AsyncProcessor whose synchronous process (used for a transacted exchange) throws the given Error. */
    private static final class ThrowingProcessor implements AsyncProcessor {
        private final Error error;

        ThrowingProcessor(Error error) {
            this.error = error;
        }

        @Override
        public void process(Exchange exchange) {
            throw error;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            throw error;
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            throw error;
        }
    }
}
