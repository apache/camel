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
package org.apache.camel.component.azure.cosmosdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmosDbConsumerTest extends CamelTestSupport {

    private CosmosDbConsumer newConsumer(Processor processor) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("accountKey", "myKey");
        CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class)
                .createEndpoint("azure-cosmosdb://mydb/myContainer", "mydb/myContainer", params);
        return new CosmosDbConsumer(endpoint, processor);
    }

    private Exchange batchExchange() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(List.of(Map.of("id", "1")));
        return exchange;
    }

    @Test
    void failedBatchIsRethrownSoTheLeaseIsNotAdvanced() throws Exception {
        // a failing route must not let the change feed advance the lease: the batch has to be rethrown so
        // the Azure ChangeFeedProcessor redelivers it (at-least-once) instead of silently losing it
        CosmosDbConsumer consumer = newConsumer(exchange -> {
            throw new IllegalStateException("boom");
        });

        RuntimeCamelException thrown
                = assertThrows(RuntimeCamelException.class, () -> consumer.processBatch(batchExchange()));
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }

    @Test
    void successfulBatchReturnsNormally() throws Exception {
        boolean[] processed = { false };
        CosmosDbConsumer consumer = newConsumer(exchange -> processed[0] = true);

        consumer.processBatch(batchExchange());

        assertTrue(processed[0]);
    }
}
