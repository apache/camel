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
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.transformer.DataTypeProcessor;
import org.apache.camel.processor.transformer.DelegatingDataTypeProcessor;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The processors of camel-core that call the callback in a finally block fail the exchange with an Error of the code
 * they call, as the processor adapters do (CAMEL-26741), instead of calling the callback and throwing the Error on: the
 * route would continue as if the step succeeded. A {@link VirtualMachineError} is left as before (thrown on after the
 * callback).
 */
class SiblingProcessorsThrowErrorTest extends ContextTestSupport {

    private static final AssertionError ERROR = new AssertionError("Simulated error of user code");
    private static final StackOverflowError SOE = new StackOverflowError("test");

    @Test
    void evaluateExpressionProcessorFailsTheExchangeWithAnError() {
        assertCallbackCalledOnceWithTheError(new EvaluateExpressionProcessor(throwingExpression(ERROR)));
    }

    @Test
    void evaluateExpressionProcessorThrowsAVirtualMachineErrorOnAfterTheCallback() {
        assertVirtualMachineErrorThrownOnAfterTheCallback(new EvaluateExpressionProcessor(throwingExpression(SOE)));
    }

    @Test
    void dataTypeProcessorFailsTheExchangeWithAnError() throws Exception {
        DataTypeProcessor processor = dataTypeProcessor(ERROR);
        try {
            assertCallbackCalledOnceWithTheError(processor);
        } finally {
            ServiceHelper.stopService(processor);
        }
    }

    @Test
    void dataTypeProcessorThrowsAVirtualMachineErrorOnAfterTheCallback() throws Exception {
        DataTypeProcessor processor = dataTypeProcessor(SOE);
        try {
            assertVirtualMachineErrorThrownOnAfterTheCallback(processor);
        } finally {
            ServiceHelper.stopService(processor);
        }
    }

    @Test
    void delegatingDataTypeProcessorFailsTheExchangeWithAnError() throws Exception {
        context.getTransformerRegistry().put(new TransformerKey(DataType.ANY, new DataType("failing")),
                new ThrowingTransformer(ERROR));
        DelegatingDataTypeProcessor processor = new DelegatingDataTypeProcessor();
        processor.setCamelContext(context);
        processor.start();
        try {
            Exchange exchange = createExchangeWithBody("Hello");
            exchange.setProperty("CamelDataType", "failing");
            List<Boolean> calls = new ArrayList<>();

            assertTrue(processor.process(exchange, calls::add));

            assertEquals(List.of(true), calls);
            assertFailedWithTheError(exchange.getException());
        } finally {
            ServiceHelper.stopService(processor);
        }
    }

    @Test
    void delegatingDataTypeProcessorCallsTheCallbackOnceWithoutTheDataTypeProperty() throws Exception {
        DelegatingDataTypeProcessor processor = new DelegatingDataTypeProcessor();
        processor.setCamelContext(context);
        processor.start();
        try {
            Exchange exchange = createExchangeWithBody("Hello");
            List<Boolean> calls = new ArrayList<>();

            assertTrue(processor.process(exchange, calls::add));

            assertEquals(List.of(true), calls, "The callback must be called once");
            assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        } finally {
            ServiceHelper.stopService(processor);
        }
    }

