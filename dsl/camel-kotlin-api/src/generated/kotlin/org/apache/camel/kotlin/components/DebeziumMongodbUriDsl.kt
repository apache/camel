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

public fun UriDsl.`debezium-mongodb`(i: DebeziumMongodbUriDsl.() -> Unit) {
  DebeziumMongodbUriDsl(this).apply(i)
}

@CamelDslMarker
public class DebeziumMongodbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("debezium-mongodb")
  }

  private var name: String = ""

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun additionalProperties(additionalProperties: String) {
    it.property("additionalProperties", additionalProperties)
  }

  public fun internalKeyConverter(internalKeyConverter: String) {
    it.property("internalKeyConverter", internalKeyConverter)
  }

  public fun internalValueConverter(internalValueConverter: String) {
    it.property("internalValueConverter", internalValueConverter)
  }

  public fun offsetCommitPolicy(offsetCommitPolicy: String) {
    it.property("offsetCommitPolicy", offsetCommitPolicy)
  }

  public fun offsetCommitTimeoutMs(offsetCommitTimeoutMs: String) {
    it.property("offsetCommitTimeoutMs", offsetCommitTimeoutMs)
  }

  public fun offsetFlushIntervalMs(offsetFlushIntervalMs: String) {
    it.property("offsetFlushIntervalMs", offsetFlushIntervalMs)
  }

  public fun offsetStorage(offsetStorage: String) {
    it.property("offsetStorage", offsetStorage)
  }

  public fun offsetStorageFileName(offsetStorageFileName: String) {
    it.property("offsetStorageFileName", offsetStorageFileName)
  }

  public fun offsetStoragePartitions(offsetStoragePartitions: String) {
    it.property("offsetStoragePartitions", offsetStoragePartitions)
  }

  public fun offsetStoragePartitions(offsetStoragePartitions: Int) {
    it.property("offsetStoragePartitions", offsetStoragePartitions.toString())
  }

  public fun offsetStorageReplicationFactor(offsetStorageReplicationFactor: String) {
    it.property("offsetStorageReplicationFactor", offsetStorageReplicationFactor)
  }

  public fun offsetStorageReplicationFactor(offsetStorageReplicationFactor: Int) {
    it.property("offsetStorageReplicationFactor", offsetStorageReplicationFactor.toString())
  }

  public fun offsetStorageTopic(offsetStorageTopic: String) {
    it.property("offsetStorageTopic", offsetStorageTopic)
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

  public fun captureMode(captureMode: String) {
    it.property("captureMode", captureMode)
  }

  public fun collectionExcludeList(collectionExcludeList: String) {
    it.property("collectionExcludeList", collectionExcludeList)
  }

  public fun collectionIncludeList(collectionIncludeList: String) {
    it.property("collectionIncludeList", collectionIncludeList)
  }

  public fun converters(converters: String) {
    it.property("converters", converters)
  }

  public fun cursorMaxAwaitTimeMs(cursorMaxAwaitTimeMs: String) {
    it.property("cursorMaxAwaitTimeMs", cursorMaxAwaitTimeMs)
  }

  public fun customMetricTags(customMetricTags: String) {
    it.property("customMetricTags", customMetricTags)
  }

  public fun databaseExcludeList(databaseExcludeList: String) {
    it.property("databaseExcludeList", databaseExcludeList)
  }

  public fun databaseIncludeList(databaseIncludeList: String) {
    it.property("databaseIncludeList", databaseIncludeList)
  }

  public fun errorsMaxRetries(errorsMaxRetries: String) {
    it.property("errorsMaxRetries", errorsMaxRetries)
  }

  public fun errorsMaxRetries(errorsMaxRetries: Int) {
    it.property("errorsMaxRetries", errorsMaxRetries.toString())
  }

  public fun eventProcessingFailureHandlingMode(eventProcessingFailureHandlingMode: String) {
    it.property("eventProcessingFailureHandlingMode", eventProcessingFailureHandlingMode)
  }

  public fun fieldExcludeList(fieldExcludeList: String) {
    it.property("fieldExcludeList", fieldExcludeList)
  }

  public fun fieldRenames(fieldRenames: String) {
    it.property("fieldRenames", fieldRenames)
  }

  public fun heartbeatIntervalMs(heartbeatIntervalMs: String) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs)
  }

  public fun heartbeatTopicsPrefix(heartbeatTopicsPrefix: String) {
    it.property("heartbeatTopicsPrefix", heartbeatTopicsPrefix)
  }

  public
      fun incrementalSnapshotWatermarkingStrategy(incrementalSnapshotWatermarkingStrategy: String) {
    it.property("incrementalSnapshotWatermarkingStrategy", incrementalSnapshotWatermarkingStrategy)
  }

  public fun maxBatchSize(maxBatchSize: String) {
    it.property("maxBatchSize", maxBatchSize)
  }

  public fun maxBatchSize(maxBatchSize: Int) {
    it.property("maxBatchSize", maxBatchSize.toString())
  }

  public fun maxQueueSize(maxQueueSize: String) {
    it.property("maxQueueSize", maxQueueSize)
  }

  public fun maxQueueSize(maxQueueSize: Int) {
    it.property("maxQueueSize", maxQueueSize.toString())
  }

  public fun maxQueueSizeInBytes(maxQueueSizeInBytes: String) {
    it.property("maxQueueSizeInBytes", maxQueueSizeInBytes)
  }

  public fun maxQueueSizeInBytes(maxQueueSizeInBytes: Int) {
    it.property("maxQueueSizeInBytes", maxQueueSizeInBytes.toString())
  }

  public fun mongodbAuthsource(mongodbAuthsource: String) {
    it.property("mongodbAuthsource", mongodbAuthsource)
  }

  public fun mongodbConnectionMode(mongodbConnectionMode: String) {
    it.property("mongodbConnectionMode", mongodbConnectionMode)
  }

  public fun mongodbConnectionString(mongodbConnectionString: String) {
    it.property("mongodbConnectionString", mongodbConnectionString)
  }

  public fun mongodbConnectTimeoutMs(mongodbConnectTimeoutMs: String) {
    it.property("mongodbConnectTimeoutMs", mongodbConnectTimeoutMs)
  }

  public fun mongodbHeartbeatFrequencyMs(mongodbHeartbeatFrequencyMs: String) {
    it.property("mongodbHeartbeatFrequencyMs", mongodbHeartbeatFrequencyMs)
  }

  public fun mongodbPassword(mongodbPassword: String) {
    it.property("mongodbPassword", mongodbPassword)
  }

  public fun mongodbPollIntervalMs(mongodbPollIntervalMs: String) {
    it.property("mongodbPollIntervalMs", mongodbPollIntervalMs)
  }

  public fun mongodbServerSelectionTimeoutMs(mongodbServerSelectionTimeoutMs: String) {
    it.property("mongodbServerSelectionTimeoutMs", mongodbServerSelectionTimeoutMs)
  }

  public fun mongodbSocketTimeoutMs(mongodbSocketTimeoutMs: String) {
    it.property("mongodbSocketTimeoutMs", mongodbSocketTimeoutMs)
  }

  public fun mongodbSslEnabled(mongodbSslEnabled: String) {
    it.property("mongodbSslEnabled", mongodbSslEnabled)
  }

  public fun mongodbSslEnabled(mongodbSslEnabled: Boolean) {
    it.property("mongodbSslEnabled", mongodbSslEnabled.toString())
  }

  public fun mongodbSslInvalidHostnameAllowed(mongodbSslInvalidHostnameAllowed: String) {
    it.property("mongodbSslInvalidHostnameAllowed", mongodbSslInvalidHostnameAllowed)
  }

  public fun mongodbSslInvalidHostnameAllowed(mongodbSslInvalidHostnameAllowed: Boolean) {
    it.property("mongodbSslInvalidHostnameAllowed", mongodbSslInvalidHostnameAllowed.toString())
  }

  public fun mongodbUser(mongodbUser: String) {
    it.property("mongodbUser", mongodbUser)
  }

  public fun notificationEnabledChannels(notificationEnabledChannels: String) {
    it.property("notificationEnabledChannels", notificationEnabledChannels)
  }

  public fun notificationSinkTopicName(notificationSinkTopicName: String) {
    it.property("notificationSinkTopicName", notificationSinkTopicName)
  }

  public fun pollIntervalMs(pollIntervalMs: String) {
    it.property("pollIntervalMs", pollIntervalMs)
  }

  public fun postProcessors(postProcessors: String) {
    it.property("postProcessors", postProcessors)
  }

  public fun provideTransactionMetadata(provideTransactionMetadata: String) {
    it.property("provideTransactionMetadata", provideTransactionMetadata)
  }

  public fun provideTransactionMetadata(provideTransactionMetadata: Boolean) {
    it.property("provideTransactionMetadata", provideTransactionMetadata.toString())
  }

  public fun queryFetchSize(queryFetchSize: String) {
    it.property("queryFetchSize", queryFetchSize)
  }

  public fun queryFetchSize(queryFetchSize: Int) {
    it.property("queryFetchSize", queryFetchSize.toString())
  }

  public fun retriableRestartConnectorWaitMs(retriableRestartConnectorWaitMs: String) {
    it.property("retriableRestartConnectorWaitMs", retriableRestartConnectorWaitMs)
  }

  public fun schemaHistoryInternalFileFilename(schemaHistoryInternalFileFilename: String) {
    it.property("schemaHistoryInternalFileFilename", schemaHistoryInternalFileFilename)
  }

  public fun schemaNameAdjustmentMode(schemaNameAdjustmentMode: String) {
    it.property("schemaNameAdjustmentMode", schemaNameAdjustmentMode)
  }

  public fun signalDataCollection(signalDataCollection: String) {
    it.property("signalDataCollection", signalDataCollection)
  }

  public fun signalEnabledChannels(signalEnabledChannels: String) {
    it.property("signalEnabledChannels", signalEnabledChannels)
  }

  public fun signalPollIntervalMs(signalPollIntervalMs: String) {
    it.property("signalPollIntervalMs", signalPollIntervalMs)
  }

  public fun skippedOperations(skippedOperations: String) {
    it.property("skippedOperations", skippedOperations)
  }

  public fun snapshotCollectionFilterOverrides(snapshotCollectionFilterOverrides: String) {
    it.property("snapshotCollectionFilterOverrides", snapshotCollectionFilterOverrides)
  }

  public fun snapshotDelayMs(snapshotDelayMs: String) {
    it.property("snapshotDelayMs", snapshotDelayMs)
  }

  public fun snapshotFetchSize(snapshotFetchSize: String) {
    it.property("snapshotFetchSize", snapshotFetchSize)
  }

  public fun snapshotFetchSize(snapshotFetchSize: Int) {
    it.property("snapshotFetchSize", snapshotFetchSize.toString())
  }

  public fun snapshotIncludeCollectionList(snapshotIncludeCollectionList: String) {
    it.property("snapshotIncludeCollectionList", snapshotIncludeCollectionList)
  }

  public fun snapshotMaxThreads(snapshotMaxThreads: String) {
    it.property("snapshotMaxThreads", snapshotMaxThreads)
  }

  public fun snapshotMaxThreads(snapshotMaxThreads: Int) {
    it.property("snapshotMaxThreads", snapshotMaxThreads.toString())
  }

  public fun snapshotMode(snapshotMode: String) {
    it.property("snapshotMode", snapshotMode)
  }

  public fun sourceinfoStructMaker(sourceinfoStructMaker: String) {
    it.property("sourceinfoStructMaker", sourceinfoStructMaker)
  }

  public fun tombstonesOnDelete(tombstonesOnDelete: String) {
    it.property("tombstonesOnDelete", tombstonesOnDelete)
  }

  public fun tombstonesOnDelete(tombstonesOnDelete: Boolean) {
    it.property("tombstonesOnDelete", tombstonesOnDelete.toString())
  }

  public fun topicNamingStrategy(topicNamingStrategy: String) {
    it.property("topicNamingStrategy", topicNamingStrategy)
  }

  public fun topicPrefix(topicPrefix: String) {
    it.property("topicPrefix", topicPrefix)
  }
}
