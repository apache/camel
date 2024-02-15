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

public fun UriDsl.`debezium-mysql`(i: DebeziumMysqlUriDsl.() -> Unit) {
  DebeziumMysqlUriDsl(this).apply(i)
}

@CamelDslMarker
public class DebeziumMysqlUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("debezium-mysql")
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

  public fun bigintUnsignedHandlingMode(bigintUnsignedHandlingMode: String) {
    it.property("bigintUnsignedHandlingMode", bigintUnsignedHandlingMode)
  }

  public fun binaryHandlingMode(binaryHandlingMode: String) {
    it.property("binaryHandlingMode", binaryHandlingMode)
  }

  public fun binlogBufferSize(binlogBufferSize: String) {
    it.property("binlogBufferSize", binlogBufferSize)
  }

  public fun binlogBufferSize(binlogBufferSize: Int) {
    it.property("binlogBufferSize", binlogBufferSize.toString())
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

  public fun connectKeepAlive(connectKeepAlive: String) {
    it.property("connectKeepAlive", connectKeepAlive)
  }

  public fun connectKeepAlive(connectKeepAlive: Boolean) {
    it.property("connectKeepAlive", connectKeepAlive.toString())
  }

  public fun connectKeepAliveIntervalMs(connectKeepAliveIntervalMs: String) {
    it.property("connectKeepAliveIntervalMs", connectKeepAliveIntervalMs)
  }

  public fun connectorAdapter(connectorAdapter: String) {
    it.property("connectorAdapter", connectorAdapter)
  }

  public fun connectTimeoutMs(connectTimeoutMs: String) {
    it.property("connectTimeoutMs", connectTimeoutMs)
  }

  public fun converters(converters: String) {
    it.property("converters", converters)
  }

  public fun customMetricTags(customMetricTags: String) {
    it.property("customMetricTags", customMetricTags)
  }

  public fun databaseExcludeList(databaseExcludeList: String) {
    it.property("databaseExcludeList", databaseExcludeList)
  }

  public fun databaseHostname(databaseHostname: String) {
    it.property("databaseHostname", databaseHostname)
  }

  public fun databaseIncludeList(databaseIncludeList: String) {
    it.property("databaseIncludeList", databaseIncludeList)
  }

  public fun databaseInitialStatements(databaseInitialStatements: String) {
    it.property("databaseInitialStatements", databaseInitialStatements)
  }

  public fun databaseJdbcDriver(databaseJdbcDriver: String) {
    it.property("databaseJdbcDriver", databaseJdbcDriver)
  }

  public fun databasePassword(databasePassword: String) {
    it.property("databasePassword", databasePassword)
  }

  public fun databasePort(databasePort: String) {
    it.property("databasePort", databasePort)
  }

  public fun databasePort(databasePort: Int) {
    it.property("databasePort", databasePort.toString())
  }

  public fun databaseServerId(databaseServerId: String) {
    it.property("databaseServerId", databaseServerId)
  }

  public fun databaseServerId(databaseServerId: Int) {
    it.property("databaseServerId", databaseServerId.toString())
  }

  public fun databaseServerIdOffset(databaseServerIdOffset: String) {
    it.property("databaseServerIdOffset", databaseServerIdOffset)
  }

  public fun databaseServerIdOffset(databaseServerIdOffset: Int) {
    it.property("databaseServerIdOffset", databaseServerIdOffset.toString())
  }

  public fun databaseSslKeystore(databaseSslKeystore: String) {
    it.property("databaseSslKeystore", databaseSslKeystore)
  }

  public fun databaseSslKeystorePassword(databaseSslKeystorePassword: String) {
    it.property("databaseSslKeystorePassword", databaseSslKeystorePassword)
  }

  public fun databaseSslMode(databaseSslMode: String) {
    it.property("databaseSslMode", databaseSslMode)
  }

  public fun databaseSslTruststore(databaseSslTruststore: String) {
    it.property("databaseSslTruststore", databaseSslTruststore)
  }

  public fun databaseSslTruststorePassword(databaseSslTruststorePassword: String) {
    it.property("databaseSslTruststorePassword", databaseSslTruststorePassword)
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

  public fun enableTimeAdjuster(enableTimeAdjuster: String) {
    it.property("enableTimeAdjuster", enableTimeAdjuster)
  }

  public fun enableTimeAdjuster(enableTimeAdjuster: Boolean) {
    it.property("enableTimeAdjuster", enableTimeAdjuster.toString())
  }

  public fun errorsMaxRetries(errorsMaxRetries: String) {
    it.property("errorsMaxRetries", errorsMaxRetries)
  }

  public fun errorsMaxRetries(errorsMaxRetries: Int) {
    it.property("errorsMaxRetries", errorsMaxRetries.toString())
  }

  public
      fun eventDeserializationFailureHandlingMode(eventDeserializationFailureHandlingMode: String) {
    it.property("eventDeserializationFailureHandlingMode", eventDeserializationFailureHandlingMode)
  }

  public fun eventProcessingFailureHandlingMode(eventProcessingFailureHandlingMode: String) {
    it.property("eventProcessingFailureHandlingMode", eventProcessingFailureHandlingMode)
  }

  public fun gtidSourceExcludes(gtidSourceExcludes: String) {
    it.property("gtidSourceExcludes", gtidSourceExcludes)
  }

  public fun gtidSourceFilterDmlEvents(gtidSourceFilterDmlEvents: String) {
    it.property("gtidSourceFilterDmlEvents", gtidSourceFilterDmlEvents)
  }

  public fun gtidSourceFilterDmlEvents(gtidSourceFilterDmlEvents: Boolean) {
    it.property("gtidSourceFilterDmlEvents", gtidSourceFilterDmlEvents.toString())
  }

  public fun gtidSourceIncludes(gtidSourceIncludes: String) {
    it.property("gtidSourceIncludes", gtidSourceIncludes)
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

  public fun includeQuery(includeQuery: String) {
    it.property("includeQuery", includeQuery)
  }

  public fun includeQuery(includeQuery: Boolean) {
    it.property("includeQuery", includeQuery.toString())
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

  public fun inconsistentSchemaHandlingMode(inconsistentSchemaHandlingMode: String) {
    it.property("inconsistentSchemaHandlingMode", inconsistentSchemaHandlingMode)
  }

  public fun incrementalSnapshotAllowSchemaChanges(incrementalSnapshotAllowSchemaChanges: String) {
    it.property("incrementalSnapshotAllowSchemaChanges", incrementalSnapshotAllowSchemaChanges)
  }

  public fun incrementalSnapshotAllowSchemaChanges(incrementalSnapshotAllowSchemaChanges: Boolean) {
    it.property("incrementalSnapshotAllowSchemaChanges",
        incrementalSnapshotAllowSchemaChanges.toString())
  }

  public fun incrementalSnapshotChunkSize(incrementalSnapshotChunkSize: String) {
    it.property("incrementalSnapshotChunkSize", incrementalSnapshotChunkSize)
  }

  public fun incrementalSnapshotChunkSize(incrementalSnapshotChunkSize: Int) {
    it.property("incrementalSnapshotChunkSize", incrementalSnapshotChunkSize.toString())
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

  public fun messageKeyColumns(messageKeyColumns: String) {
    it.property("messageKeyColumns", messageKeyColumns)
  }

  public fun minRowCountToStreamResults(minRowCountToStreamResults: String) {
    it.property("minRowCountToStreamResults", minRowCountToStreamResults)
  }

  public fun minRowCountToStreamResults(minRowCountToStreamResults: Int) {
    it.property("minRowCountToStreamResults", minRowCountToStreamResults.toString())
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

  public fun snapshotNewTables(snapshotNewTables: String) {
    it.property("snapshotNewTables", snapshotNewTables)
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

  public fun tableIgnoreBuiltin(tableIgnoreBuiltin: String) {
    it.property("tableIgnoreBuiltin", tableIgnoreBuiltin)
  }

  public fun tableIgnoreBuiltin(tableIgnoreBuiltin: Boolean) {
    it.property("tableIgnoreBuiltin", tableIgnoreBuiltin.toString())
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
}
