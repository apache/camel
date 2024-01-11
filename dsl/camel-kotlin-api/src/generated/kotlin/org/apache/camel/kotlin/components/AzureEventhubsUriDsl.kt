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
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.`azure-eventhubs`(i: AzureEventhubsUriDsl.() -> Unit) {
  AzureEventhubsUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureEventhubsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-eventhubs")
  }

  private var namespace: String = ""

  private var eventHubName: String = ""

  public fun namespace(namespace: String) {
    this.namespace = namespace
    it.url("$namespace/$eventHubName")
  }

  public fun eventHubName(eventHubName: String) {
    this.eventHubName = eventHubName
    it.url("$namespace/$eventHubName")
  }

  public fun amqpRetryOptions(amqpRetryOptions: String) {
    it.property("amqpRetryOptions", amqpRetryOptions)
  }

  public fun amqpTransportType(amqpTransportType: String) {
    it.property("amqpTransportType", amqpTransportType)
  }

  public fun blobAccessKey(blobAccessKey: String) {
    it.property("blobAccessKey", blobAccessKey)
  }

  public fun blobAccountName(blobAccountName: String) {
    it.property("blobAccountName", blobAccountName)
  }

  public fun blobContainerName(blobContainerName: String) {
    it.property("blobContainerName", blobContainerName)
  }

  public fun blobStorageSharedKeyCredential(blobStorageSharedKeyCredential: String) {
    it.property("blobStorageSharedKeyCredential", blobStorageSharedKeyCredential)
  }

  public fun checkpointBatchSize(checkpointBatchSize: String) {
    it.property("checkpointBatchSize", checkpointBatchSize)
  }

  public fun checkpointBatchSize(checkpointBatchSize: Int) {
    it.property("checkpointBatchSize", checkpointBatchSize.toString())
  }

  public fun checkpointBatchTimeout(checkpointBatchTimeout: String) {
    it.property("checkpointBatchTimeout", checkpointBatchTimeout)
  }

  public fun checkpointBatchTimeout(checkpointBatchTimeout: Int) {
    it.property("checkpointBatchTimeout", checkpointBatchTimeout.toString())
  }

  public fun checkpointStore(checkpointStore: String) {
    it.property("checkpointStore", checkpointStore)
  }

  public fun consumerGroupName(consumerGroupName: String) {
    it.property("consumerGroupName", consumerGroupName)
  }

  public fun eventPosition(eventPosition: String) {
    it.property("eventPosition", eventPosition)
  }

  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
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

  public fun partitionId(partitionId: String) {
    it.property("partitionId", partitionId)
  }

  public fun partitionKey(partitionKey: String) {
    it.property("partitionKey", partitionKey)
  }

  public fun producerAsyncClient(producerAsyncClient: String) {
    it.property("producerAsyncClient", producerAsyncClient)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun connectionString(connectionString: String) {
    it.property("connectionString", connectionString)
  }

  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  public fun sharedAccessKey(sharedAccessKey: String) {
    it.property("sharedAccessKey", sharedAccessKey)
  }

  public fun sharedAccessName(sharedAccessName: String) {
    it.property("sharedAccessName", sharedAccessName)
  }

  public fun tokenCredential(tokenCredential: String) {
    it.property("tokenCredential", tokenCredential)
  }
}
