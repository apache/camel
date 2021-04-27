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

import java.util.List;
import java.util.stream.Collectors;

import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CosmosDbProducerIT extends BaseCamelCosmosDbTestSupport {
    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    @AfterEach
    void removeAllDatabases() {
        // delete all databases being used in the test after each test
        client.readAllDatabases()
                .toIterable()
                .forEach(cosmosDatabaseProperties -> client.getDatabase(cosmosDatabaseProperties.getId()).delete()
                        .block());
    }

    @Test
    void testListDatabases() throws InterruptedException {

        // create bunch of databases
        final String prefixDatabaseNames = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final int expectedSize = 5;

        for (int i = 0; i < expectedSize; i++) {
            client.createDatabase(prefixDatabaseNames + i).block();
        }

        result.expectedMessageCount(1);

        template.send("direct:listDatabases", exchange -> {
        });

        result.assertIsSatisfied(1000);

        // check the names of the databases
        final List<CosmosDatabaseProperties> returnedDatabases = result.getExchanges().get(0).getMessage().getBody(List.class);

        final List<String> returnedDatabasesAsString = returnedDatabases
                .stream().map(CosmosDatabaseProperties::getId)
                .collect(Collectors.toList());

        assertEquals(5, returnedDatabasesAsString.size());

        for (int i = 0; i < expectedSize; i++) {
            assertTrue(returnedDatabasesAsString.contains(prefixDatabaseNames + i));
        }
    }

    @Test
    void testCreateAndDeleteDatabase() throws InterruptedException {
        final String databaseNames = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        // test create database
        result.expectedMessageCount(1);

        template.send("direct:createDatabase",
                exchange -> exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseNames));

        result.assertIsSatisfied(1000);

        // check headers
        final Message response = result.getExchanges().get(0).getMessage();

        assertNotNull(response.getHeader(CosmosDbConstants.RESPONSE_HEADERS));
        assertNotNull(response.getHeader(CosmosDbConstants.E_TAG));
        assertNotNull(response.getHeader(CosmosDbConstants.TIMESTAMP));
        assertNotNull(response.getHeader(CosmosDbConstants.RESOURCE_ID));

        // test delete database
        result.reset();

        template.send("direct:deleteDatabase",
                exchange -> exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseNames));

        result.expectedMessageCount(1);

        result.assertIsSatisfied(1000);

        // check headers
        final Message responseDeleted = result.getExchanges().get(0).getMessage();

        assertTrue(responseDeleted.getHeader(CosmosDbConstants.STATUS_CODE, Integer.class) < 300);
    }

    @Test
    void testCreateAndDeleteContainer() throws InterruptedException {
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final String containerName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        // test if we disable flag create database if not existing, we should get exchange exception
        result.expectedMessageCount(1);

        template.send("direct:createContainer", exchange -> {
            exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseName);
            exchange.getIn().setHeader(CosmosDbConstants.CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(CosmosDbConstants.CONTAINER_PARTITION_KEY_PATH, "path");
        });

        result.assertIsNotSatisfied(500);

        // test create container
        result.reset();

        result.expectedMessageCount(1);

        template.send("direct:createContainer", exchange -> {
            exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseName);
            exchange.getIn().setHeader(CosmosDbConstants.CONTAINER_NAME, containerName);
            exchange.getIn().setHeader(CosmosDbConstants.CONTAINER_PARTITION_KEY_PATH, "path");
            exchange.getIn().setHeader(CosmosDbConstants.CREATE_DATABASE_IF_NOT_EXIST, true);
        });

        result.assertIsSatisfied(1000);

        // check headers
        final Message response = result.getExchanges().get(0).getMessage();

        assertNotNull(response.getHeader(CosmosDbConstants.RESPONSE_HEADERS));
        assertNotNull(response.getHeader(CosmosDbConstants.E_TAG));
        assertNotNull(response.getHeader(CosmosDbConstants.TIMESTAMP));
        assertNotNull(response.getHeader(CosmosDbConstants.RESOURCE_ID));
        assertTrue(response.getHeader(CosmosDbConstants.STATUS_CODE, Integer.class) < 300);

        result.reset();

        // test delete container
        result.reset();

        template.send("direct:deleteContainer", exchange -> {
            exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseName);
            exchange.getIn().setHeader(CosmosDbConstants.CONTAINER_NAME, containerName);
        });

        result.expectedMessageCount(1);

        result.assertIsSatisfied(1000);

        // check headers
        final Message responseDeleted = result.getExchanges().get(0).getMessage();

        assertTrue(responseDeleted.getHeader(CosmosDbConstants.STATUS_CODE, Integer.class) < 300);
    }

    @Test
    void testReplaceDatabaseThroughput() throws InterruptedException {
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        client.createDatabaseIfNotExists(databaseName, ThroughputProperties.createManualThroughput(500)).block();

        result.expectedMessageCount(1);

        template.send("direct:replaceDatabaseThroughput", exchange -> {
            exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseName);
            exchange.getIn().setHeader(CosmosDbConstants.THROUGHPUT_PROPERTIES,
                    ThroughputProperties.createManualThroughput(700));
        });

        result.assertIsSatisfied(1000);

        // check headers
        final Message response = result.getExchanges().get(0).getMessage();

        assertTrue(response.getHeader(CosmosDbConstants.STATUS_CODE, Integer.class) < 300);
        assertEquals(700, response.getHeader(CosmosDbConstants.MANUAL_THROUGHPUT, Integer.class));
    }

    @Test
    void testQueryContainers() throws InterruptedException {
        // create bunch of containers to test
        final String prefixContainerNames = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final int expectedSize = 5;

        client.createDatabaseIfNotExists(databaseName).block();

        for (int i = 0; i < expectedSize; i++) {
            client.getDatabase(databaseName).createContainer(prefixContainerNames + i, "/path").block();
        }

        result.expectedMessageCount(1);

        final String specificContainerName = prefixContainerNames + 2;
        final String query = String.format("SELECT * from c where c.id = '%s'", specificContainerName);

        template.send("direct:queryContainers", exchange -> {
            exchange.getIn().setHeader(CosmosDbConstants.DATABASE_NAME, databaseName);
            exchange.getIn().setHeader(CosmosDbConstants.QUERY, query);
        });

        result.assertIsSatisfied(1000);

        final List<CosmosContainerProperties> returnedContainers
                = result.getExchanges().get(0).getMessage().getBody(List.class);

        assertNotNull(returnedContainers);
        assertEquals(1, returnedContainers.size());
        assertEquals(specificContainerName, returnedContainers.get(0).getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listDatabases").to("azure-cosmosdb://?operation=listDatabases").to(resultName);
                from("direct:createDatabase").to("azure-cosmosdb://?operation=createDatabase").to(resultName);
                from("direct:deleteDatabase").to("azure-cosmosdb://?operation=deleteDatabase").to(resultName);
                from("direct:createContainer").to("azure-cosmosdb://?operation=createContainer")
                        .to(resultName);
                from("direct:replaceDatabaseThroughput").to("azure-cosmosdb://?operation=replaceDatabaseThroughput")
                        .to(resultName);
                from("direct:queryContainers").to("azure-cosmosdb://?operation=queryContainers").to(resultName);
                from("direct:deleteContainer").to("azure-cosmosdb://?operation=deleteContainer").to(resultName);
            }
        };
    }
}
