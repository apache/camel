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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain {@link Processor} may throw an {@link Error}, such as an {@link AssertionError} of a test. The adapters that
 * call it as an {@link AsyncProcessor} must then fail the exchange with it and call the callback once, as for an
 * exception, instead of calling the callback and throwing the error on. A {@link VirtualMachineError} (such as an
 * {@link OutOfMemoryError} or a {@link StackOverflowError}) fails the exchange too (CAMEL-27515), but Camel does not
 * recover from it on its own: only an onException or a doCatch that names it or a supertype, as a Java catch, handles
 * it; onException(Exception.class), the dead letter channel and redelivery do not.
 */
public class ProcessorThrowsErrorTest extends ContextTestSupport {

    private static final ProcessingError ERROR = new ProcessingError();
    // an OutOfMemoryError that reaches JUnit aborts the whole test run, so it is only thrown where assertThrows expects it
    private static final OutOfMemoryError OOM = new OutOfMemoryError("test");
    private static final StackOverflowError SOE = new StackOverflowError("test");

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    public void processorThatThrowsAnErrorFailsTheExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:process", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void errorHandlingAppliesToAnErrorOfAProcessor() throws Exception {
        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:handled", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void doCatchCatchesAnErrorOfAProcessor() throws Exception {
        getMockEndpoint("mock:caught").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:try", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void beanThatIsAProcessorAndThrowsAnErrorFailsTheExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:bean", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void delegateSyncProcessorCallsTheCallbackOnceWithTheError() {
        assertCallbackCalledOnceWithTheError(new DelegateSyncProcessor(new ThrowingProcessor()));
    }

    @Test
    public void convertedProcessorCallsTheCallbackOnceWithTheError() {
        assertCallbackCalledOnceWithTheError(AsyncProcessorConverterHelper.convert(new ThrowingProcessor()));
    }

    @Test
    public void stackOverflowErrorOfAProcessorIsHandledByOnExceptionOfThrowable() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:vm-process", e -> e.getMessage().setBody("Hello"));

        // the route names a supertype of the Error, as a Java catch(Throwable): it handles it
        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void stackOverflowErrorOfAProcessorIsNotHandledByOnExceptionOfException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-process-exception", e -> e.getMessage().setBody("Hello"));

