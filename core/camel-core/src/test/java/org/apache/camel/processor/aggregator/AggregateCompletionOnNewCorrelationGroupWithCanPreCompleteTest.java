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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AggregateCompletionOnNewCorrelationGroupWithCanPreCompleteTest extends ContextTestSupport {

    @Test
    public void testCompletionOnNewCorrelationGroup() throws Exception {
        int numItems = 2000;

        getMockEndpoint("mock:aggregated").expectedMessageCount(numItems + 1);

        List<String> input = new ArrayList<>();

        for (int j = 0; j < numItems; j++) {
            for (int i = 0; i < 2; i++) {
                input.add("A" + j);
            }
        }

        input.add("C");
        input.add("C");
        input.add("C");
        input.add("end");

        template.sendBody("direct:start", input);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .split().body().streaming().parallelProcessing(false)
                        .process(exchange -> {
                            if ("end".equals(exchange.getIn().getBody())) {
                                // expectation: C is the last body to aggregate
                                exchange.getIn().setHeader("id", "C".hashCode());
                            } else {
                                exchange.getIn().setHeader("id", exchange.getIn().getBody().hashCode());
                            }
                        })
                        .aggregate(header("id"), new CanPreCompleteAggregationStrategy()).completionOnNewCorrelationGroup()
                        .to("log:aggregated", "mock:aggregated");
            }
        };
    }

    public static class CanPreCompleteAggregationStrategy implements AggregationStrategy {
        private static final Logger LOG = LoggerFactory.getLogger(CanPreCompleteAggregationStrategy.class);

        public CanPreCompleteAggregationStrategy() {
        }

        @Override
        public boolean canPreComplete() {
            return true;
        }

        @Override
        public boolean preComplete(Exchange oldExchange, Exchange newExchange) {
            boolean preComplete = false;

            String body1;
            String body2;
            String oldExchangeId;
            String newExchangeId;

            if (oldExchange == null) {
                oldExchangeId = null;
                newExchangeId = newExchange.getExchangeId();
                body1 = null;
                body2 = newExchange.getIn().getBody(String.class);
            } else {
                body1 = oldExchange.getIn().getBody(String.class);
                body2 = newExchange.getIn().getBody(String.class);

                oldExchangeId = oldExchange.getExchangeId();
                newExchangeId = newExchange.getExchangeId();
            }

            LOG.debug("preComplete body1[{}] body2[{}] [{}] [{}]", body1, body2,
                    oldExchangeId, newExchangeId);

            if (newExchange.getIn().getBody().equals("end")) {
                preComplete = true;
            }

            LOG.debug("preComplete[{}]", preComplete);
            return preComplete;
        }

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            LOG.debug("aggregate");

            if (oldExchange == null) {
                LOG.debug("aggregate oldExchange[{}] newExchangeId[{}]",
                        oldExchange,
                        newExchange.getExchangeId());
                return newExchange;
            }

            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);
            LOG.debug("aggregate body1[{}] body2[{}] [{}] [{}]", body1, body2,
                    oldExchange.getExchangeId(), newExchange.getExchangeId());

            oldExchange.getIn().setBody(body1 + body2);

            LOG.debug("aggregate [{}] [{}] [{}]", oldExchange.getIn().getBody(),
                    oldExchange.getExchangeId(), newExchange.getExchangeId());

            return oldExchange;
        }

        @Override
        public void onCompletion(Exchange exchange) {
            LOG.debug("onCompletion[{}]", exchange.getExchangeId());
        }
    }
}
