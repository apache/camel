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
package org.apache.camel.component.azure.cosmosdb.integration.operations;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbOperationsBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "endpoint", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB endpoint, e.g: mvn clean install -Dendpoint="),
        @EnabledIfSystemProperty(named = "accessKey", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB accessKey, e.g: mvn clean install -DaccessKey=")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbContainerOperationsIT {
    private static final String DATABASE_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private static final String LEASE_DATABASE_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

    private CosmosAsyncClientWrapper clientWrapper;
    private CosmosDbContainerOperations containerOperations;
    private String containerId;

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = CosmosDbTestUtils.loadAzureAccessFromJvmEnv();

        final CosmosAsyncClient client = new CosmosClientBuilder()
                .contentResponseOnWriteEnabled(true)
                .key(properties.getProperty("access_key"))
                .endpoint(properties.getProperty("endpoint"))
                .buildAsyncClient();

        clientWrapper = new CosmosAsyncClientWrapper(client);
    }

    @AfterAll
    void tearDown() {
        clientWrapper.getDatabase(DATABASE_NAME).delete().block();
        clientWrapper.getDatabase(LEASE_DATABASE_NAME).delete().block();
    }

    @BeforeEach
    void prepareFreshContainer() {
        containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        containerOperations = CosmosDbClientOperations.withClient(clientWrapper)
                .createDatabaseIfNotExistAndGetDatabaseOperations(DATABASE_NAME, null)
                .createContainerIfNotExistAndGetContainerOperations(containerId, "partition", null, null);

        // make sure container is created
        containerOperations.getContainerId().block();
    }

    @Test
    void testItemCreateItems() {
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-1");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        final Map<String, Object> item3 = new HashMap<>();
        item3.put("id", "test-id-3");
        item3.put("partition", "test-2");
        item3.put("field1", 6654);
        item3.put("field2", "super super awesome!");

        containerOperations.createItem(item1, new PartitionKey("test-1"), null).block();
        containerOperations.createItem(item2, new PartitionKey("test-1"), null).block();
        containerOperations.createItem(item3, new PartitionKey("test-2"), null).block();

        final Flux<Map> itemsPartitions1 = readAllItems("test-1");
        final Flux<Map> itemsPartitions2 = readAllItems("test-2");

        // assert all out written data
        assertEquals(2, itemsPartitions1.count().block());
        assertEquals(1, itemsPartitions2.count().block());
        assertTrue(itemsPartitions1.any(item -> item.get("id").toString().equals("test-id-1")).block());
        assertTrue(itemsPartitions1.any(item -> item.get("id").toString().equals("test-id-2")).block());
        assertTrue(itemsPartitions2.any(item -> item.get("id").toString().equals("test-id-3")).block());
    }

    @Test
    void testCreateItems() {
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-1");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        final List<Map<String, ?>> items = new LinkedList<>();

        items.add(item1);
        items.add(item2);

        // create items
        containerOperations.createItems(items, new PartitionKey("test-1"), null).blockFirst();
        assertEquals(2, readAllItems("test-1").count().block());
    }

    @Test
    void testUpsertItem() {
        final Map<String, Object> originalItem = new HashMap<>();
        originalItem.put("id", "test-id-1");
        originalItem.put("partition", "test-1");
        originalItem.put("field1", 12234);
        originalItem.put("field2", "awesome!");

        final Map<String, Object> upsertItem = new HashMap<>();
        upsertItem.put("id", "test-id-1");
        upsertItem.put("partition", "test-1");
        upsertItem.put("field1", 99332);
        upsertItem.put("field2", "upsert!");

        // upsert should create our record if it is not existing
        containerOperations.upsertItem(originalItem, new PartitionKey("test-1"), null).block();

        // should only have one item
        assertEquals(1, readAllItems("test-1").count().block());
        assertNotNull(readItem("test-id-1", "test-1").getItem());
        assertEquals("awesome!", readItem("test-id-1", "test-1").getItem().get("field2"));

        // upsert
        containerOperations.upsertItem(upsertItem, new PartitionKey("test-1"), null).block();

        // should only have one item
        assertEquals(1, readAllItems("test-1").count().block());
        assertNotNull(readItem("test-id-1", "test-1").getItem());
        assertEquals("upsert!", readItem("test-id-1", "test-1").getItem().get("field2"));
    }

    @Test
    void testUpsertItems() {
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-1");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        final List<Map<String, ?>> items = new LinkedList<>();

        items.add(item1);
        items.add(item2);

        // create items
        containerOperations.upsertItems(items, new PartitionKey("test-1"), null).blockFirst();
        assertEquals(2, readAllItems("test-1").count().block());
    }

    @Test
    void testDeleteItem() {
        final Map<String, Object> originalItem = new HashMap<>();
        originalItem.put("id", "test-id-1");
        originalItem.put("partition", "test-1");
        originalItem.put("field1", 12234);
        originalItem.put("field2", "awesome!");

        containerOperations.createItem(originalItem, new PartitionKey("test-1"), null).block();

        assertEquals(1, readAllItems("test-1").count().block());
        assertNotNull(readItem("test-id-1", "test-1").getItem());

        // now delete
        containerOperations.deleteItem("test-id-1", new PartitionKey("test-1"), null).block();

        assertEquals(0, readAllItems("test-1").count().block());
    }

    @Test
    void testReplaceItem() {
        final Map<String, Object> originalItem = new HashMap<>();
        originalItem.put("id", "test-id-1");
        originalItem.put("partition", "test-1");
        originalItem.put("field1", 12234);
        originalItem.put("field2", "awesome!");

        final Map<String, Object> replacedItem = new HashMap<>();
        replacedItem.put("id", "test-id-1");
        replacedItem.put("partition", "test-1");
        replacedItem.put("field1", 99332);
        replacedItem.put("field2", "replace!");

        // expect an error to replace non existing item
        assertThrows(Exception.class,
                () -> containerOperations.replaceItem(originalItem, "test-id-1", new PartitionKey("test-1"), null).block());

        // try again
        containerOperations.createItem(originalItem, new PartitionKey("test-1"), null).block();
        containerOperations.replaceItem(replacedItem, "test-id-1", new PartitionKey("test-1"), null).block();

        // should only have one item
        assertEquals(1, readAllItems("test-1").count().block());
        assertNotNull(readItem("test-id-1", "test-1").getItem());
        assertEquals("replace!", readItem("test-id-1", "test-1").getItem().get("field2"));
    }

    @Test
    void testQueryItems() {
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-1");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        final Map<String, Object> item3 = new HashMap<>();
        item3.put("id", "test-id-3");
        item3.put("partition", "test-2");
        item3.put("field1", 6654);
        item3.put("field2", "super super awesome!");

        containerOperations.createItem(item1, new PartitionKey("test-1"), null).block();
        containerOperations.createItem(item2, new PartitionKey("test-1"), null).block();
        containerOperations.createItem(item3, new PartitionKey("test-2"), null).block();

        final String query = "SELECT c.id,c.field2 from c where c.id = 'test-id-2'";

        assertEquals(1, containerOperations.queryItems(query, null, Map.class).count().block());

        final Map actualItem = containerOperations.queryItems(query, null, Map.class)
                .blockFirst();

        // should only have two keys
        assertEquals(2, actualItem.keySet().stream().count());
        assertEquals("test-id-2", actualItem.get("id"));
        assertEquals("super awesome!", actualItem.get("field2"));
    }

    @Test
    void testCaptureChangeFeed() {
        // we create our lease container first with our lease database
        final Mono<CosmosAsyncContainer> leaseContainer = CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withContainerName("camel-lease")
                .withContainerPartitionKeyPath("/id")
                .withDatabaseName(LEASE_DATABASE_NAME)
                .withCreateContainerIfNotExist(true)
                .withCreateDatabaseIfNotExist(true)
                .buildContainerOperations()
                .getContainer();

        final CosmosDbTestUtils.Latch latch = new CosmosDbTestUtils.Latch();
        final Consumer<List<Map<String, ?>>> onEvent = event -> {
            assertEquals(2, event.size());
            assertEquals("test-id-1", event.get(0).get("id"));
            assertEquals("test-id-2", event.get(1).get("id"));
            latch.done();
        };

        final ChangeFeedProcessor changeFeedProcessorMono
                = containerOperations.captureEventsWithChangeFeed(leaseContainer, "my-host", onEvent, null);

        // start our events processor
        changeFeedProcessorMono.start().block();

        // insert our testing items
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-2");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        containerOperations.createItem(item1, new PartitionKey("test-1"), null).block();
        containerOperations.createItem(item2, new PartitionKey("test-2"), null).block();

        latch.await(10000);

        // stop our events processor
        changeFeedProcessorMono.stop().block();
    }

    private CosmosItemResponse<Map> readItem(final String itemId, final String partitionKey) {
        return containerOperations.readItem(itemId, new PartitionKey(partitionKey), null, Map.class).block();
    }

    private Flux<Map> readAllItems(final String partitionKey) {
        return containerOperations.readAllItems(new PartitionKey(partitionKey), null, Map.class);
    }
}
