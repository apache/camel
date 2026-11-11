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
package org.apache.camel.component.bean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bean method that throws an {@link Error} is handled as a bean that is a {@link org.apache.camel.Processor}: an
 * {@link Error} such as an {@link AssertionError} fails the exchange and the error handler gets it, while a
 * {@link VirtualMachineError} (such as an {@link OutOfMemoryError} or a {@link StackOverflowError}) is not given to
 * Camel's error handling (onException, doCatch, the dead letter channel, redelivery) but thrown on after the callback.
 */
public class BeanMethodThrowsErrorTest extends ContextTestSupport {

    private static final ProcessingError ERROR = new ProcessingError();
    // an OutOfMemoryError that reaches JUnit aborts the whole test run, so it is only thrown where assertThrows expects it
    private static final OutOfMemoryError OOM = new OutOfMemoryError("test");
    private static final StackOverflowError SOE = new StackOverflowError("test");

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    public void beanMethodThatThrowsAnErrorFailsTheExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:error", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void errorHandlingAppliesToAnErrorOfABeanMethod() throws Exception {
        getMockEndpoint("mock:handled").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:handled", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
        assertMockEndpointsSatisfied();
    }

    @Test
    public void errorOfACallableReturnedByABeanMethodFailsTheExchange() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exchange out = template.send("direct:callable", e -> e.getMessage().setBody("Hello"));

        assertFailedWithTheError(out.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size(), "The exchange is still inflight");
    }

    @Test
    public void stackOverflowErrorOfABeanMethodIsNotGivenToOnException() throws Exception {
        getMockEndpoint("mock:handled").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-handled", e -> e.getMessage().setBody("Hello"));

        // as a bean that is a Processor: the error handler logs the Error and continues the exchange, without onException
        assertNull(out.getException());
        assertEquals(1, calls.get());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void outOfMemoryErrorOfABeanMethodIsNotGivenToDoCatch() throws Exception {
        // as a bean that is a Processor, the exchange stays inflight, so do not wait for it when the context stops
        context.getShutdownStrategy().setTimeUnit(TimeUnit.MILLISECONDS);
        context.getShutdownStrategy().setTimeout(100);
        getMockEndpoint("mock:caught").expectedMessageCount(0);

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class,
                () -> template.send("direct:vm-try", e -> e.getMessage().setBody("Hello")));

        assertSame(OOM, thrown);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void stackOverflowErrorOfABeanMethodIsNotRedeliveredNorGivenToTheDeadLetterChannel() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(0);

        Exchange out = template.send("direct:vm-dlc", e -> e.getMessage().setBody("Hello"));

        assertNull(out.getException());
        assertEquals(1, calls.get(), "The exchange was redelivered");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void beanProcessorCallsTheCallbackOnceWithTheError() throws Exception {
        BeanProcessor processor = startedBeanProcessor(new ThrowingBean(ERROR, calls));
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> done = new ArrayList<>();

        boolean sync = processor.process(exchange, done::add);

        assertTrue(sync);
        assertEquals(List.of(true), done);
        assertFailedWithTheError(exchange.getException());
    }

    @Test
    public void beanProcessorThrowsAVirtualMachineErrorOnAfterTheCallback() throws Exception {
        BeanProcessor processor = startedBeanProcessor(new ThrowingBean(OOM, calls));
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> done = new ArrayList<>();

        OutOfMemoryError thrown = assertThrows(OutOfMemoryError.class, () -> processor.process(exchange, done::add));

        assertSame(OOM, thrown);
        assertEquals(List.of(true), done);
        assertNull(exchange.getException());
    }

    private BeanProcessor startedBeanProcessor(Object bean) {
        BeanProcessor processor = new BeanProcessor(bean, context);
        processor.setMethod("doSomething");
        ServiceHelper.startService(processor);
        return processor;
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
            public void configure() {
                from("direct:error")
                        .bean(new ThrowingBean(ERROR, calls), "doSomething")
                        .to("mock:result");

                from("direct:handled")
                        .onException(ProcessingError.class).handled(true).to("mock:handled").end()
                        .bean(new ThrowingBean(ERROR, calls), "doSomething")
                        .to("mock:result");

                from("direct:callable")
                        .bean(new ThrowingBean(ERROR, calls), "callable")
                        .to("mock:result");

                from("direct:vm-handled")
                        .onException(Throwable.class).handled(true).to("mock:handled").end()
                        .bean(new ThrowingBean(SOE, calls), "doSomething");

                from("direct:vm-try")
                        .doTry()
                            .bean(new ThrowingBean(OOM, calls), "doSomething")
                        .doCatch(Throwable.class)
                            .to("mock:caught")
                        .end();

                from("direct:vm-dlc")
                        .errorHandler(deadLetterChannel("mock:dead").maximumRedeliveries(3).redeliveryDelay(0))
                        .bean(new ThrowingBean(SOE, calls), "doSomething");
            }
        };
    }

    public static final class ThrowingBean {
        private final Error error;
        private final AtomicInteger calls;

        ThrowingBean(Error error, AtomicInteger calls) {
            this.error = error;
            this.calls = calls;
        }

        public void doSomething(String body) {
            calls.incrementAndGet();
            throw error;
        }

        public Callable<String> callable(String body) {
            // the Callable is called by the bean component after the method returned, outside the reflective call
            return () -> {
                calls.incrementAndGet();
                throw error;
            };
        }
    }

    private static final class ProcessingError extends Error {
        private static final long serialVersionUID = 1L;

        ProcessingError() {
            super("Simulated error of a bean method");
        }
    }
}
