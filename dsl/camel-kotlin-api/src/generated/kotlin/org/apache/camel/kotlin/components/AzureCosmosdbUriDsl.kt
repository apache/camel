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

  public fun databaseName(databaseName: String) {
    this.databaseName = databaseName
    it.url("$databaseName/$containerName")
  }

  public fun containerName(containerName: String) {
    this.containerName = containerName
    it.url("$databaseName/$containerName")
  }

  public fun clientTelemetryEnabled(clientTelemetryEnabled: String) {
    it.property("clientTelemetryEnabled", clientTelemetryEnabled)
  }

  public fun clientTelemetryEnabled(clientTelemetryEnabled: Boolean) {
    it.property("clientTelemetryEnabled", clientTelemetryEnabled.toString())
  }

  public fun connectionSharingAcrossClientsEnabled(connectionSharingAcrossClientsEnabled: String) {
    it.property("connectionSharingAcrossClientsEnabled", connectionSharingAcrossClientsEnabled)
  }

  public fun connectionSharingAcrossClientsEnabled(connectionSharingAcrossClientsEnabled: Boolean) {
    it.property("connectionSharingAcrossClientsEnabled",
        connectionSharingAcrossClientsEnabled.toString())
  }

  public fun consistencyLevel(consistencyLevel: String) {
    it.property("consistencyLevel", consistencyLevel)
  }

  public fun containerPartitionKeyPath(containerPartitionKeyPath: String) {
    it.property("containerPartitionKeyPath", containerPartitionKeyPath)
  }

  public fun contentResponseOnWriteEnabled(contentResponseOnWriteEnabled: String) {
    it.property("contentResponseOnWriteEnabled", contentResponseOnWriteEnabled)
  }

  public fun contentResponseOnWriteEnabled(contentResponseOnWriteEnabled: Boolean) {
    it.property("contentResponseOnWriteEnabled", contentResponseOnWriteEnabled.toString())
  }

  public fun cosmosAsyncClient(cosmosAsyncClient: String) {
    it.property("cosmosAsyncClient", cosmosAsyncClient)
  }

  public fun createContainerIfNotExists(createContainerIfNotExists: String) {
    it.property("createContainerIfNotExists", createContainerIfNotExists)
  }

  public fun createContainerIfNotExists(createContainerIfNotExists: Boolean) {
    it.property("createContainerIfNotExists", createContainerIfNotExists.toString())
  }

  public fun createDatabaseIfNotExists(createDatabaseIfNotExists: String) {
    it.property("createDatabaseIfNotExists", createDatabaseIfNotExists)
  }

  public fun createDatabaseIfNotExists(createDatabaseIfNotExists: Boolean) {
    it.property("createDatabaseIfNotExists", createDatabaseIfNotExists.toString())
  }

  public fun databaseEndpoint(databaseEndpoint: String) {
    it.property("databaseEndpoint", databaseEndpoint)
  }

  public fun multipleWriteRegionsEnabled(multipleWriteRegionsEnabled: String) {
    it.property("multipleWriteRegionsEnabled", multipleWriteRegionsEnabled)
  }

  public fun multipleWriteRegionsEnabled(multipleWriteRegionsEnabled: Boolean) {
    it.property("multipleWriteRegionsEnabled", multipleWriteRegionsEnabled.toString())
  }

  public fun preferredRegions(preferredRegions: String) {
    it.property("preferredRegions", preferredRegions)
  }

  public fun readRequestsFallbackEnabled(readRequestsFallbackEnabled: String) {
    it.property("readRequestsFallbackEnabled", readRequestsFallbackEnabled)
  }

  public fun readRequestsFallbackEnabled(readRequestsFallbackEnabled: Boolean) {
    it.property("readRequestsFallbackEnabled", readRequestsFallbackEnabled.toString())
  }

  public fun throughputProperties(throughputProperties: String) {
    it.property("throughputProperties", throughputProperties)
  }

  public fun changeFeedProcessorOptions(changeFeedProcessorOptions: String) {
    it.property("changeFeedProcessorOptions", changeFeedProcessorOptions)
  }

  public fun createLeaseContainerIfNotExists(createLeaseContainerIfNotExists: String) {
    it.property("createLeaseContainerIfNotExists", createLeaseContainerIfNotExists)
  }

  public fun createLeaseContainerIfNotExists(createLeaseContainerIfNotExists: Boolean) {
    it.property("createLeaseContainerIfNotExists", createLeaseContainerIfNotExists.toString())
  }

  public fun createLeaseDatabaseIfNotExists(createLeaseDatabaseIfNotExists: String) {
    it.property("createLeaseDatabaseIfNotExists", createLeaseDatabaseIfNotExists)
  }

  public fun createLeaseDatabaseIfNotExists(createLeaseDatabaseIfNotExists: Boolean) {
    it.property("createLeaseDatabaseIfNotExists", createLeaseDatabaseIfNotExists.toString())
  }

  public fun hostName(hostName: String) {
    it.property("hostName", hostName)
  }

  public fun leaseContainerName(leaseContainerName: String) {
    it.property("leaseContainerName", leaseContainerName)
  }

  public fun leaseDatabaseName(leaseDatabaseName: String) {
    it.property("leaseDatabaseName", leaseDatabaseName)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun itemId(itemId: String) {
    it.property("itemId", itemId)
  }

  public fun itemPartitionKey(itemPartitionKey: String) {
    it.property("itemPartitionKey", itemPartitionKey)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun queryRequestOptions(queryRequestOptions: String) {
    it.property("queryRequestOptions", queryRequestOptions)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun indexingPolicy(indexingPolicy: String) {
    it.property("indexingPolicy", indexingPolicy)
  }

  public fun accountKey(accountKey: String) {
    it.property("accountKey", accountKey)
  }

  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }
}
