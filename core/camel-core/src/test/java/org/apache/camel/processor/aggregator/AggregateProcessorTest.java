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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
@Isolated
public class AggregateProcessorTest extends ContextTestSupport {

    private ExecutorService executorService;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        executorService = Executors.newSingleThreadExecutor();
    }

    @Test
    public void testAggregateProcessorCompletionPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+END");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "predicate");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().contains("END");

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionPredicate(complete);
        ap.setEagerCheckCompletion(false);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("END");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateProcessorCompletionPredicateEager() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+END");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "predicate");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().isEqualTo("END");

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionPredicate(complete);
        ap.setEagerCheckCompletion(true);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("END");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateProcessorCompletionAggregatedSize() throws Exception {
        doTestAggregateProcessorCompletionAggregatedSize(false);
    }

    @Test
    public void testAggregateProcessorCompletionAggregatedSizeEager() throws Exception {
        doTestAggregateProcessorCompletionAggregatedSize(true);
    }

    private void doTestAggregateProcessorCompletionAggregatedSize(boolean eager) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "size");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionSize(3);
        ap.setEagerCheckCompletion(eager);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateProcessorCompletionTimeout() throws Exception {
        doTestAggregateProcessorCompletionTimeout(false);
    }

    @Test
    public void testAggregateProcessorCompletionTimeoutEager() throws Exception {
        doTestAggregateProcessorCompletionTimeout(true);
    }

    private void doTestAggregateProcessorCompletionTimeout(boolean eager) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionTimeout(100);
        ap.setEagerCheckCompletion(eager);
        ap.setCompletionTimeoutCheckerInterval(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        Thread.sleep(5);
        ap.process(e2);
        Thread.sleep(10);
        ap.process(e3);

        Thread.sleep(150);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateCompletionInterval() throws Exception {
        // camel context must be started
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C", "D");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "interval");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionInterval(100);
        ap.setCompletionTimeoutCheckerInterval(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);

        Thread.sleep(250);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateIgnoreInvalidCorrelationKey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+C+END");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().contains("END");

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionPredicate(complete);
        ap.setIgnoreInvalidCorrelationKeys(true);

        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("END");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateBadCorrelationKey() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+C+END");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().contains("END");

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionPredicate(complete);

        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("END");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);

        ap.process(e2);
        Exception e = e2.getException();
        assertNotNull(e);
        assertTrue(e.getMessage().startsWith("Invalid correlation key."));

        ap.process(e3);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateCloseCorrelationKeyOnCompletion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+END");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().contains("END");

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionPredicate(complete);
        ap.setCloseCorrelationKeyOnCompletion(1000);

        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("END");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("C");
        e4.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);

        ap.process(e4);
        Exception e = e4.getException();
        assertNotNull(e);
        assertTrue(e.getMessage().startsWith("The correlation key [123] has been closed."));

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateUseBatchSizeFromConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B", "C+D+E");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "consumer");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionSize(100);
        ap.setCompletionFromBatchConsumer(true);

        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);
        e1.setProperty(Exchange.BATCH_INDEX, 0);
        e1.setProperty(Exchange.BATCH_SIZE, 2);
        e1.setProperty(Exchange.BATCH_COMPLETE, false);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);
        e2.setProperty(Exchange.BATCH_INDEX, 1);
        e2.setProperty(Exchange.BATCH_SIZE, 2);
        e2.setProperty(Exchange.BATCH_COMPLETE, true);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 123);
        e3.setProperty(Exchange.BATCH_INDEX, 0);
        e3.setProperty(Exchange.BATCH_SIZE, 3);
        e3.setProperty(Exchange.BATCH_COMPLETE, false);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 123);
        e4.setProperty(Exchange.BATCH_INDEX, 1);
        e4.setProperty(Exchange.BATCH_SIZE, 3);
        e4.setProperty(Exchange.BATCH_COMPLETE, false);

        Exchange e5 = new DefaultExchange(context);
        e5.getIn().setBody("E");
        e5.getIn().setHeader("id", 123);
        e5.setProperty(Exchange.BATCH_INDEX, 2);
        e5.setProperty(Exchange.BATCH_SIZE, 3);
        e5.setProperty(Exchange.BATCH_COMPLETE, true);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);
        ap.process(e5);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateLogFailedExchange() throws Exception {
        doTestAggregateLogFailedExchange(null);
    }

    @Test
    public void testAggregateHandleFailedExchange() throws Exception {
        final AtomicBoolean tested = new AtomicBoolean();

        ExceptionHandler myHandler = new ExceptionHandler() {
            public void handleException(Throwable exception) {
            }

            public void handleException(String message, Throwable exception) {
            }

            public void handleException(String message, Exchange exchange, Throwable exception) {
                assertEquals("Error processing aggregated exchange", message);
                assertEquals("B+Kaboom+END", exchange.getIn().getBody());
                assertEquals("Damn", exception.getMessage());
                tested.set(true);
            }
        };

        doTestAggregateLogFailedExchange(myHandler);
        assertTrue(tested.get());
    }

    private void doTestAggregateLogFailedExchange(ExceptionHandler handler) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+END");

        AsyncProcessor done = new AsyncProcessorSupport() {
            public boolean process(Exchange exchange, AsyncCallback callback) {
                try {
                    if (exchange.getIn().getBody(String.class).contains("Kaboom")) {
                        throw new IllegalArgumentException("Damn");
                    } else {
                        SendProcessor send = new SendProcessor(context.getEndpoint("mock:result"));
                        send.start();
                        send.process(exchange, callback);
                    }
                } catch (Exception e) {
                    exchange.setException(e);
                    callback.done(false);
                }
                return false;
            }
        };

        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setEagerCheckCompletion(true);
        ap.setCompletionPredicate(body().isEqualTo("END"));
        if (handler != null) {
            ap.setExceptionHandler(handler);
        }
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 456);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("Kaboom");
        e3.getIn().setHeader("id", 456);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("END");
        e4.getIn().setHeader("id", 456);

        Exchange e5 = new DefaultExchange(context);
        e5.getIn().setBody("END");
        e5.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);
        ap.process(e5);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

    @Test
    public void testAggregateForceCompletion() throws Exception {
        // camel context must be started
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("B+END", "A+END");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "force");

        AsyncProcessor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        ap.setCompletionSize(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 456);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("END");
        e3.getIn().setHeader("id", 123);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("END");
        e4.getIn().setHeader("id", 456);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        assertEquals(0, mock.getExchanges().size(), "should not have completed yet");

        ap.forceCompletionOfAllGroups();

        assertMockEndpointsSatisfied();

        ap.stop();
    }

}
