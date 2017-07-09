/**
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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * To test CAMEL-4037 that a restart of aggregator can re-initialize the timeout map
 *
 * @version 
 */
public class AggregateProcessorTimeoutCompletionRestartTest extends ContextTestSupport {

    private ExecutorService executorService;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void testAggregateProcessorTimeoutRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        // start with a high timeout so no completes before we stop
        ap.setCompletionTimeout(250);
        ap.setCompletionTimeoutCheckerInterval(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);

        ap.process(e1);
        ap.process(e2);

        // shutdown before the 1/4 sec timeout occurs
        // however we use stop instead of shutdown as shutdown will clear the in memory aggregation repository,
        ap.stop();

        // should be no completed
        assertEquals(0, mock.getReceivedCounter());

        // start aggregator again
        ap.start();

        // the aggregator should restore the timeout condition and trigger timeout
        assertMockEndpointsSatisfied();
        assertEquals(1, mock.getReceivedCounter());

        ap.shutdown();
    }

    public void testAggregateProcessorTimeoutExpressionRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        // start with a high timeout so no completes before we stop
        ap.setCompletionTimeoutExpression(header("myTimeout"));
        ap.setCompletionTimeoutCheckerInterval(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);
        e1.getIn().setHeader("myTimeout", 250);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);
        e2.getIn().setHeader("myTimeout", 250);

        ap.process(e1);
        ap.process(e2);

        // shutdown before the 1/4 sec timeout occurs
        // however we use stop instead of shutdown as shutdown will clear the in memory aggregation repository,
        ap.stop();

        // should be no completed
        assertEquals(0, mock.getReceivedCounter());

        // start aggregator again
        ap.start();

        // the aggregator should restore the timeout condition and trigger timeout
        assertMockEndpointsSatisfied();
        assertEquals(1, mock.getReceivedCounter());

        ap.shutdown();
    }

    public void testAggregateProcessorTwoTimeoutExpressionRestart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("C+D", "A+B");
        mock.expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(context, done, corr, as, executorService, true);
        // start with a high timeout so no completes before we stop
        ap.setCompletionTimeoutExpression(header("myTimeout"));
        ap.setCompletionTimeoutCheckerInterval(10);
        ap.start();

        Exchange e1 = new DefaultExchange(context);
        e1.getIn().setBody("A");
        e1.getIn().setHeader("id", 123);
        e1.getIn().setHeader("myTimeout", 300);

        Exchange e2 = new DefaultExchange(context);
        e2.getIn().setBody("B");
        e2.getIn().setHeader("id", 123);
        e2.getIn().setHeader("myTimeout", 300);

        Exchange e3 = new DefaultExchange(context);
        e3.getIn().setBody("C");
        e3.getIn().setHeader("id", 456);
        e3.getIn().setHeader("myTimeout", 250);

        Exchange e4 = new DefaultExchange(context);
        e4.getIn().setBody("D");
        e4.getIn().setHeader("id", 456);
        e4.getIn().setHeader("myTimeout", 250);

        ap.process(e1);
        ap.process(e2);
        ap.process(e3);
        ap.process(e4);

        // shutdown before the 1/4 sec timeout occurs
        // however we use stop instead of shutdown as shutdown will clear the in memory aggregation repository,
        ap.stop();

        // should be no completed
        assertEquals(0, mock.getReceivedCounter());

        // start aggregator again
        ap.start();

        // the aggregator should restore the timeout condition and trigger timeout
        assertMockEndpointsSatisfied();
        assertEquals(2, mock.getReceivedCounter());

        ap.shutdown();
    }
}
