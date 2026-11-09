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

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain {@link Processor} may throw an {@link Error}, such as an {@link AssertionError} of a test. The adapters that
 * call it as an {@link AsyncProcessor} must then fail the exchange with it and call the callback once, as for an
 * exception, instead of calling the callback and throwing the error on.
 */
public class ProcessorThrowsErrorTest extends ContextTestSupport {

    private static final ProcessingError ERROR = new ProcessingError();

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
            public void configure() {
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
            }
        };
    }

    private static final class ThrowingProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            throw ERROR;
        }
    }

    private static final class ProcessingError extends Error {
        private static final long serialVersionUID = 1L;

        ProcessingError() {
            super("Simulated error of a processor");
        }
    }
}
