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

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
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
    @Metadata(required = false)
    private String accountKey;
    @UriParam(label = "security", secret = false)
    @Metadata(required = false)
    private boolean useDefaultIdentity;
    @UriParam(label = "common")
    @Metadata(required = true)
    private String databaseEndpoint;
    @UriParam(label = "common")
    private String containerPartitionKeyPath;
    @UriParam(label = "common")
    @Metadata(autowired = true)
    private CosmosAsyncClient cosmosAsyncClient;
    @UriParam(label = "common", defaultValue = "SESSION")
    private ConsistencyLevel consistencyLevel = ConsistencyLevel.SESSION;
    @UriParam(label = "common")
    private String preferredRegions;
    @UriParam(label = "common", defaultValue = "false")
    private boolean clientTelemetryEnabled;
    @UriParam(label = "common", defaultValue = "false")
    private boolean connectionSharingAcrossClientsEnabled;
    @UriParam(label = "common", defaultValue = "true")
    private boolean multipleWriteRegionsEnabled = true;
    @UriParam(label = "common", defaultValue = "true")
    private boolean readRequestsFallbackEnabled = true;
    @UriParam(label = "common", defaultValue = "true")
    private boolean contentResponseOnWriteEnabled = true;
    @UriParam(label = "common", defaultValue = "false")
    private boolean createDatabaseIfNotExists;
    @UriParam(label = "common", defaultValue = "false")
    private boolean createContainerIfNotExists;
    @UriParam(label = "common")
    private ThroughputProperties throughputProperties;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean createLeaseDatabaseIfNotExists;
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean createLeaseContainerIfNotExists;
    @UriParam(label = "consumer", defaultValue = "camel-lease")
    private String leaseContainerName = "camel-lease";
    @UriParam(label = "consumer")
    private String leaseDatabaseName;
    @UriParam(label = "consumer")
    private String hostName;
    @UriParam(label = "consumer")
    private ChangeFeedProcessorOptions changeFeedProcessorOptions;
    @UriParam(label = "producer")
    private String query;
    @UriParam(label = "producer")
    private String itemPartitionKey;
    @UriParam(label = "producer")
    private String itemId;
    @UriParam(label = "producer")
    private CosmosQueryRequestOptions queryRequestOptions;
    @UriParam(label = "producer", defaultValue = "listDatabases")
    private CosmosDbOperationsDefinition operation = CosmosDbOperationsDefinition.listDatabases;

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
     * Indicates whether to use the default identity mechanism instead of the access key.
     */
    public boolean isUseDefaultIdentity() {
        return useDefaultIdentity;
    }

    public void setUseDefaultIdentity(boolean useDefaultIdentity) {
        this.useDefaultIdentity = useDefaultIdentity;
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
    public String getItemPartitionKey() {
        return itemPartitionKey;
    }

    public void setItemPartitionKey(String itemPartitionKey) {
        this.itemPartitionKey = itemPartitionKey;
    }

    /**
     * Sets the itemId in case needed for operation on item like delete, replace
     */
    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
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
     * Sets if the component should create Cosmos lease database for the consumer automatically in case it doesn't exist
     * in Cosmos account
     */
    public boolean isCreateLeaseDatabaseIfNotExists() {
        return createLeaseDatabaseIfNotExists;
    }

    public void setCreateLeaseDatabaseIfNotExists(boolean createLeaseDatabaseIfNotExists) {
        this.createLeaseDatabaseIfNotExists = createLeaseDatabaseIfNotExists;
    }

    /**
     * Sets if the component should create Cosmos lease container for the consumer automatically in case it doesn't
     * exist in Cosmos database
     */
    public boolean isCreateLeaseContainerIfNotExists() {
        return createLeaseContainerIfNotExists;
    }

    public void setCreateLeaseContainerIfNotExists(boolean createLeaseContainerIfNotExists) {
        this.createLeaseContainerIfNotExists = createLeaseContainerIfNotExists;
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
     * Set additional QueryRequestOptions that can be used with queryItems, queryContainers, queryDatabases,
     * listDatabases, listItems, listContainers operations
     */
    public CosmosQueryRequestOptions getQueryRequestOptions() {
        return queryRequestOptions;
    }

    public void setQueryRequestOptions(CosmosQueryRequestOptions queryRequestOptions) {
        this.queryRequestOptions = queryRequestOptions;
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
     * Sets the comma separated preferred regions for geo-replicated database accounts. For example, "East US" as the
     * preferred region.
     * <p>
     * When EnableEndpointDiscovery is true and PreferredRegions is non-empty, the SDK will prefer to use the regions in
     * the container in the order they are specified to perform operations.
     */
    public String getPreferredRegions() {
        return preferredRegions;
    }

    public void setPreferredRegions(String preferredRegions) {
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
     * Sets the boolean to only return the headers and status code in Cosmos DB response in case of Create, Update and
     * Delete operations on CosmosItem.
     *
     * In Consumer, it is enabled by default because of the ChangeFeed in the consumer that needs this flag to be
     * enabled and thus is shouldn't be overridden. In Producer, it advised to disable it since it reduces the network
     * overhead
     */
    public boolean isContentResponseOnWriteEnabled() {
        return contentResponseOnWriteEnabled;
    }

    public void setContentResponseOnWriteEnabled(boolean contentResponseOnWriteEnabled) {
        this.contentResponseOnWriteEnabled = contentResponseOnWriteEnabled;
    }

    /**
     * Sets the lease container which acts as a state storage and coordinates processing the change feed across multiple
     * workers. The lease container can be stored in the same account as the monitored container or in a separate
     * account.
     *
     * It will be auto created if {@link createLeaseContainerIfNotExists} is set to true.
     */
    public String getLeaseContainerName() {
        return leaseContainerName;
    }

    public void setLeaseContainerName(String leaseContainerName) {
        this.leaseContainerName = leaseContainerName;
    }

    /**
     * Sets the lease database where the {@link leaseContainerName} will be stored. If it is not specified, this
     * component will store the lease container in the same database that is specified in {@link databaseName}.
     *
     * It will be auto created if {@link createLeaseDatabaseIfNotExists} is set to true.
     */
    public String getLeaseDatabaseName() {
        return leaseDatabaseName;
    }

    public void setLeaseDatabaseName(String leaseDatabaseName) {
        this.leaseDatabaseName = leaseDatabaseName;
    }

    /**
     * Sets the hostname. The host: a host is an application instance that uses the change feed processor to listen for
     * changes. Multiple instances with the same lease configuration can run in parallel, but each instance should have
     * a different instance name.
     *
     * If not specified, this will be a generated random hostname.
     */
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Sets the {@link ChangeFeedProcessorOptions} to be used. Unless specifically set the default values that will be
     * used are:
     * <ul>
     * <li>maximum items per page or FeedResponse: 100</li>
     * <li>lease renew interval: 17 seconds</li>
     * <li>lease acquire interval: 13 seconds</li>
     * <li>lease expiration interval: 60 seconds</li>
     * <li>feed poll delay: 5 seconds</li>
     * <li>maximum scale count: unlimited</li>
     * </ul>
     */
    public ChangeFeedProcessorOptions getChangeFeedProcessorOptions() {
        return changeFeedProcessorOptions;
    }

    public void setChangeFeedProcessorOptions(ChangeFeedProcessorOptions changeFeedProcessorOptions) {
        this.changeFeedProcessorOptions = changeFeedProcessorOptions;
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
