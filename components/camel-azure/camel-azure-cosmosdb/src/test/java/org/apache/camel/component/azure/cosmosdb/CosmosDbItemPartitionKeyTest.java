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

import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The partition key of the item operations comes from the CamelAzureCosmosDbItemPartitionKey header or the
 * itemPartitionKey option. A PartitionKey header is used as it is; any other value is converted to a String.
 */
class CosmosDbItemPartitionKeyTest extends CamelTestSupport {

    private final CosmosDbConfiguration configuration = new CosmosDbConfiguration();
    private final CosmosDbConfigurationOptionsProxy proxy = new CosmosDbConfigurationOptionsProxy(configuration);

    @Test
    void partitionKeyHeaderIsUsedAsItIs() {
        Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader(CosmosDbConstants.ITEM_PARTITION_KEY, new PartitionKey(5));
        assertEquals(new PartitionKey(5), proxy.getItemPartitionKey(exchange));
        assertNotEquals(new PartitionKey("5"), proxy.getItemPartitionKey(exchange));

        exchange.getIn().setHeader(CosmosDbConstants.ITEM_PARTITION_KEY, new PartitionKey(true));
        assertEquals(new PartitionKey(true), proxy.getItemPartitionKey(exchange));

        exchange.getIn().setHeader(CosmosDbConstants.ITEM_PARTITION_KEY, new PartitionKey("a"));
        assertEquals(new PartitionKey("a"), proxy.getItemPartitionKey(exchange));
    }

    @Test
    void otherValuesAreConvertedToString() {
        Exchange exchange = new DefaultExchange(context);
        configuration.setItemPartitionKey("b");
        assertEquals(new PartitionKey("b"), proxy.getItemPartitionKey(exchange));

        exchange.getIn().setHeader(CosmosDbConstants.ITEM_PARTITION_KEY, "a");
        assertEquals(new PartitionKey("a"), proxy.getItemPartitionKey(exchange));

        exchange.getIn().setHeader(CosmosDbConstants.ITEM_PARTITION_KEY, 5);
        assertEquals(new PartitionKey("5"), proxy.getItemPartitionKey(exchange));
    }
}
