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

import java.util.Map;

import com.azure.cosmos.CosmosAsyncClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.azure.cosmosdb.client.CosmosDbClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * To read and write records to the CosmosDB database on Azure cloud platform.
 */
@UriEndpoint(firstVersion = "3.10.0", scheme = "azure-cosmosdb", title = "Azure CosmosDB",
             syntax = "azure-cosmosdb:databaseName/containerName", category = {
                     Category.CLOUD, Category.DATABASE })
@BodyTypes(consumerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<java.util.Map<String, Object>>",
                                   description = "The items of one batch of the change feed: a List with a Map per item"
                                                 + " created or changed, with the system fields of Cosmos DB such as _etag and"
                                                 + " _lsn"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "createItem",
                                                         carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }),
                                          @BodyType.When(option = "operation", value = "upsertItem",
                                                         carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }),
                                          @BodyType.When(option = "operation", value = "replaceItem",
                                                         carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }) },
                                  description = "The item for createItem, upsertItem and replaceItem: a Map or a POJO, which"
                                                + " Jackson serializes, or the JSON of the item as bytes. A JSON String fails, as"
                                                + " it is sent as a JSON string and not as an object: unmarshal it first. The other"
                                                + " operations do not use the body: the item id, the partition key and the query"
                                                + " are options or headers"),
           producerOut = @BodyType(value = Carrier.LIST,
                                   javaType = "java.util.List<com.azure.cosmos.models.CosmosDatabaseProperties>",
                                   dependsOn = "contentResponseOnWriteEnabled",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "queryDatabases",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.cosmos.models.CosmosDatabaseProperties>"),
                                           @BodyType.When(option = "operation", value = "listContainers",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.cosmos.models.CosmosContainerProperties>"),
                                           @BodyType.When(option = "operation", value = "queryContainers",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.azure.cosmos.models.CosmosContainerProperties>"),
                                           @BodyType.When(option = "operation", value = "createDatabase",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "deleteDatabase",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "createContainer",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "deleteContainer",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "replaceDatabaseThroughput",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "replaceContainerThroughput",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "createItem",
                                                          carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }),
                                           @BodyType.When(option = "operation", value = "upsertItem",
                                                          carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }),
                                           @BodyType.When(option = "operation", value = "replaceItem",
                                                          carriers = { Carrier.MAP, Carrier.POJO, Carrier.BYTES }),
                                           @BodyType.When(option = "operation", value = "readItem", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<String, Object>"),
                                           @BodyType.When(option = "operation", value = "readAllItems", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<java.util.Map<String, Object>>"),
                                           @BodyType.When(option = "operation", value = "queryItems", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "deleteItem",
                                                          carriers = Carrier.NULL) },
                                   description = "A List of the properties of the SDK for listDatabases, the default, and the"
                                                 + " list and query operations of databases and containers; the item as written"
                                                 + " for createItem, upsertItem and replaceItem, of the type of the body sent, or"
                                                 + " no body with contentResponseOnWriteEnabled=false; the item as a Map for"
                                                 + " readItem, and a List of them for readAllItems and queryItems, whose elements"
                                                 + " are values for a SELECT VALUE query. The operations on databases, containers"
                                                 + " and throughput leave the body and set the response in headers"))
public class CosmosDbEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private CosmosDbConfiguration configuration;

    private CosmosAsyncClient cosmosAsyncClient;

    public CosmosDbEndpoint(final String uri, final Component component, final CosmosDbConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CosmosDbProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // we need to have database and container set to consume events
        if (ObjectHelper.isEmpty(configuration.getDatabaseName()) || ObjectHelper.isEmpty(configuration.getContainerName())) {
            throw new IllegalArgumentException("Database name and container name must be set.");
        }

        final Consumer cosmosDbConsumer = new CosmosDbConsumer(this, processor);
        configureConsumer(cosmosDbConsumer);

        return cosmosDbConsumer;
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        cosmosAsyncClient = configuration.getCosmosAsyncClient() != null
                ? configuration.getCosmosAsyncClient() : CosmosDbClientFactory.createCosmosAsyncClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (cosmosAsyncClient != null) {
            cosmosAsyncClient.close();
        }

        super.doStop();
    }

    /**
     * The component configurations
     */
    public CosmosDbConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(CosmosDbConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Inject an external {@link CosmosAsyncClient} into the component which provides a client-side logical
     * representation of the Azure Cosmos DB service. This asynchronous client is used to configure and execute requests
     * against the service.
     */
    public CosmosAsyncClient getCosmosAsyncClient() {
        return cosmosAsyncClient;
    }

    public void setCosmosAsyncClient(CosmosAsyncClient cosmosAsyncClient) {
        this.cosmosAsyncClient = cosmosAsyncClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getDatabaseEndpoint())) {
            return configuration.getDatabaseEndpoint();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "cosmosdb";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getDatabaseName() != null) {
            return Map.of("databaseName", configuration.getDatabaseName());
        }
        return null;
    }
}
