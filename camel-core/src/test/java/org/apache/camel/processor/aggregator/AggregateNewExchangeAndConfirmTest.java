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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;

import static org.awaitility.Awaitility.await;

/**
 * Testing CAMEL-3139
 * 
 * @version 
 */
public class AggregateNewExchangeAndConfirmTest extends ContextTestSupport {

    private MyRepo repo = new MyRepo();

    public void testAggregateNewExchangeAndConfirm() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedBodiesReceived("ABC");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);

        assertMockEndpointsSatisfied();

        // give UoW time to complete and confirm
        await().atMost(1, TimeUnit.SECONDS).until(() -> repo.getId() != null);

        // must have confirmed
        assertEquals(mock.getReceivedExchanges().get(0).getExchangeId(), repo.getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyNewExchangeAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(3)
                    .to("mock:aggregated");
            }
        };
    }

    private class MyNewExchangeAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            String body = "";
            if (oldExchange != null) {
                body = oldExchange.getIn().getBody(String.class);
            }
            body += newExchange.getIn().getBody(String.class);
            newExchange.getIn().setBody(body);
            return newExchange;
        }
    }

    private class MyRepo extends MemoryAggregationRepository {

        private String id;

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            log.info("Confirmed id: " + exchangeId);
            this.id = exchangeId;
        }

        public String getId() {
            return id;
        }
    }
}