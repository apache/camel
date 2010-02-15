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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * @version $Revision$
 */
public class AggregateProcessorTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testAggregateProcessorCompletionPredicate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+END");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().contains("END");

        AggregateProcessor ap = new AggregateProcessor(done, corr, as);
        ap.setCompletionPredicate(complete);
        ap.setEagerEvaluateCompletionPredicate(false);
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

    public void testAggregateProcessorCompletionPredicateEager() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+END");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();
        Predicate complete = body().isEqualTo("END");

        AggregateProcessor ap = new AggregateProcessor(done, corr, as);
        ap.setCompletionPredicate(complete);
        ap.setEagerEvaluateCompletionPredicate(true);
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

    public void testAggregateProcessorCompletionAggregatedSize() throws Exception {
        doTestAggregateProcessorCompletionAggregatedSize(false);
    }

    public void testAggregateProcessorCompletionAggregatedSizeEager() throws Exception {
        doTestAggregateProcessorCompletionAggregatedSize(true);
    }

    private void doTestAggregateProcessorCompletionAggregatedSize(boolean eager) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(done, corr, as);
        ap.setCompletionAggregatedSize(3);
        ap.setEagerEvaluateCompletionPredicate(eager);
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

    public void testAggregateProcessorCompletionTimeout() throws Exception {
        doTestAggregateProcessorCompletionTimeout(false);
    }

    public void testAggregateProcessorCompletionTimeoutEager() throws Exception {
        doTestAggregateProcessorCompletionTimeout(true);
    }

    private void doTestAggregateProcessorCompletionTimeout(boolean eager) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A+B+C");

        Processor done = new SendProcessor(context.getEndpoint("mock:result"));
        Expression corr = header("id");
        AggregationStrategy as = new BodyInAggregatingStrategy();

        AggregateProcessor ap = new AggregateProcessor(done, corr, as);
        ap.setCompletionTimeout(3000);
        ap.setEagerEvaluateCompletionPredicate(eager);
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

        Thread.sleep(250);
        ap.process(e2);

        Thread.sleep(500);
        ap.process(e3);

        Thread.sleep(5000);
        ap.process(e4);

        assertMockEndpointsSatisfied();

        ap.stop();
    }

}
