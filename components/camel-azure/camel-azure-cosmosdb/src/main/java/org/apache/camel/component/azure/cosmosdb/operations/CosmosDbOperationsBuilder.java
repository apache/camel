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
package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;

public final class CosmosDbOperationsBuilder {

    private final CosmosAsyncClientWrapper clientWrapper;
    private String databaseName;
    private boolean createDatabaseIfNotExist;
    private String containerName;
    private String containerPartitionKeyPath;
    private boolean createContainerIfNotExist;
    private ThroughputProperties throughputProperties;

    private IndexingPolicy indexingPolicy;

    private CosmosDbOperationsBuilder(CosmosAsyncClientWrapper clientWrapper) {
        this.clientWrapper = clientWrapper;
    }

    public static CosmosDbOperationsBuilder withClient(final CosmosAsyncClientWrapper clientWrapper) {
        return new CosmosDbOperationsBuilder(clientWrapper);
    }

    public CosmosDbOperationsBuilder withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public CosmosDbOperationsBuilder withCreateDatabaseIfNotExist(boolean createDatabaseIfNotExist) {
        this.createDatabaseIfNotExist = createDatabaseIfNotExist;
        return this;
    }

    public CosmosDbOperationsBuilder withContainerName(String containerName) {
        this.containerName = containerName;
        return this;
    }

    public CosmosDbOperationsBuilder withContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
        return this;
    }

    public CosmosDbOperationsBuilder withCreateContainerIfNotExist(boolean createContainerIfNotExist) {
        this.createContainerIfNotExist = createContainerIfNotExist;
        return this;
    }

    public CosmosDbOperationsBuilder withThroughputProperties(ThroughputProperties throughputProperties) {
        this.throughputProperties = throughputProperties;
        return this;
    }

    public CosmosDbOperationsBuilder withIndexingPolicy(IndexingPolicy indexingPolicy) {
        this.indexingPolicy = indexingPolicy;
        return this;
    }

    public CosmosDbDatabaseOperations buildDatabaseOperations() {
        // if we enabled this flag, we create a database first before running the operation
        if (createDatabaseIfNotExist) {
            return CosmosDbClientOperations.withClient(clientWrapper)
                    .createDatabaseIfNotExistAndGetDatabaseOperations(databaseName, throughputProperties);
        }

        // otherwise just return the operation without creating a database if it is not existing
        return CosmosDbClientOperations.withClient(clientWrapper)
                .getDatabaseOperations(databaseName);
    }

    public CosmosDbContainerOperations buildContainerOperations() {
        // if we enabled this flag, we create a container first before running the operation
        if (createContainerIfNotExist) {
            return buildDatabaseOperations()
                    .createContainerIfNotExistAndGetContainerOperations(containerName,
                            containerPartitionKeyPath,
                            throughputProperties,
                            indexingPolicy);
        }

        // otherwise just return the operation without creating a container if it is not existing
        return buildDatabaseOperations()
                .getContainerOperations(containerName);
    }
}
