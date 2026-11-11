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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group that is completed by its first exchange is never stored in the repository, so the aggregator must not confirm
 * it: the confirm would not find the exchange in the completed table, and the aggregator would keep its id as
 * unconfirmed for good.
 */
public class JdbcAggregateCompletedByFirstExchangeTest extends AbstractJdbcAggregationTestSupport {

    private final List<String> confirmed = new CopyOnWriteArrayList<>();
    private final List<String> notConfirmed = new CopyOnWriteArrayList<>();

    @Override
    void configureJdbcAggregationRepository() {
        JdbcAggregationRepository configured = applicationContext.getBean("repo1", JdbcAggregationRepository.class);
        repo = new JdbcAggregationRepository(
                configured.getTransactionManager(), configured.getRepositoryName(), configured.getDataSource()) {
            @Override
            public boolean confirmWithResult(CamelContext camelContext, String exchangeId) {
                boolean answer = super.confirmWithResult(camelContext, exchangeId);
                (answer ? confirmed : notConfirmed).add(exchangeId);
                return answer;
            }
        };
    }

    @Test
    public void testGroupCompletedByFirstExchangeIsNotConfirmed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("A", "B", "CD");

        template.sendBodyAndHeaders("direct:start", "A", Map.of("id", 1, "size", 1));
        template.sendBodyAndHeaders("direct:start", "B", Map.of("id", 2, "size", 1));
        // a group that is stored, as a control
        template.sendBodyAndHeaders("direct:start", "C", Map.of("id", 3, "size", 2));
        template.sendBodyAndHeaders("direct:start", "D", Map.of("id", 3, "size", 2));

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> confirmed.size() == 1);
        assertTrue(notConfirmed.isEmpty(), "Exchanges confirmed but not found in the completed table: " + notConfirmed);
        String stored = mock.getReceivedExchanges().stream()
                .filter(exchange -> "CD".equals(exchange.getMessage().getBody(String.class)))
                .findFirst().orElseThrow().getExchangeId();
        assertEquals(List.of(stored), confirmed);
        assertTrue(repo.scan(context).isEmpty(), "No exchange should be left in the completed table");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                configureJdbcAggregationRepository();

                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .completionSize(header("size")).aggregationRepository(repo)
                        .to("mock:result");
            }
        };
    }
}
