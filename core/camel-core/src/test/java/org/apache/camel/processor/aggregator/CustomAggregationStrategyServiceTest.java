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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

public class CustomAggregationStrategyServiceTest extends ContextTestSupport {

    private MyCustomStrategy strategy = new MyCustomStrategy();

    @Test
    public void testCustomAggregationStrategy() throws Exception {
        assertTrue("Should be started", strategy.start);
        assertFalse("Should not be stopped", strategy.stop);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "100", "id", "1");
        template.sendBodyAndHeader("direct:start", "150", "id", "1");
        template.sendBodyAndHeader("direct:start", "130", "id", "1");

        assertMockEndpointsSatisfied();

        // stop Camel
        context.stop();

        assertFalse("Should not be started", strategy.start);
        assertTrue("Should be stopped", strategy.stop);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(strategy).header("id").completionSize(3).to("mock:result");
            }
        };
    }

    public final class MyCustomStrategy extends ServiceSupport implements AggregationStrategy {

        public boolean stop;
        public boolean start;

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            return newExchange;
        }

        @Override
        protected void doStart() throws Exception {
            start = true;
            stop = false;
        }

        @Override
        protected void doStop() throws Exception {
            stop = true;
            start = false;
        }
    }

}
