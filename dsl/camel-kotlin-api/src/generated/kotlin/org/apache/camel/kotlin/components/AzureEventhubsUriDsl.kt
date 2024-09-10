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

/**
 * Send and receive events to/from Azure Event Hubs using AMQP protocol.
 */
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

  /**
   * EventHubs namespace created in Azure Portal.
   */
  public fun namespace(namespace: String) {
    this.namespace = namespace
    it.url("$namespace/$eventHubName")
  }

  /**
   * EventHubs name under a specific namespace.
   */
  public fun eventHubName(eventHubName: String) {
    this.eventHubName = eventHubName
    it.url("$namespace/$eventHubName")
  }

  /**
   * Sets the retry policy for EventHubProducerAsyncClient. If not specified, the default retry
   * options are used.
   */
  public fun amqpRetryOptions(amqpRetryOptions: String) {
    it.property("amqpRetryOptions", amqpRetryOptions)
  }

  /**
   * Sets the transport type by which all the communication with Azure Event Hubs occurs.
   */
  public fun amqpTransportType(amqpTransportType: String) {
    it.property("amqpTransportType", amqpTransportType)
  }

  /**
   * In case you chose the default BlobCheckpointStore, this sets access key for the associated
   * azure account name to be used for authentication with azure blob services.
   */
  public fun blobAccessKey(blobAccessKey: String) {
    it.property("blobAccessKey", blobAccessKey)
  }

  /**
   * In case you chose the default BlobCheckpointStore, this sets Azure account name to be used for
   * authentication with azure blob services.
   */
  public fun blobAccountName(blobAccountName: String) {
    it.property("blobAccountName", blobAccountName)
  }

  /**
   * In case you chose the default BlobCheckpointStore, this sets the blob container that shall be
   * used by the BlobCheckpointStore to store the checkpoint offsets.
   */
  public fun blobContainerName(blobContainerName: String) {
    it.property("blobContainerName", blobContainerName)
  }

  /**
   * In case you chose the default BlobCheckpointStore, StorageSharedKeyCredential can be injected
   * to create the azure client, this holds the important authentication information.
   */
  public fun blobStorageSharedKeyCredential(blobStorageSharedKeyCredential: String) {
    it.property("blobStorageSharedKeyCredential", blobStorageSharedKeyCredential)
  }

  /**
   * Sets the batch size between each checkpoint update. Works jointly with checkpointBatchTimeout.
   */
  public fun checkpointBatchSize(checkpointBatchSize: String) {
    it.property("checkpointBatchSize", checkpointBatchSize)
  }

  /**
   * Sets the batch size between each checkpoint update. Works jointly with checkpointBatchTimeout.
   */
  public fun checkpointBatchSize(checkpointBatchSize: Int) {
    it.property("checkpointBatchSize", checkpointBatchSize.toString())
  }

  /**
   * Sets the batch timeout between each checkpoint update. Works jointly with checkpointBatchSize.
   */
  public fun checkpointBatchTimeout(checkpointBatchTimeout: String) {
    it.property("checkpointBatchTimeout", checkpointBatchTimeout)
  }

  /**
   * Sets the batch timeout between each checkpoint update. Works jointly with checkpointBatchSize.
   */
  public fun checkpointBatchTimeout(checkpointBatchTimeout: Int) {
    it.property("checkpointBatchTimeout", checkpointBatchTimeout.toString())
  }

  /**
   * Sets the CheckpointStore the EventProcessorClient will use for storing partition ownership and
   * checkpoint information. Users can, optionally, provide their own implementation of CheckpointStore
   * which will store ownership and checkpoint information. By default, it's set to use
   * com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore which stores all checkpoint
   * offsets into Azure Blob Storage.
   */
  public fun checkpointStore(checkpointStore: String) {
    it.property("checkpointStore", checkpointStore)
  }

  /**
   * Sets the name of the consumer group this consumer is associated with. Events are read in the
   * context of this group. The name of the consumer group that is created by default is $Default.
   */
  public fun consumerGroupName(consumerGroupName: String) {
    it.property("consumerGroupName", consumerGroupName)
  }

  /**
   * Sets the map containing the event position to use for each partition if a checkpoint for the
   * partition does not exist in CheckpointStore. This map is keyed off of the partition id. If there
   * is no checkpoint in CheckpointStore and there is no entry in this map, the processing of the
   * partition will start from EventPosition#latest() position.
   */
  public fun eventPosition(eventPosition: String) {
    it.property("eventPosition", eventPosition)
  }

  /**
   * Sets the count used by the receiver to control the number of events the Event Hub consumer will
   * actively receive and queue locally without regard to whether a receive operation is currently
   * active.
   */
  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  /**
   * Sets the count used by the receiver to control the number of events the Event Hub consumer will
   * actively receive and queue locally without regard to whether a receive operation is currently
   * active.
   */
  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
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
   * Sets the identifier of the Event Hub partition that the EventData events will be sent to. If
   * the identifier is not specified, the Event Hubs service will be responsible for routing events
   * that are sent to an available partition.
   */
  public fun partitionId(partitionId: String) {
    it.property("partitionId", partitionId)
  }

  /**
   * Sets a hashing key to be provided for the batch of events, which instructs the Event Hubs
   * service to map this key to a specific partition. The selection of a partition is stable for a
   * given partition hashing key. Should any other batches of events be sent using the same exact
   * partition hashing key, the Event Hubs service will route them all to the same partition. This
   * should be specified only when there is a need to group events by partition, but there is
   * flexibility into which partition they are routed. If ensuring that a batch of events is sent only
   * to a specific partition, it is recommended that the identifier of the position be specified
   * directly when sending the batch.
   */
  public fun partitionKey(partitionKey: String) {
    it.property("partitionKey", partitionKey)
  }

  /**
   * Sets the EventHubProducerAsyncClient.An asynchronous producer responsible for transmitting
   * EventData to a specific Event Hub, grouped together in batches. Depending on the
   * com.azure.messaging.eventhubs.models.CreateBatchOptions options specified when creating an
   * com.azure.messaging.eventhubs.EventDataBatch, the events may be automatically routed to an
   * available partition or specific to a partition. Use by this component to produce the data in camel
   * producer.
   */
  public fun producerAsyncClient(producerAsyncClient: String) {
    it.property("producerAsyncClient", producerAsyncClient)
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
   * Instead of supplying namespace, sharedAccessKey, sharedAccessName, etc. you can supply the
   * connection string for your eventHub. The connection string for EventHubs already includes all the
   * necessary information to connect to your EventHub. To learn how to generate the connection string,
   * take a look at this documentation:
   * https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-get-connection-string
   */
  public fun connectionString(connectionString: String) {
    it.property("connectionString", connectionString)
  }

  /**
   * Determines the credential strategy to adopt
   */
  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  /**
   * The generated value for the SharedAccessName.
   */
  public fun sharedAccessKey(sharedAccessKey: String) {
    it.property("sharedAccessKey", sharedAccessKey)
  }

  /**
   * The name you chose for your EventHubs SAS keys.
   */
  public fun sharedAccessName(sharedAccessName: String) {
    it.property("sharedAccessName", sharedAccessName)
  }

  /**
   * Provide custom authentication credentials using an implementation of TokenCredential.
   */
  public fun tokenCredential(tokenCredential: String) {
    it.property("tokenCredential", tokenCredential)
  }
}