    @Test
    void synchronousSplitFailsTheExchangeWithAnErrorOfItsExpression() throws Exception {
        getMockEndpoint("mock:after").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        getMockEndpoint("mock:part").expectedMessageCount(0);

        Exchange out = template.send("direct:split", e -> e.getMessage().setBody("a,b"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException(), "onException must handle the Error");
        // the split completed its aggregation strategy (doDone), as for an exception
        assertNull(out.getProperty(ExchangePropertyKey.AGGREGATION_STRATEGY));
        Throwable caught = out.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertSame(ERROR, caught instanceof CamelExecutionException ? caught.getCause() : caught);
    }

    @Test
    void synchronousSplitFailsTheExchangeWithAnErrorOfItsAggregationStrategy() throws Exception {
        // the split task fails the exchange with the Error of the AggregationStrategy (see MulticastTaskThrowsErrorTest
        // for the other modes); the catch around the await manager of the synchronous split is still there for an Error
        // that leaves the task: either way the exchange fails instead of continuing after the callback
        getMockEndpoint("mock:part").expectedBodiesReceived("a");
        getMockEndpoint("mock:after").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:splitAggregate", e -> e.getMessage().setBody("a,b"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException(), "onException must handle the Error");
        Throwable caught = out.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        assertSame(ERROR, caught instanceof CamelExecutionException ? caught.getCause() : caught);
    }

    @Test
    void splitFailsTheExchangeWithAnErrorOfItsExpression() throws Exception {
        // the default (not synchronous) split: the Error failed the exchange through the engine before too, but skipped
        // the completion of the split
        getMockEndpoint("mock:part").expectedMessageCount(0);
        getMockEndpoint("mock:after").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:asyncSplit", e -> e.getMessage().setBody("a,b"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException(), "onException must handle the Error");
        // the split completed its aggregation strategy (doDone), as for an exception
        assertNull(out.getProperty(ExchangePropertyKey.AGGREGATION_STRATEGY));
    }

    @Test
    void recipientListFailsTheExchangeWithAnErrorOfItsExpression() throws Exception {
        getMockEndpoint("mock:after").expectedMessageCount(0);
        getMockEndpoint("mock:handled").expectedMessageCount(1);

        Exchange out = template.send("direct:recipientList", e -> e.getMessage().setBody("a"));

        assertMockEndpointsSatisfied();
        assertNull(out.getException(), "onException must handle the Error");
    }

    private DataTypeProcessor dataTypeProcessor(Error error) throws Exception {
        context.getTransformerRegistry().put(new TransformerKey(DataType.ANY, new DataType("failing")),
                new ThrowingTransformer(error));
        DataTypeProcessor processor = new DataTypeProcessor(null, "failing");
        processor.setCamelContext(context);
        processor.start();
        return processor;
    }

    private void assertCallbackCalledOnceWithTheError(AsyncProcessor processor) {
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> calls = new ArrayList<>();

        assertTrue(processor.process(exchange, calls::add));

        assertEquals(List.of(true), calls);
        assertFailedWithTheError(exchange.getException());
    }

    private void assertVirtualMachineErrorThrownOnAfterTheCallback(AsyncProcessor processor) {
        Exchange exchange = createExchangeWithBody("Hello");
        List<Boolean> calls = new ArrayList<>();

        StackOverflowError thrown = assertThrows(StackOverflowError.class, () -> processor.process(exchange, calls::add));

        assertSame(SOE, thrown);
        assertEquals(List.of(true), calls);
        // the current behaviour of a VirtualMachineError (as in ProcessorThrowsErrorTest), not a contract
        assertNull(exchange.getException());
    }

    private static void assertFailedWithTheError(Exception exception) {
        // the exchange keeps an Error wrapped in a CamelExecutionException
        assertSame(ERROR, assertInstanceOf(CamelExecutionException.class, exception).getCause());
    }

    private static Expression throwingExpression(Error error) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                throw error;
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split")
                        .onException(AssertionError.class).handled(true).to("mock:handled").end()
                        .split(throwingExpression(ERROR)).synchronous()
                            .to("mock:part")
                        .end()
                        .to("mock:after");

                from("direct:splitAggregate")
                        .onException(AssertionError.class).handled(true).to("mock:handled").end()
                        .split(body().tokenize(","), (oldExchange, newExchange) -> {
                            throw ERROR;
                        }).synchronous()
                        .to("mock:part")
                        .end()
                        .to("mock:after");

                from("direct:asyncSplit")
                        .onException(AssertionError.class).handled(true).to("mock:handled").end()
                        .split(throwingExpression(ERROR))
                            .to("mock:part")
                        .end()
                        .to("mock:after");

                from("direct:recipientList")
                        .onException(AssertionError.class).handled(true).to("mock:handled").end()
                        .recipientList(throwingExpression(ERROR))
                        .to("mock:after");
            }
        };
    }

    private static final class ThrowingTransformer extends Transformer {
        private final Error error;

        ThrowingTransformer(Error error) {
            this.error = error;
        }

        @Override
        public void transform(Message message, DataType from, DataType to) {
            throw error;
        }
    }
}
