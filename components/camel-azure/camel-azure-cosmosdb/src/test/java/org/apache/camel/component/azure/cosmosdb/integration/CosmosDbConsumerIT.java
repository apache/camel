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
package org.apache.camel.component.azure.cosmosdb.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "endpoint", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB endpoint, e.g: mvn clean install -Dendpoint="),
        @EnabledIfSystemProperty(named = "accessKey", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB accessKey, e.g: mvn clean install -DaccessKey=")
})
class CosmosDbConsumerIT extends BaseCamelCosmosDbTestSupport {

    private static final String DATABASE_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private String containerName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private String leaseDatabaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

    @BeforeEach
    void createDatabaseContainerAndItems() {
        client.createDatabaseIfNotExists(DATABASE_NAME).block();
        client.getDatabase(DATABASE_NAME).createContainerIfNotExists(containerName, "/partition", null).block();
    }

    @Test
    void testConsumeEvents() throws Exception {
        // start our route
        context.getRouteController().startRoute("readEventsRoute");

        // we give some time for our route to start
        Thread.sleep(10000);

        // create testing items
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

        client.getDatabase(DATABASE_NAME).getContainer(containerName).createItem(item1, new PartitionKey("test-1"), null)
                .block();
        client.getDatabase(DATABASE_NAME).getContainer(containerName).createItem(item2, new PartitionKey("test-1"), null)
                .block();

        // start testing
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:readEvents");
        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.assertIsSatisfied(1000);

        final List returnedResults
                = mockEndpoint.getExchanges().get(0).getMessage().getBody(List.class);

        assertEquals(2, returnedResults.size());

        mockEndpoint.reset();

        // we send one more record
        client.getDatabase(DATABASE_NAME).getContainer(containerName).createItem(item3, new PartitionKey("test-2"), null)
                .block();

        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.assertIsSatisfied(1000);

        final List<Map> returnedResults2
                = mockEndpoint.getExchanges().get(0).getMessage().getBody(List.class);

        assertEquals(1, returnedResults2.size());
        assertEquals("test-id-3", returnedResults2.get(0).get("id"));
    }

    @AfterEach
    void removeAllDatabases() {
        // delete all databases being used in the test after each test
        client.readAllDatabases()
                .toIterable()
                .forEach(cosmosDatabaseProperties -> client.getDatabase(cosmosDatabaseProperties.getId()).delete()
                        .block());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format(
                        "azure-cosmosdb://%s/%s?leaseDatabaseName=%s&createLeaseDatabaseIfNotExists=true&createLeaseContainerIfNotExists=true",
                        DATABASE_NAME, containerName, leaseDatabaseName))
                        .routeId("readEventsRoute")
                        .to("mock:readEvents")
                        .setAutoStartup("false");
            }
        };
    }
}
