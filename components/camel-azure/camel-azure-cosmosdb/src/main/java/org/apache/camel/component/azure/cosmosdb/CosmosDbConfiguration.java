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
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.azure.common.CredentialType.SHARED_ACCOUNT_KEY;

@UriParams
public class CosmosDbConfiguration implements Cloneable {

    @UriPath(description = "The name of the database. The consumer requires it. The producer uses it for each operation"
                           + " except listDatabases and queryDatabases, and the CamelAzureCosmosDbDatabaseName header"
                           + " replaces it.")
    private String databaseName;
    @UriPath(description = "The name of the container. The consumer requires it, and receives the changes of this"
                           + " container. The producer uses it for the operations on a container and on its items, and the"
                           + " CamelAzureCosmosDbContainerName header replaces it.")
    private String containerName;
    @UriParam(label = "security", security = "secret",
              description = "The key of the Azure Cosmos DB account, a read-write key or a read-only key, that authorizes"
                            + " the requests when credentialType is SHARED_ACCOUNT_KEY. Not used when the"
                            + " cosmosAsyncClient option is set.")
    @Metadata(required = false)
    private String accountKey;
    @UriParam(label = "common",
              description = "The URI of the Azure Cosmos DB account, for example https://myaccount.documents.azure.com:443/."
                            + " Required unless the cosmosAsyncClient option is set, which replaces it.")
    @Metadata(required = true)
    private String databaseEndpoint;
    @UriParam(label = "common",
              description = "The partition key path of a container that the component creates, for example /myPartitionKey."
                            + " A / is added in front when it is missing. Required by createContainer and when"
                            + " createContainerIfNotExists is true, and the CamelAzureCosmosDbContainerPartitionKeyPath header"
                            + " replaces it for the producer.")
    private String containerPartitionKeyPath;
    @UriParam(label = "common",
              description = "To use a custom com.azure.cosmos.CosmosAsyncClient for all requests. When this option is set or"
                            + " autowired, the databaseEndpoint, accountKey, credentialType, consistencyLevel,"
                            + " preferredRegions, clientTelemetryEnabled, connectionSharingAcrossClientsEnabled,"
                            + " multipleWriteRegionsEnabled, readRequestsFallbackEnabled and contentResponseOnWriteEnabled"
                            + " options are not used. The endpoint does not close a client that is set or autowired.")
    @Metadata(autowired = true)
    private CosmosAsyncClient cosmosAsyncClient;
    @UriParam(label = "common", defaultValue = "SESSION",
              description = "The default consistency level of the requests of the client. It must be the same as or weaker"
                            + " than the default consistency level of the account. From the strongest to the weakest: Strong,"
                            + " BoundedStaleness, Session, ConsistentPrefix and Eventual.")
    private ConsistencyLevel consistencyLevel = ConsistencyLevel.SESSION;
    @UriParam(label = "common",
              description = "A comma-separated list of the preferred regions of a geo-replicated account, in order of"
                            + " preference, for example East US,West US. The client sends its requests to the first"
                            + " available region of this list.")
    private String preferredRegions;
    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to enable client telemetry. This has no effect: the azure-cosmos SDK ignores this flag,"
                            + " as it can no longer send client telemetry to the service.")
    private boolean clientTelemetryEnabled;
    @UriParam(label = "common", defaultValue = "false",
              description = "Whether the client shares its connections in Direct mode with the other clients of the JVM that"
                            + " enable this option, such as the clients of other endpoints or of other accounts. The"
                            + " connection settings of the first of these clients, such as its timeouts, then apply to all"
                            + " of them.")
    private boolean connectionSharingAcrossClientsEnabled;
    @UriParam(label = "common", defaultValue = "true",
              description = "Whether the client sends writes to any writable region of a geo-replicated account, in the order"
                            + " of preferredRegions. Has no effect unless multi-region writes are enabled on the account.")
    private boolean multipleWriteRegionsEnabled = true;
    @UriParam(label = "common", defaultValue = "true",
              description = "Whether the client can send reads to the other regions of the account, for example when a"
                            + " region is not available. Has no effect unless the account has more than one region.")
    private boolean readRequestsFallbackEnabled = true;
    @UriParam(label = "common", defaultValue = "true",
              description = "Whether Cosmos DB returns the item in the response of createItem, upsertItem and replaceItem,"
                            + " which the producer then sets as the body. If false, the body is null after these operations,"
                            + " which saves network traffic. The consumer requires true: its change feed processor fails"
                            + " to start with false.")
    private boolean contentResponseOnWriteEnabled = true;
    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to create the database when it does not exist, with the throughputProperties option. The"
                            + " consumer creates it when it starts, and the producer before each operation on the database,"
                            + " its containers or their items, except deleteDatabase and deleteContainer. The"
                            + " CamelAzureCosmosDbCreateDatabaseIfNotExist header replaces it for the producer.")
    private boolean createDatabaseIfNotExists;
    @UriParam(label = "common", defaultValue = "false",
              description = "Whether to create the container when it does not exist, with the containerPartitionKeyPath and"
                            + " throughputProperties options, and the indexingPolicy option for the producer. The consumer"
                            + " creates it when it starts, and the producer before replaceContainerThroughput and each"
                            + " operation on items except deleteItem. The CamelAzureCosmosDbCreateContainerIfNotExist header"
                            + " replaces it for the producer.")
    private boolean createContainerIfNotExists;
    @UriParam(label = "common, advanced",
              description = "The com.azure.cosmos.models.IndexingPolicy of a container that the producer creates, with"
                            + " createContainer or because createContainerIfNotExists is true. If not set, the container gets"
                            + " the default indexing policy of Cosmos DB. The CamelAzureCosmosDbIndexingPolicy header replaces"
                            + " it, and the consumer does not use it.")
    private IndexingPolicy indexingPolicy;
    @UriParam(label = "common",
              description = "The throughput, as a com.azure.cosmos.models.ThroughputProperties, for createDatabase,"
                            + " createContainer, replaceDatabaseThroughput and replaceContainerThroughput, and for the"
                            + " databases and containers that the create*IfNotExists options create, the lease database and"
                            + " lease container included. The CamelAzureCosmosDbThroughputProperties header replaces it for"
                            + " the producer.")
    private ThroughputProperties throughputProperties;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer creates the lease database when it does not exist, with the"
                            + " throughputProperties option.")
    private boolean createLeaseDatabaseIfNotExists;
    @UriParam(label = "consumer", defaultValue = "false",
              description = "Whether the consumer creates the lease container when it does not exist, with the partition key"
                            + " path /id and the throughputProperties option.")
    private boolean createLeaseContainerIfNotExists;
    @UriParam(label = "consumer", defaultValue = "camel-lease",
              description = "The name of the lease container, where the change feed processor of the consumer stores how far"
                            + " it has processed each range of the feed and which host processes it. The consumers that use"
                            + " the same lease container for the same container share the work of processing its changes. The lease container"
                            + " must have the partition key path /id.")
    private String leaseContainerName = "camel-lease";
    @UriParam(label = "consumer",
              description = "The name of the database of the lease container. If not set, the database of the databaseName"
                            + " option is used.")
    private String leaseDatabaseName;
    @UriParam(label = "consumer",
              description = "The name of the host, which identifies this consumer in the leases of the change feed processor."
                            + " The consumers that share a lease container need different names. If not set, a random name"
                            + " is generated each time the change feed processor is built.")
    private String hostName;
    @UriParam(label = "consumer",
              description = "The com.azure.cosmos.models.ChangeFeedProcessorOptions of the change feed processor, for example"
                            + " to start from the beginning of the change feed or from a point in time. If not set, the"
                            + " defaults of the SDK apply: at most 100 items per batch, a lease renew interval of 17 seconds,"
                            + " a lease acquire interval of 13 seconds, a lease expiration interval of 60 seconds, a feed poll"
                            + " delay of 5 seconds, no maximum scale count, and only the changes after the first start.")
    private ChangeFeedProcessorOptions changeFeedProcessorOptions;
    @UriParam(label = "producer",
              description = "The SQL query of queryDatabases, queryContainers and queryItems, for example SELECT * FROM c"
                            + " WHERE c.id = '1'. The CamelAzureCosmosDbQuery header replaces it.")
    private String query;
    @UriParam(label = "producer",
              description = "The value of the partition key of the item, as a String, for createItem, upsertItem,"
                            + " replaceItem, deleteItem and readItem, and of the items that readAllItems reads. If not set,"
                            + " the partition key value is null. The CamelAzureCosmosDbItemPartitionKey header replaces it.")
    private String itemPartitionKey;
    @UriParam(label = "producer",
              description = "The id of the item for readItem, replaceItem and deleteItem. The CamelAzureCosmosDbItemId header"
                            + " replaces it.")
    private String itemId;
    @UriParam(label = "producer",
              description = "The com.azure.cosmos.models.CosmosQueryRequestOptions of queryDatabases, listContainers,"
                            + " queryContainers, readAllItems and queryItems, for example to set the maximum number of items"
                            + " per page. The CamelAzureCosmosDbQueryRequestOptions header replaces it.")
    private CosmosQueryRequestOptions queryRequestOptions;
    @UriParam(label = "producer", defaultValue = "listDatabases",
              description = "The operation that the producer performs. The operations on a database use databaseName, and"
                            + " the operations on a container or its items also use containerName. The"
                            + " CamelAzureCosmosDbOperation header replaces this option.")
    private CosmosDbOperationsDefinition operation = CosmosDbOperationsDefinition.listDatabases;
    @UriParam(label = "security", enums = "SHARED_ACCOUNT_KEY,AZURE_IDENTITY",
              defaultValue = "SHARED_ACCOUNT_KEY",
              description = "How the client authenticates. SHARED_ACCOUNT_KEY: with the accountKey option. AZURE_IDENTITY:"
                            + " with a DefaultAzureCredential of the Azure Identity library, which finds the credentials in"
                            + " the environment, such as environment variables, a managed identity or the Azure CLI. Not used"
                            + " when the cosmosAsyncClient option is set.")
    private CredentialType credentialType = SHARED_ACCOUNT_KEY;

    public CosmosDbConfiguration() {
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public String getDatabaseEndpoint() {
        return databaseEndpoint;
    }

    public void setDatabaseEndpoint(String databaseEndpoint) {
        this.databaseEndpoint = databaseEndpoint;
    }

    public String getContainerPartitionKeyPath() {
        return containerPartitionKeyPath;
    }

    public void setContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
    }

    public String getItemPartitionKey() {
        return itemPartitionKey;
    }

    public void setItemPartitionKey(String itemPartitionKey) {
        this.itemPartitionKey = itemPartitionKey;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public boolean isCreateDatabaseIfNotExists() {
        return createDatabaseIfNotExists;
    }

    public void setCreateDatabaseIfNotExists(boolean createDatabaseIfNotExists) {
        this.createDatabaseIfNotExists = createDatabaseIfNotExists;
    }

    public boolean isCreateContainerIfNotExists() {
        return createContainerIfNotExists;
    }

    public void setCreateContainerIfNotExists(boolean createContainerIfNotExists) {
        this.createContainerIfNotExists = createContainerIfNotExists;
    }

    public boolean isCreateLeaseDatabaseIfNotExists() {
        return createLeaseDatabaseIfNotExists;
    }

    public void setCreateLeaseDatabaseIfNotExists(boolean createLeaseDatabaseIfNotExists) {
        this.createLeaseDatabaseIfNotExists = createLeaseDatabaseIfNotExists;
    }

    public boolean isCreateLeaseContainerIfNotExists() {
        return createLeaseContainerIfNotExists;
    }

    public void setCreateLeaseContainerIfNotExists(boolean createLeaseContainerIfNotExists) {
        this.createLeaseContainerIfNotExists = createLeaseContainerIfNotExists;
    }

    public CosmosAsyncClient getCosmosAsyncClient() {
        return cosmosAsyncClient;
    }

    public void setCosmosAsyncClient(CosmosAsyncClient cosmosAsyncClient) {
        this.cosmosAsyncClient = cosmosAsyncClient;
    }

    public CosmosQueryRequestOptions getQueryRequestOptions() {
        return queryRequestOptions;
    }

    public void setQueryRequestOptions(CosmosQueryRequestOptions queryRequestOptions) {
        this.queryRequestOptions = queryRequestOptions;
    }

    public ConsistencyLevel getConsistencyLevel() {
        return consistencyLevel;
    }

    public void setConsistencyLevel(ConsistencyLevel consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public String getPreferredRegions() {
        return preferredRegions;
    }

    public void setPreferredRegions(String preferredRegions) {
        this.preferredRegions = preferredRegions;
    }

    public boolean isClientTelemetryEnabled() {
        return clientTelemetryEnabled;
    }

    public void setClientTelemetryEnabled(boolean clientTelemetryEnabled) {
        this.clientTelemetryEnabled = clientTelemetryEnabled;
    }

    public boolean isConnectionSharingAcrossClientsEnabled() {
        return connectionSharingAcrossClientsEnabled;
    }

    public void setConnectionSharingAcrossClientsEnabled(boolean connectionSharingAcrossClientsEnabled) {
        this.connectionSharingAcrossClientsEnabled = connectionSharingAcrossClientsEnabled;
    }

    public boolean isMultipleWriteRegionsEnabled() {
        return multipleWriteRegionsEnabled;
    }

    public void setMultipleWriteRegionsEnabled(boolean multipleWriteRegionsEnabled) {
        this.multipleWriteRegionsEnabled = multipleWriteRegionsEnabled;
    }

    public boolean isReadRequestsFallbackEnabled() {
        return readRequestsFallbackEnabled;
    }

    public void setReadRequestsFallbackEnabled(boolean readRequestsFallbackEnabled) {
        this.readRequestsFallbackEnabled = readRequestsFallbackEnabled;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public ThroughputProperties getThroughputProperties() {
        return throughputProperties;
    }

    public void setThroughputProperties(ThroughputProperties throughputProperties) {
        this.throughputProperties = throughputProperties;
    }

    public boolean isContentResponseOnWriteEnabled() {
        return contentResponseOnWriteEnabled;
    }

    public void setContentResponseOnWriteEnabled(boolean contentResponseOnWriteEnabled) {
        this.contentResponseOnWriteEnabled = contentResponseOnWriteEnabled;
    }

    public String getLeaseContainerName() {
        return leaseContainerName;
    }

    public void setLeaseContainerName(String leaseContainerName) {
        this.leaseContainerName = leaseContainerName;
    }

    public String getLeaseDatabaseName() {
        return leaseDatabaseName;
    }

    public void setLeaseDatabaseName(String leaseDatabaseName) {
        this.leaseDatabaseName = leaseDatabaseName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public ChangeFeedProcessorOptions getChangeFeedProcessorOptions() {
        return changeFeedProcessorOptions;
    }

    public void setChangeFeedProcessorOptions(ChangeFeedProcessorOptions changeFeedProcessorOptions) {
        this.changeFeedProcessorOptions = changeFeedProcessorOptions;
    }

    public CosmosDbOperationsDefinition getOperation() {
        return operation;
    }

    public void setOperation(CosmosDbOperationsDefinition operation) {
        this.operation = operation;
    }

    public IndexingPolicy getIndexingPolicy() {
        return indexingPolicy;
    }

    public void setIndexingPolicy(IndexingPolicy indexingPolicy) {
        this.indexingPolicy = indexingPolicy;
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
