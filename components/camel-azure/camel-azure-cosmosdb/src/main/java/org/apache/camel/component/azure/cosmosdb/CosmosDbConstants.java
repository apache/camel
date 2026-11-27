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

import org.apache.camel.spi.Metadata;

public final class CosmosDbConstants {
    private static final String HEADER_PREFIX = "CamelAzureCosmosDb";

    // headers evaluated by the producer only
    @Metadata(label = "producer", description = "The name of the database. Replaces the databaseName option.",
              javaType = "String")
    public static final String DATABASE_NAME = HEADER_PREFIX + "DatabaseName";
    @Metadata(label = "producer", description = "The name of the container. Replaces the containerName option.",
              javaType = "String")
    public static final String CONTAINER_NAME = HEADER_PREFIX + "ContainerName";
    @Metadata(label = "producer", description = "The operation that the producer performs. Replaces the operation option.",
              javaType = "org.apache.camel.component.azure.cosmosdb.CosmosDbOperationsDefinition")
    public static final String OPERATION = HEADER_PREFIX + "Operation";
    @Metadata(label = "producer",
              description = "The SQL query of queryDatabases, queryContainers and queryItems. Replaces the query option.",
              javaType = "String")
    public static final String QUERY = HEADER_PREFIX + "Query";
    @Metadata(label = "producer",
              description = "The options of queryDatabases, listContainers, queryContainers, readAllItems and queryItems."
                            + " Replaces the queryRequestOptions option.",
              javaType = "com.azure.cosmos.models.CosmosQueryRequestOptions")
    public static final String QUERY_REQUEST_OPTIONS = HEADER_PREFIX + "QueryRequestOptions";
    @Metadata(label = "producer",
              description = "Whether to create the database when it does not exist. Replaces the createDatabaseIfNotExists"
                            + " option.",
              javaType = "Boolean")
    public static final String CREATE_DATABASE_IF_NOT_EXIST = HEADER_PREFIX + "CreateDatabaseIfNotExist";
    @Metadata(label = "producer",
              description = "Whether to create the container when it does not exist. Replaces the createContainerIfNotExists"
                            + " option.",
              javaType = "Boolean")
    public static final String CREATE_CONTAINER_IF_NOT_EXIST = HEADER_PREFIX + "CreateContainerIfNotExist";
    @Metadata(label = "producer",
              description = "The indexing policy of a container that the producer creates. Replaces the indexingPolicy"
                            + " option.",
              javaType = "com.azure.cosmos.models.IndexingPolicy")
    public static final String INDEXING_POLICY = HEADER_PREFIX + "IndexingPolicy";
    @Metadata(label = "producer",
              description = "The throughput of a database or container that the producer creates, and the new throughput"
                            + " of replaceDatabaseThroughput and replaceContainerThroughput. Replaces the"
                            + " throughputProperties option.",
              javaType = "com.azure.cosmos.models.ThroughputProperties")
    public static final String THROUGHPUT_PROPERTIES = HEADER_PREFIX + "ThroughputProperties";
    @Metadata(label = "producer", description = "The request options of deleteDatabase.",
              javaType = "com.azure.cosmos.models.CosmosDatabaseRequestOptions")
    public static final String DATABASE_REQUEST_OPTIONS = HEADER_PREFIX + "DatabaseRequestOptions";
    @Metadata(label = "producer",
              description = "The partition key path of a container that the producer creates. Replaces the"
                            + " containerPartitionKeyPath option.",
              javaType = "String")
    public static final String CONTAINER_PARTITION_KEY_PATH = HEADER_PREFIX + "ContainerPartitionKeyPath";
    @Metadata(label = "producer", description = "The request options of deleteContainer.",
              javaType = "com.azure.cosmos.models.CosmosContainerRequestOptions")
    public static final String CONTAINER_REQUEST_OPTIONS = HEADER_PREFIX + "ContainerRequestOptions";
    @Metadata(label = "producer",
              description = "The value of the partition key of the item for createItem, upsertItem, replaceItem, deleteItem"
                            + " and readItem, and of the items that readAllItems reads. Replaces the itemPartitionKey"
                            + " option. A com.azure.cosmos.models.PartitionKey is used as it is, for example for a partition"
                            + " key value that is a number or a boolean; any other value is converted to a String.",
              javaType = "String or com.azure.cosmos.models.PartitionKey")
    public static final String ITEM_PARTITION_KEY = HEADER_PREFIX + "ItemPartitionKey";
    @Metadata(label = "producer",
              description = "The request options of createItem, upsertItem, replaceItem, deleteItem and readItem.",
              javaType = "com.azure.cosmos.models.CosmosItemRequestOptions")
    public static final String ITEM_REQUEST_OPTIONS = HEADER_PREFIX + "ItemRequestOptions";
    @Metadata(label = "producer",
              description = "The id of the item for readItem, replaceItem and deleteItem. Replaces the itemId option.",
              javaType = "String")
    public static final String ITEM_ID = HEADER_PREFIX + "ItemId";

    // headers set by the producer
    @Metadata(label = "producer",
              description = "The resource id of the database or container, set by the operations on a database or a"
                            + " container that get its properties back, such as createDatabase and createContainer.",
              javaType = "String")
    public static final String RESOURCE_ID = HEADER_PREFIX + "ResourceId";
    @Metadata(label = "producer",
              description = "The entity tag of the database, container, throughput or item that the operation returns.",
              javaType = "String")
    public static final String E_TAG = HEADER_PREFIX + "Etag";
    @Metadata(label = "producer",
              description = "The time of the last update of the database, container or throughput that the operation"
                            + " returns.",
              javaType = "java.time.Instant")
    public static final String TIMESTAMP = HEADER_PREFIX + "Timestamp";
    @Metadata(label = "producer",
              description = "The headers of the response of Cosmos DB, for all operations except listDatabases,"
                            + " queryDatabases, listContainers, queryContainers, readAllItems and queryItems.",
              javaType = "Map<String, String>")
    public static final String RESPONSE_HEADERS = HEADER_PREFIX + "ResponseHeaders";
    @Metadata(label = "producer",
              description = "The HTTP status code of the response of Cosmos DB, for all operations except listDatabases,"
                            + " queryDatabases, listContainers, queryContainers, readAllItems and queryItems.",
              javaType = "Integer")
    public static final String STATUS_CODE = HEADER_PREFIX + "StatusCode";
    @Metadata(label = "producer",
              description = "The default time to live of the items of the container, in seconds, set by the operations on"
                            + " a container that get its properties back, such as createContainer.",
              javaType = "Integer")
    public static final String DEFAULT_TIME_TO_LIVE_SECONDS = HEADER_PREFIX + "DefaultTimeToLiveInSeconds";
    @Metadata(label = "producer",
              description = "The manual throughput, in request units per second, after replaceDatabaseThroughput or"
                            + " replaceContainerThroughput.",
              javaType = "Integer")
    public static final String MANUAL_THROUGHPUT = HEADER_PREFIX + "ManualThroughput";
    @Metadata(label = "producer",
              description = "The maximum autoscale throughput, in request units per second, after"
                            + " replaceDatabaseThroughput or replaceContainerThroughput.",
              javaType = "Integer")
    public static final String AUTOSCALE_MAX_THROUGHPUT = HEADER_PREFIX + "AutoscaleMaxThroughput";

    private CosmosDbConstants() {
    }
}
