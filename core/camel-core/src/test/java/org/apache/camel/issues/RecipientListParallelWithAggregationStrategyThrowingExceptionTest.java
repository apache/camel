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
package org.apache.camel.issues;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Tests the issue stated in
 * <a href="https://issues.apache.org/jira/browse/CAMEL-10272">CAMEL-10272</a>.
 */
public class RecipientListParallelWithAggregationStrategyThrowingExceptionTest extends ContextTestSupport {

    @Test
    public void testAggregationTimeExceptionWithParallelProcessing() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "recipients", "mock:a,mock:b");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                // must use share UoW if we want the error handler to react on
                // exceptions
                // from the aggregation strategy also.
                from("direct:start").recipientList(header("recipients")).aggregationStrategy(new MyAggregateBean()).parallelProcessing().stopOnAggregateException()
                    .shareUnitOfWork().end().to("mock:end");
            }
        };
    }

    public static class MyAggregateBean implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            throw new RuntimeException("Simulating a runtime exception thrown from the aggregation strategy");
        }
    }
}
