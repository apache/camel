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

public fun UriDsl.`debezium-oracle`(i: DebeziumOracleUriDsl.() -> Unit) {
  DebeziumOracleUriDsl(this).apply(i)
}

@CamelDslMarker
public class DebeziumOracleUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("debezium-oracle")
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

  public fun binaryHandlingMode(binaryHandlingMode: String) {
    it.property("binaryHandlingMode", binaryHandlingMode)
  }

  public fun columnExcludeList(columnExcludeList: String) {
    it.property("columnExcludeList", columnExcludeList)
  }

  public fun columnIncludeList(columnIncludeList: String) {
    it.property("columnIncludeList", columnIncludeList)
  }

  public fun columnPropagateSourceType(columnPropagateSourceType: String) {
    it.property("columnPropagateSourceType", columnPropagateSourceType)
  }

  public fun converters(converters: String) {
    it.property("converters", converters)
  }

  public fun customMetricTags(customMetricTags: String) {
    it.property("customMetricTags", customMetricTags)
  }

  public fun databaseConnectionAdapter(databaseConnectionAdapter: String) {
    it.property("databaseConnectionAdapter", databaseConnectionAdapter)
  }

  public fun databaseDbname(databaseDbname: String) {
    it.property("databaseDbname", databaseDbname)
  }

  public fun databaseHostname(databaseHostname: String) {
    it.property("databaseHostname", databaseHostname)
  }

  public fun databaseOutServerName(databaseOutServerName: String) {
    it.property("databaseOutServerName", databaseOutServerName)
  }

  public fun databasePassword(databasePassword: String) {
    it.property("databasePassword", databasePassword)
  }

  public fun databasePdbName(databasePdbName: String) {
    it.property("databasePdbName", databasePdbName)
  }

  public fun databasePort(databasePort: String) {
    it.property("databasePort", databasePort)
  }

  public fun databasePort(databasePort: Int) {
    it.property("databasePort", databasePort.toString())
  }

  public fun databaseUrl(databaseUrl: String) {
    it.property("databaseUrl", databaseUrl)
  }

  public fun databaseUser(databaseUser: String) {
    it.property("databaseUser", databaseUser)
  }

  public fun datatypePropagateSourceType(datatypePropagateSourceType: String) {
    it.property("datatypePropagateSourceType", datatypePropagateSourceType)
  }

  public fun decimalHandlingMode(decimalHandlingMode: String) {
    it.property("decimalHandlingMode", decimalHandlingMode)
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

  public fun heartbeatActionQuery(heartbeatActionQuery: String) {
    it.property("heartbeatActionQuery", heartbeatActionQuery)
  }

  public fun heartbeatIntervalMs(heartbeatIntervalMs: String) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs)
  }

  public fun heartbeatTopicsPrefix(heartbeatTopicsPrefix: String) {
    it.property("heartbeatTopicsPrefix", heartbeatTopicsPrefix)
  }

  public fun includeSchemaChanges(includeSchemaChanges: String) {
    it.property("includeSchemaChanges", includeSchemaChanges)
  }

  public fun includeSchemaChanges(includeSchemaChanges: Boolean) {
    it.property("includeSchemaChanges", includeSchemaChanges.toString())
  }

  public fun includeSchemaComments(includeSchemaComments: String) {
    it.property("includeSchemaComments", includeSchemaComments)
  }

  public fun includeSchemaComments(includeSchemaComments: Boolean) {
    it.property("includeSchemaComments", includeSchemaComments.toString())
  }

  public
      fun incrementalSnapshotWatermarkingStrategy(incrementalSnapshotWatermarkingStrategy: String) {
    it.property("incrementalSnapshotWatermarkingStrategy", incrementalSnapshotWatermarkingStrategy)
  }

  public fun intervalHandlingMode(intervalHandlingMode: String) {
    it.property("intervalHandlingMode", intervalHandlingMode)
  }

  public fun lobEnabled(lobEnabled: String) {
    it.property("lobEnabled", lobEnabled)
  }

  public fun lobEnabled(lobEnabled: Boolean) {
    it.property("lobEnabled", lobEnabled.toString())
  }

  public fun logMiningArchiveDestinationName(logMiningArchiveDestinationName: String) {
    it.property("logMiningArchiveDestinationName", logMiningArchiveDestinationName)
  }

  public fun logMiningArchiveLogHours(logMiningArchiveLogHours: String) {
    it.property("logMiningArchiveLogHours", logMiningArchiveLogHours)
  }

  public fun logMiningArchiveLogHours(logMiningArchiveLogHours: Int) {
    it.property("logMiningArchiveLogHours", logMiningArchiveLogHours.toString())
  }

  public fun logMiningArchiveLogOnlyMode(logMiningArchiveLogOnlyMode: String) {
    it.property("logMiningArchiveLogOnlyMode", logMiningArchiveLogOnlyMode)
  }

  public fun logMiningArchiveLogOnlyMode(logMiningArchiveLogOnlyMode: Boolean) {
    it.property("logMiningArchiveLogOnlyMode", logMiningArchiveLogOnlyMode.toString())
  }

  public
      fun logMiningArchiveLogOnlyScnPollIntervalMs(logMiningArchiveLogOnlyScnPollIntervalMs: String) {
    it.property("logMiningArchiveLogOnlyScnPollIntervalMs",
        logMiningArchiveLogOnlyScnPollIntervalMs)
  }

  public fun logMiningBatchSizeDefault(logMiningBatchSizeDefault: String) {
    it.property("logMiningBatchSizeDefault", logMiningBatchSizeDefault)
  }

  public fun logMiningBatchSizeDefault(logMiningBatchSizeDefault: Int) {
    it.property("logMiningBatchSizeDefault", logMiningBatchSizeDefault.toString())
  }

  public fun logMiningBatchSizeMax(logMiningBatchSizeMax: String) {
    it.property("logMiningBatchSizeMax", logMiningBatchSizeMax)
  }

  public fun logMiningBatchSizeMax(logMiningBatchSizeMax: Int) {
    it.property("logMiningBatchSizeMax", logMiningBatchSizeMax.toString())
  }

  public fun logMiningBatchSizeMin(logMiningBatchSizeMin: String) {
    it.property("logMiningBatchSizeMin", logMiningBatchSizeMin)
  }

  public fun logMiningBatchSizeMin(logMiningBatchSizeMin: Int) {
    it.property("logMiningBatchSizeMin", logMiningBatchSizeMin.toString())
  }

  public fun logMiningBufferDropOnStop(logMiningBufferDropOnStop: String) {
    it.property("logMiningBufferDropOnStop", logMiningBufferDropOnStop)
  }

  public fun logMiningBufferDropOnStop(logMiningBufferDropOnStop: Boolean) {
    it.property("logMiningBufferDropOnStop", logMiningBufferDropOnStop.toString())
  }

  public fun logMiningBufferInfinispanCacheEvents(logMiningBufferInfinispanCacheEvents: String) {
    it.property("logMiningBufferInfinispanCacheEvents", logMiningBufferInfinispanCacheEvents)
  }

  public fun logMiningBufferInfinispanCacheGlobal(logMiningBufferInfinispanCacheGlobal: String) {
    it.property("logMiningBufferInfinispanCacheGlobal", logMiningBufferInfinispanCacheGlobal)
  }

  public
      fun logMiningBufferInfinispanCacheProcessedTransactions(logMiningBufferInfinispanCacheProcessedTransactions: String) {
    it.property("logMiningBufferInfinispanCacheProcessedTransactions",
        logMiningBufferInfinispanCacheProcessedTransactions)
  }

  public
      fun logMiningBufferInfinispanCacheSchemaChanges(logMiningBufferInfinispanCacheSchemaChanges: String) {
    it.property("logMiningBufferInfinispanCacheSchemaChanges",
        logMiningBufferInfinispanCacheSchemaChanges)
  }

  public
      fun logMiningBufferInfinispanCacheTransactions(logMiningBufferInfinispanCacheTransactions: String) {
    it.property("logMiningBufferInfinispanCacheTransactions",
        logMiningBufferInfinispanCacheTransactions)
  }

  public
      fun logMiningBufferTransactionEventsThreshold(logMiningBufferTransactionEventsThreshold: String) {
    it.property("logMiningBufferTransactionEventsThreshold",
        logMiningBufferTransactionEventsThreshold)
  }

  public
      fun logMiningBufferTransactionEventsThreshold(logMiningBufferTransactionEventsThreshold: Int) {
    it.property("logMiningBufferTransactionEventsThreshold",
        logMiningBufferTransactionEventsThreshold.toString())
  }

  public fun logMiningBufferType(logMiningBufferType: String) {
    it.property("logMiningBufferType", logMiningBufferType)
  }

  public fun logMiningFlushTableName(logMiningFlushTableName: String) {
    it.property("logMiningFlushTableName", logMiningFlushTableName)
  }

  public fun logMiningQueryFilterMode(logMiningQueryFilterMode: String) {
    it.property("logMiningQueryFilterMode", logMiningQueryFilterMode)
  }

  public fun logMiningRestartConnection(logMiningRestartConnection: String) {
    it.property("logMiningRestartConnection", logMiningRestartConnection)
  }

  public fun logMiningRestartConnection(logMiningRestartConnection: Boolean) {
    it.property("logMiningRestartConnection", logMiningRestartConnection.toString())
  }

  public fun logMiningScnGapDetectionGapSizeMin(logMiningScnGapDetectionGapSizeMin: String) {
    it.property("logMiningScnGapDetectionGapSizeMin", logMiningScnGapDetectionGapSizeMin)
  }

  public fun logMiningScnGapDetectionGapSizeMin(logMiningScnGapDetectionGapSizeMin: Int) {
    it.property("logMiningScnGapDetectionGapSizeMin", logMiningScnGapDetectionGapSizeMin.toString())
  }

  public
      fun logMiningScnGapDetectionTimeIntervalMaxMs(logMiningScnGapDetectionTimeIntervalMaxMs: String) {
    it.property("logMiningScnGapDetectionTimeIntervalMaxMs",
        logMiningScnGapDetectionTimeIntervalMaxMs)
  }

  public fun logMiningSessionMaxMs(logMiningSessionMaxMs: String) {
    it.property("logMiningSessionMaxMs", logMiningSessionMaxMs)
  }

  public fun logMiningSleepTimeDefaultMs(logMiningSleepTimeDefaultMs: String) {
    it.property("logMiningSleepTimeDefaultMs", logMiningSleepTimeDefaultMs)
  }

  public fun logMiningSleepTimeIncrementMs(logMiningSleepTimeIncrementMs: String) {
    it.property("logMiningSleepTimeIncrementMs", logMiningSleepTimeIncrementMs)
  }

  public fun logMiningSleepTimeMaxMs(logMiningSleepTimeMaxMs: String) {
    it.property("logMiningSleepTimeMaxMs", logMiningSleepTimeMaxMs)
  }

  public fun logMiningSleepTimeMinMs(logMiningSleepTimeMinMs: String) {
    it.property("logMiningSleepTimeMinMs", logMiningSleepTimeMinMs)
  }

  public fun logMiningStrategy(logMiningStrategy: String) {
    it.property("logMiningStrategy", logMiningStrategy)
  }

  public fun logMiningTransactionRetentionMs(logMiningTransactionRetentionMs: String) {
    it.property("logMiningTransactionRetentionMs", logMiningTransactionRetentionMs)
  }

  public fun logMiningUsernameExcludeList(logMiningUsernameExcludeList: String) {
    it.property("logMiningUsernameExcludeList", logMiningUsernameExcludeList)
  }

  public fun logMiningUsernameIncludeList(logMiningUsernameIncludeList: String) {
    it.property("logMiningUsernameIncludeList", logMiningUsernameIncludeList)
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

  public fun messageKeyColumns(messageKeyColumns: String) {
    it.property("messageKeyColumns", messageKeyColumns)
  }

  public fun notificationEnabledChannels(notificationEnabledChannels: String) {
    it.property("notificationEnabledChannels", notificationEnabledChannels)
  }

  public fun notificationSinkTopicName(notificationSinkTopicName: String) {
    it.property("notificationSinkTopicName", notificationSinkTopicName)
  }

  public fun openlogreplicatorHost(openlogreplicatorHost: String) {
    it.property("openlogreplicatorHost", openlogreplicatorHost)
  }

  public fun openlogreplicatorPort(openlogreplicatorPort: String) {
    it.property("openlogreplicatorPort", openlogreplicatorPort)
  }

  public fun openlogreplicatorPort(openlogreplicatorPort: Int) {
    it.property("openlogreplicatorPort", openlogreplicatorPort.toString())
  }

  public fun openlogreplicatorSource(openlogreplicatorSource: String) {
    it.property("openlogreplicatorSource", openlogreplicatorSource)
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

  public fun racNodes(racNodes: String) {
    it.property("racNodes", racNodes)
  }

  public fun retriableRestartConnectorWaitMs(retriableRestartConnectorWaitMs: String) {
    it.property("retriableRestartConnectorWaitMs", retriableRestartConnectorWaitMs)
  }

  public fun schemaHistoryInternal(schemaHistoryInternal: String) {
    it.property("schemaHistoryInternal", schemaHistoryInternal)
  }

  public fun schemaHistoryInternalFileFilename(schemaHistoryInternalFileFilename: String) {
    it.property("schemaHistoryInternalFileFilename", schemaHistoryInternalFileFilename)
  }

  public
      fun schemaHistoryInternalSkipUnparseableDdl(schemaHistoryInternalSkipUnparseableDdl: String) {
    it.property("schemaHistoryInternalSkipUnparseableDdl", schemaHistoryInternalSkipUnparseableDdl)
  }

  public
      fun schemaHistoryInternalSkipUnparseableDdl(schemaHistoryInternalSkipUnparseableDdl: Boolean) {
    it.property("schemaHistoryInternalSkipUnparseableDdl",
        schemaHistoryInternalSkipUnparseableDdl.toString())
  }

  public
      fun schemaHistoryInternalStoreOnlyCapturedDatabasesDdl(schemaHistoryInternalStoreOnlyCapturedDatabasesDdl: String) {
    it.property("schemaHistoryInternalStoreOnlyCapturedDatabasesDdl",
        schemaHistoryInternalStoreOnlyCapturedDatabasesDdl)
  }

  public
      fun schemaHistoryInternalStoreOnlyCapturedDatabasesDdl(schemaHistoryInternalStoreOnlyCapturedDatabasesDdl: Boolean) {
    it.property("schemaHistoryInternalStoreOnlyCapturedDatabasesDdl",
        schemaHistoryInternalStoreOnlyCapturedDatabasesDdl.toString())
  }

  public
      fun schemaHistoryInternalStoreOnlyCapturedTablesDdl(schemaHistoryInternalStoreOnlyCapturedTablesDdl: String) {
    it.property("schemaHistoryInternalStoreOnlyCapturedTablesDdl",
        schemaHistoryInternalStoreOnlyCapturedTablesDdl)
  }

  public
      fun schemaHistoryInternalStoreOnlyCapturedTablesDdl(schemaHistoryInternalStoreOnlyCapturedTablesDdl: Boolean) {
    it.property("schemaHistoryInternalStoreOnlyCapturedTablesDdl",
        schemaHistoryInternalStoreOnlyCapturedTablesDdl.toString())
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

  public fun snapshotDelayMs(snapshotDelayMs: String) {
    it.property("snapshotDelayMs", snapshotDelayMs)
  }

  public fun snapshotEnhancePredicateScn(snapshotEnhancePredicateScn: String) {
    it.property("snapshotEnhancePredicateScn", snapshotEnhancePredicateScn)
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

  public fun snapshotLockingMode(snapshotLockingMode: String) {
    it.property("snapshotLockingMode", snapshotLockingMode)
  }

  public fun snapshotLockTimeoutMs(snapshotLockTimeoutMs: String) {
    it.property("snapshotLockTimeoutMs", snapshotLockTimeoutMs)
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

  public fun snapshotSelectStatementOverrides(snapshotSelectStatementOverrides: String) {
    it.property("snapshotSelectStatementOverrides", snapshotSelectStatementOverrides)
  }

  public fun snapshotTablesOrderByRowCount(snapshotTablesOrderByRowCount: String) {
    it.property("snapshotTablesOrderByRowCount", snapshotTablesOrderByRowCount)
  }

  public fun sourceinfoStructMaker(sourceinfoStructMaker: String) {
    it.property("sourceinfoStructMaker", sourceinfoStructMaker)
  }

  public fun tableExcludeList(tableExcludeList: String) {
    it.property("tableExcludeList", tableExcludeList)
  }

  public fun tableIncludeList(tableIncludeList: String) {
    it.property("tableIncludeList", tableIncludeList)
  }

  public fun timePrecisionMode(timePrecisionMode: String) {
    it.property("timePrecisionMode", timePrecisionMode)
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

  public fun unavailableValuePlaceholder(unavailableValuePlaceholder: String) {
    it.property("unavailableValuePlaceholder", unavailableValuePlaceholder)
  }
}
