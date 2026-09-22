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
package org.apache.camel.component.opensearch.aggregation;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class OpensearchBulkRequestAggregationStrategyTest {

    private final OpensearchBulkRequestAggregationStrategy strategy = new OpensearchBulkRequestAggregationStrategy();
    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Exchange exchangeWith(String id) {
        Exchange exchange = new DefaultExchange(context);
        BulkOperation operation = new BulkOperation.Builder()
                .index(new IndexOperation.Builder<>().index("idx").id(id).document(Map.of("id", id)).build())
                .build();
        exchange.getIn().setBody(new BulkOperation[] { operation });
        return exchange;
    }

    private static List<String> ids(BulkRequest request) {
        return request.operations().stream().map(op -> op.index().id()).toList();
    }

    @Test
    void firstAggregationMustReturnNewExchangeCarryingTheRequest() {
        Exchange newExchange = exchangeWith("1");

        Exchange result = strategy.aggregate(null, newExchange);

        // On the first call oldExchange is null; returning it (the previous bug) would make the
        // AggregationStrategy return null, which AggregateProcessor rejects.
        assertSame(newExchange, result);
        BulkRequest request = result.getIn().getBody(BulkRequest.class);
        assertNotNull(request);
        assertEquals(List.of("1"), ids(request));
    }

    @Test
    void subsequentAggregationMergesAllOperationsInInsertionOrder() {
        Exchange step1 = strategy.aggregate(null, exchangeWith("1"));
        Exchange step2 = strategy.aggregate(step1, exchangeWith("2"));
        Exchange step3 = strategy.aggregate(step2, exchangeWith("3"));

        // a 3-step chain proves the merged request preserves insertion order cumulatively
        assertEquals(List.of("1", "2"), ids(step2.getIn().getBody(BulkRequest.class)));
        assertEquals(List.of("1", "2", "3"), ids(step3.getIn().getBody(BulkRequest.class)));
    }
}