        // an Exception clause does not catch an Error, also when it is wrapped in a CamelExecutionException
        assertFailedWith(SOE, out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void outOfMemoryErrorOfAProcessorIsCaughtByDoCatchOfThrowable() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(1);

        Exchange out = template.send("direct:vm-try", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void outOfMemoryErrorOfAProcessorIsNotCaughtByDoCatchOfException() throws Exception {
        getMockEndpoint("mock:caught").expectedMessageCount(0);
        getMockEndpoint("mock:finally").expectedMessageCount(1);

        Exchange out = template.send("direct:vm-try-exception", e -> e.getMessage().setBody("Hello"));

        // not caught, but the doFinally runs, as a Java finally
        assertFailedWith(OOM, out.getException());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void stackOverflowErrorOfAProcessorIsNotRedeliveredNorGivenToTheDeadLetterChannel() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-dlc", e -> e.getMessage().setBody("Hello"));

        assertFailedWith(SOE, out.getException());
        assertEquals(1, calls.get(), "The exchange was redelivered");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void stackOverflowErrorOfABeanThatIsAProcessorIsHandledByOnExceptionOfThrowable() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:vm-bean", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void stackOverflowErrorOfAProducerIsHandledByOnExceptionOfThrowable() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:vm-producer", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void restOfTheRouteDoesNotRunLaterAfterAStackOverflowError() throws Exception {
        getMockEndpoint("mock:after").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);

        CamelExecutionException thrown = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:vm-two-steps", "Hello"));
        assertSame(SOE, thrown.getCause());
        // another exchange on the same thread: before CAMEL-27515 it ran the second step of the failed exchange
        template.sendBody("direct:vm-other", "Bye");

        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size());
    }

    @Test
    public void adaptersFailTheExchangeWithAVirtualMachineError() {
        assertFailsTheExchangeWithAVirtualMachineError(new DelegateSyncProcessor(new ThrowingProcessor(OOM, calls)));
        assertFailsTheExchangeWithAVirtualMachineError(
                AsyncProcessorConverterHelper.convert(new ThrowingProcessor(OOM, calls)));
    }

    private void assertFailsTheExchangeWithAVirtualMachineError(AsyncProcessor processor) {
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> done = new ArrayList<>();

        boolean sync = processor.process(exchange, done::add);

        assertTrue(sync);
        assertEquals(List.of(true), done);
        assertFailedWith(OOM, exchange.getException());
    }

    private static void assertFailedWith(Throwable error, Exception exception) {
        // the exchange keeps an Error wrapped in a CamelExecutionException
        assertIsInstanceOf(CamelExecutionException.class, exception);
        assertSame(error, exception.getCause());
    }

    private void assertCallbackCalledOnceWithTheError(AsyncProcessor processor) {
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> calls = new ArrayList<>();

        boolean sync = processor.process(exchange, calls::add);

        assertTrue(sync);
        assertEquals(List.of(true), calls);
        assertFailedWithTheError(exchange.getException());
    }

    private static void assertFailedWithTheError(Exception exception) {
        // the exchange keeps an Error wrapped in a CamelExecutionException
        assertIsInstanceOf(CamelExecutionException.class, exception);
        assertSame(ERROR, exception.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:process")
                        .process(new ThrowingProcessor())
                        .to("mock:result");

                from("direct:handled")
                        .onException(ProcessingError.class).handled(true).to("mock:handled").end()
                        .process(new ThrowingProcessor())
                        .to("mock:result");

                from("direct:try")
                        .doTry()
                            .process(new ThrowingProcessor())
                            .to("mock:result")
                        .doCatch(ProcessingError.class)
                            .to("mock:caught")
                        .end();

                from("direct:bean")
                        .bean(new ThrowingProcessor())
                        .to("mock:result");

                from("direct:vm-process")
                        .onException(Throwable.class).handled(true).to("mock:handled").end()
                        .process(new ThrowingProcessor(SOE, calls));

                from("direct:vm-try")
                        .doTry()
                            .process(new ThrowingProcessor(OOM, calls))
                        .doCatch(Throwable.class)
                            .to("mock:caught")
                        .end();

                from("direct:vm-process-exception")
                        .onException(Exception.class).handled(true).to("mock:handled").end()
                        .process(new ThrowingProcessor(SOE, calls));

                from("direct:vm-try-exception")
                        .doTry()
                            .process(new ThrowingProcessor(OOM, calls))
                        .doCatch(Exception.class)
                            .to("mock:caught")
                        .doFinally()
                            .to("mock:finally")
                        .end();

                from("direct:vm-two-steps")
                        .process(new ThrowingProcessor(SOE, calls))
                        .to("mock:after");

                from("direct:vm-other")
                        .to("mock:other");

                from("direct:vm-dlc")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(3).redeliveryDelay(0))
                        .process(new ThrowingProcessor(SOE, calls));

                from("direct:vm-bean")
                        .onException(Throwable.class).handled(true).to("mock:handled").end()
                        .bean(new ThrowingProcessor(SOE, calls));

                getContext().addEndpoint("throwing:producer", new ThrowingProducerEndpoint(calls));
                from("direct:vm-producer")
                        .onException(Throwable.class).handled(true).to("mock:handled").end()
                        .to("throwing:producer");
            }
        };
    }

    private static final class ThrowingProcessor implements Processor {
        private final Error error;
        private final AtomicInteger calls;

        ThrowingProcessor() {
            this(ERROR, new AtomicInteger());
        }

        ThrowingProcessor(Error error, AtomicInteger calls) {
            this.error = error;
            this.calls = calls;
        }

        @Override
        public void process(Exchange exchange) {
            calls.incrementAndGet();
            throw error;
        }
    }

    /**
     * An endpoint whose producer is not an AsyncProcessor, so it is called through the bridge of
     * AsyncProcessorConverterHelper.
     */
    private static final class ThrowingProducerEndpoint extends DefaultEndpoint {
        private final AtomicInteger calls;

        ThrowingProducerEndpoint(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    calls.incrementAndGet();
                    throw SOE;
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException("Producer only");
        }

        @Override
        protected String createEndpointUri() {
            return "throwing:producer";
        }
    }

    private static final class ProcessingError extends Error {
        private static final long serialVersionUID = 1L;

        ProcessingError() {
            super("Simulated error of a processor");
        }
    }
}
