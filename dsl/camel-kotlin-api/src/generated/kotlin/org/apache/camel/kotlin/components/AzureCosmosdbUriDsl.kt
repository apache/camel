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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * To read and write records to the CosmosDB database on Azure cloud platform.
 */
public fun UriDsl.`azure-cosmosdb`(i: AzureCosmosdbUriDsl.() -> Unit) {
  AzureCosmosdbUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureCosmosdbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-cosmosdb")
  }

  private var databaseName: String = ""

  private var containerName: String = ""

  /**
   * The name of the Cosmos database that component should connect to. In case you are producing
   * data and have createDatabaseIfNotExists=true, the component will automatically auto create a
   * Cosmos database.
   */
  public fun databaseName(databaseName: String) {
    this.databaseName = databaseName
    it.url("$databaseName/$containerName")
  }

  /**
   * The name of the Cosmos container that component should connect to. In case you are producing
   * data and have createContainerIfNotExists=true, the component will automatically auto create a
   * Cosmos container.
   */
  public fun containerName(containerName: String) {
    this.containerName = containerName
    it.url("$databaseName/$containerName")
  }

  /**
   * Sets the flag to enable client telemetry which will periodically collect database operations
   * aggregation statistics, system information like cpu/memory and send it to cosmos monitoring
   * service, which will be helpful during debugging. DEFAULT value is false indicating this is an
   * opt-in feature, by default no telemetry collection.
   */
  public fun clientTelemetryEnabled(clientTelemetryEnabled: String) {
    it.property("clientTelemetryEnabled", clientTelemetryEnabled)
  }

  /**
   * Sets the flag to enable client telemetry which will periodically collect database operations
   * aggregation statistics, system information like cpu/memory and send it to cosmos monitoring
   * service, which will be helpful during debugging. DEFAULT value is false indicating this is an
   * opt-in feature, by default no telemetry collection.
   */
  public fun clientTelemetryEnabled(clientTelemetryEnabled: Boolean) {
    it.property("clientTelemetryEnabled", clientTelemetryEnabled.toString())
  }

  /**
   * Enables connections sharing across multiple Cosmos Clients. The default is false. When you have
   * multiple instances of Cosmos Client in the same JVM interacting with multiple Cosmos accounts,
   * enabling this allows connection sharing in Direct mode if possible between instances of Cosmos
   * Client. Please note, when setting this option, the connection configuration (e.g., socket timeout
   * config, idle timeout config) of the first instantiated client will be used for all other client
   * instances.
   */
  public fun connectionSharingAcrossClientsEnabled(connectionSharingAcrossClientsEnabled: String) {
    it.property("connectionSharingAcrossClientsEnabled", connectionSharingAcrossClientsEnabled)
  }

  /**
   * Enables connections sharing across multiple Cosmos Clients. The default is false. When you have
   * multiple instances of Cosmos Client in the same JVM interacting with multiple Cosmos accounts,
   * enabling this allows connection sharing in Direct mode if possible between instances of Cosmos
   * Client. Please note, when setting this option, the connection configuration (e.g., socket timeout
   * config, idle timeout config) of the first instantiated client will be used for all other client
   * instances.
   */
  public fun connectionSharingAcrossClientsEnabled(connectionSharingAcrossClientsEnabled: Boolean) {
    it.property("connectionSharingAcrossClientsEnabled",
        connectionSharingAcrossClientsEnabled.toString())
  }

  /**
   * Sets the consistency levels supported for Azure Cosmos DB client operations in the Azure Cosmos
   * DB service. The requested ConsistencyLevel must match or be weaker than that provisioned for the
   * database account. Consistency levels by order of strength are STRONG, BOUNDED_STALENESS, SESSION
   * and EVENTUAL. Refer to consistency level documentation for additional details:
   * https://docs.microsoft.com/en-us/azure/cosmos-db/consistency-levels
   */
  public fun consistencyLevel(consistencyLevel: String) {
    it.property("consistencyLevel", consistencyLevel)
  }

  /**
   * Sets the container partition key path.
   */
  public fun containerPartitionKeyPath(containerPartitionKeyPath: String) {
    it.property("containerPartitionKeyPath", containerPartitionKeyPath)
  }

  /**
   * Sets the boolean to only return the headers and status code in Cosmos DB response in case of
   * Create, Update and Delete operations on CosmosItem. In Consumer, it is enabled by default because
   * of the ChangeFeed in the consumer that needs this flag to be enabled, and thus it shouldn't be
   * overridden. In Producer, it is advised to disable it since it reduces the network overhead
   */
  public fun contentResponseOnWriteEnabled(contentResponseOnWriteEnabled: String) {
    it.property("contentResponseOnWriteEnabled", contentResponseOnWriteEnabled)
  }

  /**
   * Sets the boolean to only return the headers and status code in Cosmos DB response in case of
   * Create, Update and Delete operations on CosmosItem. In Consumer, it is enabled by default because
   * of the ChangeFeed in the consumer that needs this flag to be enabled, and thus it shouldn't be
   * overridden. In Producer, it is advised to disable it since it reduces the network overhead
   */
  public fun contentResponseOnWriteEnabled(contentResponseOnWriteEnabled: Boolean) {
    it.property("contentResponseOnWriteEnabled", contentResponseOnWriteEnabled.toString())
  }

  /**
   * Inject an external CosmosAsyncClient into the component which provides a client-side logical
   * representation of the Azure Cosmos DB service. This asynchronous client is used to configure and
   * execute requests against the service.
   */
  public fun cosmosAsyncClient(cosmosAsyncClient: String) {
    it.property("cosmosAsyncClient", cosmosAsyncClient)
  }

  /**
   * Sets if the component should create the Cosmos container automatically in case it doesn't exist
   * in the Cosmos database
   */
  public fun createContainerIfNotExists(createContainerIfNotExists: String) {
    it.property("createContainerIfNotExists", createContainerIfNotExists)
  }

  /**
   * Sets if the component should create the Cosmos container automatically in case it doesn't exist
   * in the Cosmos database
   */
  public fun createContainerIfNotExists(createContainerIfNotExists: Boolean) {
    it.property("createContainerIfNotExists", createContainerIfNotExists.toString())
  }

  /**
   * Sets if the component should create the Cosmos database automatically in case it doesn't exist
   * in the Cosmos account
   */
  public fun createDatabaseIfNotExists(createDatabaseIfNotExists: String) {
    it.property("createDatabaseIfNotExists", createDatabaseIfNotExists)
  }

  /**
   * Sets if the component should create the Cosmos database automatically in case it doesn't exist
   * in the Cosmos account
   */
  public fun createDatabaseIfNotExists(createDatabaseIfNotExists: Boolean) {
    it.property("createDatabaseIfNotExists", createDatabaseIfNotExists.toString())
  }

  /**
   * Sets the Azure Cosmos database endpoint the component will connect to.
   */
  public fun databaseEndpoint(databaseEndpoint: String) {
    it.property("databaseEndpoint", databaseEndpoint)
  }

  /**
   * Sets the flag to enable writes on any regions for geo-replicated database accounts in the Azure
   * Cosmos DB service. When the value of this property is true, the SDK will direct write operations
   * to available writable regions of geo-replicated database account. Writable regions are ordered by
   * PreferredRegions property. Setting the property value to true has no effect until
   * EnableMultipleWriteRegions in DatabaseAccount is also set to true. DEFAULT value is true
   * indicating that writes are directed to available writable regions of geo-replicated database
   * account.
   */
  public fun multipleWriteRegionsEnabled(multipleWriteRegionsEnabled: String) {
    it.property("multipleWriteRegionsEnabled", multipleWriteRegionsEnabled)
  }

  /**
   * Sets the flag to enable writes on any regions for geo-replicated database accounts in the Azure
   * Cosmos DB service. When the value of this property is true, the SDK will direct write operations
   * to available writable regions of geo-replicated database account. Writable regions are ordered by
   * PreferredRegions property. Setting the property value to true has no effect until
   * EnableMultipleWriteRegions in DatabaseAccount is also set to true. DEFAULT value is true
   * indicating that writes are directed to available writable regions of geo-replicated database
   * account.
   */
  public fun multipleWriteRegionsEnabled(multipleWriteRegionsEnabled: Boolean) {
    it.property("multipleWriteRegionsEnabled", multipleWriteRegionsEnabled.toString())
  }

  /**
   * Sets the comma separated preferred regions for geo-replicated database accounts. For example,
   * East US as the preferred region. When EnableEndpointDiscovery is true and PreferredRegions is
   * non-empty, the SDK will prefer to use the regions in the container in the order they are specified
   * to perform operations.
   */
  public fun preferredRegions(preferredRegions: String) {
    it.property("preferredRegions", preferredRegions)
  }

  /**
   * Sets whether to allow for reads to go to multiple regions configured on an account of Azure
   * Cosmos DB service. DEFAULT value is true. If this property is not set, the default is true for all
   * Consistency Levels other than Bounded Staleness, The default is false for Bounded Staleness. 1.
   * endpointDiscoveryEnabled is true 2. the Azure Cosmos DB account has more than one region
   */
  public fun readRequestsFallbackEnabled(readRequestsFallbackEnabled: String) {
    it.property("readRequestsFallbackEnabled", readRequestsFallbackEnabled)
  }

  /**
   * Sets whether to allow for reads to go to multiple regions configured on an account of Azure
   * Cosmos DB service. DEFAULT value is true. If this property is not set, the default is true for all
   * Consistency Levels other than Bounded Staleness, The default is false for Bounded Staleness. 1.
   * endpointDiscoveryEnabled is true 2. the Azure Cosmos DB account has more than one region
   */
  public fun readRequestsFallbackEnabled(readRequestsFallbackEnabled: Boolean) {
    it.property("readRequestsFallbackEnabled", readRequestsFallbackEnabled.toString())
  }

  /**
   * Sets throughput of the resources in the Azure Cosmos DB service.
   */
  public fun throughputProperties(throughputProperties: String) {
    it.property("throughputProperties", throughputProperties)
  }

  /**
   * Sets the ChangeFeedProcessorOptions to be used. Unless specifically set the default values that
   * will be used are: maximum items per page or FeedResponse: 100 lease renew interval: 17 seconds
   * lease acquire interval: 13 seconds lease expiration interval: 60 seconds feed poll delay: 5
   * seconds maximum scale count: unlimited
   */
  public fun changeFeedProcessorOptions(changeFeedProcessorOptions: String) {
    it.property("changeFeedProcessorOptions", changeFeedProcessorOptions)
  }

  /**
   * Sets if the component should create Cosmos lease container for the consumer automatically in
   * case it doesn't exist in Cosmos database
   */
  public fun createLeaseContainerIfNotExists(createLeaseContainerIfNotExists: String) {
    it.property("createLeaseContainerIfNotExists", createLeaseContainerIfNotExists)
  }

  /**
   * Sets if the component should create Cosmos lease container for the consumer automatically in
   * case it doesn't exist in Cosmos database
   */
  public fun createLeaseContainerIfNotExists(createLeaseContainerIfNotExists: Boolean) {
    it.property("createLeaseContainerIfNotExists", createLeaseContainerIfNotExists.toString())
  }

  /**
   * Sets if the component should create the Cosmos lease database for the consumer automatically in
   * case it doesn't exist in the Cosmos account
   */
  public fun createLeaseDatabaseIfNotExists(createLeaseDatabaseIfNotExists: String) {
    it.property("createLeaseDatabaseIfNotExists", createLeaseDatabaseIfNotExists)
  }

  /**
   * Sets if the component should create the Cosmos lease database for the consumer automatically in
   * case it doesn't exist in the Cosmos account
   */
  public fun createLeaseDatabaseIfNotExists(createLeaseDatabaseIfNotExists: Boolean) {
    it.property("createLeaseDatabaseIfNotExists", createLeaseDatabaseIfNotExists.toString())
  }

  /**
   * Sets the hostname. The host: a host is an application instance that uses the change feed
   * processor to listen for changes. Multiple instances with the same lease configuration can run in
   * parallel, but each instance should have a different instance name. If not specified, this will be
   * a generated random hostname.
   */
  public fun hostName(hostName: String) {
    it.property("hostName", hostName)
  }

  /**
   * Sets the lease container which acts as a state storage and coordinates processing the change
   * feed across multiple workers. The lease container can be stored in the same account as the
   * monitored container or in a separate account. It will be auto-created if
   * createLeaseContainerIfNotExists is set to true.
   */
  public fun leaseContainerName(leaseContainerName: String) {
    it.property("leaseContainerName", leaseContainerName)
  }

  /**
   * Sets the lease database where the leaseContainerName will be stored. If it is not specified,
   * this component will store the lease container in the same database that is specified in
   * databaseName. It will be auto-created if createLeaseDatabaseIfNotExists is set to true.
   */
  public fun leaseDatabaseName(leaseDatabaseName: String) {
    it.property("leaseDatabaseName", leaseDatabaseName)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * Sets the itemId in case needed for operation on item like delete, replace
   */
  public fun itemId(itemId: String) {
    it.property("itemId", itemId)
  }

  /**
   * Sets partition key. Represents a partition key value in the Azure Cosmos DB database service. A
   * partition key identifies the partition where the item is stored in.
   */
  public fun itemPartitionKey(itemPartitionKey: String) {
    it.property("itemPartitionKey", itemPartitionKey)
  }

  /**
   * The CosmosDB operation that can be used with this component on the producer.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * An SQL query to execute on a given resources. To learn more about Cosmos SQL API, check this
   * link {link https://docs.microsoft.com/en-us/azure/cosmos-db/sql-query-getting-started}
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * Set additional QueryRequestOptions that can be used with queryItems, queryContainers,
   * queryDatabases, listDatabases, listItems, listContainers operations
   */
  public fun queryRequestOptions(queryRequestOptions: String) {
    it.property("queryRequestOptions", queryRequestOptions)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * The CosmosDB Indexing Policy that will be set in case of container creation, this option is
   * related to createLeaseContainerIfNotExists and it will be taken into account when the latter is
   * true.
   */
  public fun indexingPolicy(indexingPolicy: String) {
    it.property("indexingPolicy", indexingPolicy)
  }

  /**
   * Sets either a master or readonly key used to perform authentication for accessing resource.
   */
  public fun accountKey(accountKey: String) {
    it.property("accountKey", accountKey)
  }

  /**
   * Determines the credential strategy to adopt
   */
  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }
}
