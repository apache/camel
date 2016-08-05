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
package org.apache.camel.component.jcache.processor.aggregate;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.junit.Test;


public class JCacheAggregationRepositoryRoutesTest extends JCacheAggregationRepositoryTestSupport {

    private static final String MOCK_GOTCHA = "mock:gotcha";
    private static final String DIRECT_ONE = "direct:one";
    private static final String DIRECT_TWO = "direct:two";

    @EndpointInject(uri = MOCK_GOTCHA)
    private MockEndpoint mock;

    @Produce(uri = DIRECT_ONE)
    private ProducerTemplate produceOne;

    @Produce(uri = DIRECT_TWO)
    private ProducerTemplate produceTwo;

    @Test
    public void checkAggregationFromTwoRoutes() throws Exception {
        final JCacheAggregationRepository repoOne = createRepository(false);
        final JCacheAggregationRepository repoTwo = createRepository(false);

        final int completionSize = 4;
        final String correlator = "CORRELATOR";
        RouteBuilder rbOne = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(DIRECT_ONE).routeId("AggregatingRouteOne")
                    .aggregate(header(correlator))
                    .aggregationRepository(repoOne)
                    .aggregationStrategy(new MyAggregationStrategy())
                    .completionSize(completionSize)
                    .to(MOCK_GOTCHA);
            }
        };

        RouteBuilder rbTwo = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(DIRECT_TWO).routeId("AggregatingRouteTwo")
                    .aggregate(header(correlator))
                    .aggregationRepository(repoTwo)
                    .aggregationStrategy(new MyAggregationStrategy())
                    .completionSize(completionSize)
                    .to(MOCK_GOTCHA);
            }
        };

        context().addRoutes(rbOne);
        context().addRoutes(rbTwo);
        context().start();

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(1 + 2 + 3 + 4);

        produceOne.sendBodyAndHeader(1, correlator, correlator);
        produceTwo.sendBodyAndHeader(2, correlator, correlator);
        produceOne.sendBodyAndHeader(3, correlator, correlator);
        produceOne.sendBodyAndHeader(4, correlator, correlator);

        mock.assertIsSatisfied();
    }

    private class MyAggregationStrategy implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            } else {
                Integer n = newExchange.getIn().getBody(Integer.class);
                Integer o = oldExchange.getIn().getBody(Integer.class);
                Integer v = (o == null ? 0 : o) + (n == null ? 0 : n);

                oldExchange.getIn().setBody(v, Integer.class);

                return oldExchange;
            }
        }
    }
}
