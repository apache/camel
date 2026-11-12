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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
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
 * {@link OutOfMemoryError} or a {@link StackOverflowError}) is left as before: the adapters do not set it on the
 * exchange, so Camel's error handling (onException, doCatch, the dead letter channel, redelivery) does not get it.
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
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    public void stackOverflowErrorOfAProcessorIsNotGivenToOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-process", e -> e.getMessage().setBody("Hello"));

        // as before: the error handler logs the Error and continues the exchange, without onException
        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    public void outOfMemoryErrorOfAProcessorIsNotGivenToDoCatch() throws Exception {
        // as before, the exchange stays inflight, so do not wait for it when the context stops
        shortShutdownTimeout();
        getMockEndpoint("mock:caught").expectedMessageCount(0);

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                () -> template.send("direct:vm-try", e -> e.getMessage().setBody("Hello")));

        assertSame(OOM, thrown);
        assertMockEndpointsSatisfied();
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    public void stackOverflowErrorOfAProcessorIsNotRedeliveredNorGivenToTheDeadLetterChannel() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-dlc", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get(), "The exchange was redelivered");
        assertMockEndpointsSatisfied();
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    public void stackOverflowErrorOfABeanThatIsAProcessorIsNotGivenToOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-bean", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_AFTER_CALLBACK)
    public void stackOverflowErrorOfAProducerIsNotGivenToOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-producer", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void adaptersThrowAVirtualMachineErrorOnAfterTheCallback() {
        assertVirtualMachineErrorThrownOnAfterTheCallback(new DelegateSyncProcessor(new ThrowingProcessor(OOM, calls)));
        assertVirtualMachineErrorThrownOnAfterTheCallback(
                AsyncProcessorConverterHelper.convert(new ThrowingProcessor(OOM, calls)));
    }

    private void assertVirtualMachineErrorThrownOnAfterTheCallback(AsyncProcessor processor) {
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> done = new ArrayList<>();

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class, () -> processor.process(exchange, done::add));

        assertSame(OOM, thrown);
        assertEquals(List.of(true), done);
        assertNull(exchange.getException());
    }

    private void shortShutdownTimeout() {
        context.getShutdownStrategy().setTimeUnit(TimeUnit.MILLISECONDS);
        context.getShutdownStrategy().setTimeout(100);
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
