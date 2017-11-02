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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

public class AggregationStrategyLifecycleTest extends ContextTestSupport {

    private MyCompletionStrategy strategy = new MyCompletionStrategy();

    public void testAggregateLifecycle() throws Exception {
        assertTrue("Should be started", strategy.isStarted());
        assertSame(context, strategy.getCamelContext());

        MockEndpoint result = getMockEndpoint("mock:aggregated");
        result.expectedBodiesReceived("A+B+C");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        assertMockEndpointsSatisfied();

        context.stop();

        assertTrue("Should be stopped", strategy.isStopped());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), strategy).completionSize(3)
                    .to("mock:aggregated");
            }
        };
    }

    private final class MyCompletionStrategy extends ServiceSupport implements AggregationStrategy, CamelContextAware {

        private CamelContext camelContext;
        private String separator;

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            String body = oldExchange.getIn().getBody(String.class) + separator
                + newExchange.getIn().getBody(String.class);
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        protected void doStart() throws Exception {
            ObjectHelper.notNull(camelContext, "CamelContext");

            separator = "+";
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }
}
