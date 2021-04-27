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

import java.util.List;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CosmosDbConfiguration implements Cloneable {

    @UriPath
    private String databaseName;
    @UriPath
    private String containerName;
    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String accountKey;
    @UriParam(label = "common")
    @Metadata(required = true)
    private String databaseEndpoint;
    @UriParam(label = "common")
    private String containerPartitionKeyPath;
    @UriParam(label = "common")
    private PartitionKey itemPartitionKey;
    @UriParam(label = "common")
    @Metadata(autowired = true)
    private CosmosAsyncClient cosmosAsyncClient;
    @UriParam(label = "common", defaultValue = "ConsistencyLevel.SESSION")
    private ConsistencyLevel consistencyLevel = ConsistencyLevel.SESSION;
    @UriParam(label = "common")
    private List<String> preferredRegions;
    @UriParam(label = "common", defaultValue = "false")
    private boolean clientTelemetryEnabled;
    @UriParam(label = "common", defaultValue = "false")
    private boolean connectionSharingAcrossClientsEnabled;
    @UriParam(label = "common", defaultValue = "true")
    private boolean multipleWriteRegionsEnabled = true;
    @UriParam(label = "common", defaultValue = "true")
    private boolean readRequestsFallbackEnabled = true;
    @UriParam(label = "common")
    private String query;
    @UriParam(label = "common")
    private ThroughputProperties throughputProperties;
    @UriParam(label = "producer", defaultValue = "listDatabases")
    private CosmosDbOperationsDefinition operation = CosmosDbOperationsDefinition.listDatabases;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean createDatabaseIfNotExists;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean createContainerIfNotExists;

    public CosmosDbConfiguration() {
    }

    /**
     * The name of the Cosmos database that component should connect to. In case you are producing data and have
     * createDatabaseIfNotExists=true, the component will automatically auto create a Cosmos database.
     */
    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * The name of the Cosmos container that component should connect to. In case you are producing data and have
     * createContainerIfNotExists=true, the component will automatically auto create a Cosmos container.
     */
    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    /**
     * Sets either a master or readonly key used to perform authentication for accessing resource.
     */
    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    /**
     * Sets the Azure Cosmos database endpoint the component will connect to.
     */
    public String getDatabaseEndpoint() {
        return databaseEndpoint;
    }

    public void setDatabaseEndpoint(String databaseEndpoint) {
        this.databaseEndpoint = databaseEndpoint;
    }

    /**
     * Sets the container partition key path.
     */
    public String getContainerPartitionKeyPath() {
        return containerPartitionKeyPath;
    }

    public void setContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
    }

    /**
     * Sets partition key. Represents a partition key value in the Azure Cosmos DB database service. A partition key
     * identifies the partition where the item is stored in.
     */
    public PartitionKey getItemPartitionKey() {
        return itemPartitionKey;
    }

    public void setItemPartitionKey(PartitionKey itemPartitionKey) {
        this.itemPartitionKey = itemPartitionKey;
    }

    /**
     * Sets if the component should create Cosmos database automatically in case it doesn't exist in Cosmos account
     */
    public boolean isCreateDatabaseIfNotExists() {
        return createDatabaseIfNotExists;
    }

    public void setCreateDatabaseIfNotExists(boolean createDatabaseIfNotExists) {
        this.createDatabaseIfNotExists = createDatabaseIfNotExists;
    }

    /**
     * Sets if the component should create Cosmos container automatically in case it doesn't exist in Cosmos database
     */
    public boolean isCreateContainerIfNotExists() {
        return createContainerIfNotExists;
    }

    public void setCreateContainerIfNotExists(boolean createContainerIfNotExists) {
        this.createContainerIfNotExists = createContainerIfNotExists;
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

    /**
     * Sets the consistency levels supported for Azure Cosmos DB client operations in the Azure Cosmos DB service.
     * <p>
     * The requested ConsistencyLevel must match or be weaker than that provisioned for the database account.
     * Consistency levels by order of strength are STRONG, BOUNDED_STALENESS, SESSION and EVENTUAL.
     *
     * Refer to consistency level documentation for additional details:
     * https://docs.microsoft.com/en-us/azure/cosmos-db/consistency-levels
     */
    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    /**
     * Sets the preferred regions for geo-replicated database accounts. For example, "East US" as the preferred region.
     * <p>
     * When EnableEndpointDiscovery is true and PreferredRegions is non-empty, the SDK will prefer to use the regions in
     * the container in the order they are specified to perform operations.
     */
    public List<String> getPreferredRegions() {
        return preferredRegions;
    }

    public void setPreferredRegions(List<String> preferredRegions) {
        this.preferredRegions = preferredRegions;
    }

    /**
     * Sets the flag to enable client telemetry which will periodically collect database operations aggregation
     * statistics, system information like cpu/memory and send it to cosmos monitoring service, which will be helpful
     * during debugging.
     * <p>
     * DEFAULT value is false indicating this is opt in feature, by default no telemetry collection.
     */
    public boolean isClientTelemetryEnabled() {
        return clientTelemetryEnabled;
    }

    public void setClientTelemetryEnabled(boolean clientTelemetryEnabled) {
        this.clientTelemetryEnabled = clientTelemetryEnabled;
    }

    /**
     * Enables connections sharing across multiple Cosmos Clients. The default is false. When you have multiple
     * instances of Cosmos Client in the same JVM interacting to multiple Cosmos accounts, enabling this allows
     * connection sharing in Direct mode if possible between instances of Cosmos Client.
     *
     * Please note, when setting this option, the connection configuration (e.g., socket timeout config, idle timeout
     * config) of the first instantiated client will be used for all other client instances.
     */
    public boolean isConnectionSharingAcrossClientsEnabled() {
        return connectionSharingAcrossClientsEnabled;
    }

    public void setConnectionSharingAcrossClientsEnabled(boolean connectionSharingAcrossClientsEnabled) {
        this.connectionSharingAcrossClientsEnabled = connectionSharingAcrossClientsEnabled;
    }

    /**
     * Sets the flag to enable writes on any regions for geo-replicated database accounts in the Azure Cosmos DB
     * service.
     * <p>
     * When the value of this property is true, the SDK will direct write operations to available writable regions of
     * geo-replicated database account. Writable regions are ordered by PreferredRegions property. Setting the property
     * value to true has no effect until EnableMultipleWriteRegions in DatabaseAccount is also set to true.
     * <p>
     * DEFAULT value is true indicating that writes are directed to available writable regions of geo-replicated
     * database account.
     */
    public boolean isMultipleWriteRegionsEnabled() {
        return multipleWriteRegionsEnabled;
    }

    public void setMultipleWriteRegionsEnabled(boolean multipleWriteRegionsEnabled) {
        this.multipleWriteRegionsEnabled = multipleWriteRegionsEnabled;
    }

    /**
     * Sets whether to allow for reads to go to multiple regions configured on an account of Azure Cosmos DB service.
     * <p>
     * DEFAULT value is true.
     * <p>
     * If this property is not set, the default is true for all Consistency Levels other than Bounded Staleness, The
     * default is false for Bounded Staleness. 1. {@link #endpointDiscoveryEnabled} is true 2. the Azure Cosmos DB
     * account has more than one region
     */
    public boolean isReadRequestsFallbackEnabled() {
        return readRequestsFallbackEnabled;
    }

    public void setReadRequestsFallbackEnabled(boolean readRequestsFallbackEnabled) {
        this.readRequestsFallbackEnabled = readRequestsFallbackEnabled;
    }

    /**
     * An SQL query to execute on a given resources. To learn more about Cosmos SQL API, check this link
     * {@link https://docs.microsoft.com/en-us/azure/cosmos-db/sql-query-getting-started}
     */
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Sets throughput of the resources in the Azure Cosmos DB service.
     */
    public ThroughputProperties getThroughputProperties() {
        return throughputProperties;
    }

    public void setThroughputProperties(ThroughputProperties throughputProperties) {
        this.throughputProperties = throughputProperties;
    }

    /**
     * The CosmosDB operation that can be used with this component on the producer.
     */
    public CosmosDbOperationsDefinition getOperation() {
        return operation;
    }

    public void setOperation(CosmosDbOperationsDefinition operation) {
        this.operation = operation;
    }

    // *************************************************
    //
    // *************************************************

    public CosmosDbConfiguration copy() {
        try {
            return (CosmosDbConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
