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
 * Capture changes from a MongoDB database.
 */
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

  /**
   * Unique name for the connector. Attempting to register again with the same name will fail.
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * Additional properties for debezium components in case they can't be set directly on the camel
   * configurations (e.g: setting Kafka Connect properties needed by Debezium engine, for example
   * setting KafkaOffsetBackingStore), the properties have to be prefixed with additionalProperties..
   * E.g:
   * additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro
   */
  public fun additionalProperties(additionalProperties: String) {
    it.property("additionalProperties", additionalProperties)
  }

  /**
   * The Converter class that should be used to serialize and deserialize key data for offsets. The
   * default is JSON converter.
   */
  public fun internalKeyConverter(internalKeyConverter: String) {
    it.property("internalKeyConverter", internalKeyConverter)
  }

  /**
   * The Converter class that should be used to serialize and deserialize value data for offsets.
   * The default is JSON converter.
   */
  public fun internalValueConverter(internalValueConverter: String) {
    it.property("internalValueConverter", internalValueConverter)
  }

  /**
   * The name of the Java class of the commit policy. It defines when offsets commit has to be
   * triggered based on the number of events processed and the time elapsed since the last commit. This
   * class must implement the interface 'OffsetCommitPolicy'. The default is a periodic commit policy
   * based upon time intervals.
   */
  public fun offsetCommitPolicy(offsetCommitPolicy: String) {
    it.property("offsetCommitPolicy", offsetCommitPolicy)
  }

  /**
   * Maximum number of milliseconds to wait for records to flush and partition offset data to be
   * committed to offset storage before cancelling the process and restoring the offset data to be
   * committed in a future attempt. The default is 5 seconds.
   */
  public fun offsetCommitTimeoutMs(offsetCommitTimeoutMs: String) {
    it.property("offsetCommitTimeoutMs", offsetCommitTimeoutMs)
  }

  /**
   * Interval at which to try committing offsets. The default is 1 minute.
   */
  public fun offsetFlushIntervalMs(offsetFlushIntervalMs: String) {
    it.property("offsetFlushIntervalMs", offsetFlushIntervalMs)
  }

  /**
   * The name of the Java class that is responsible for persistence of connector offsets.
   */
  public fun offsetStorage(offsetStorage: String) {
    it.property("offsetStorage", offsetStorage)
  }

  /**
   * Path to file where offsets are to be stored. Required when offset.storage is set to the
   * FileOffsetBackingStore.
   */
  public fun offsetStorageFileName(offsetStorageFileName: String) {
    it.property("offsetStorageFileName", offsetStorageFileName)
  }

  /**
   * The number of partitions used when creating the offset storage topic. Required when
   * offset.storage is set to the 'KafkaOffsetBackingStore'.
   */
  public fun offsetStoragePartitions(offsetStoragePartitions: String) {
    it.property("offsetStoragePartitions", offsetStoragePartitions)
  }

  /**
   * The number of partitions used when creating the offset storage topic. Required when
   * offset.storage is set to the 'KafkaOffsetBackingStore'.
   */
  public fun offsetStoragePartitions(offsetStoragePartitions: Int) {
    it.property("offsetStoragePartitions", offsetStoragePartitions.toString())
  }

  /**
   * Replication factor used when creating the offset storage topic. Required when offset.storage is
   * set to the KafkaOffsetBackingStore
   */
  public fun offsetStorageReplicationFactor(offsetStorageReplicationFactor: String) {
    it.property("offsetStorageReplicationFactor", offsetStorageReplicationFactor)
  }

  /**
   * Replication factor used when creating the offset storage topic. Required when offset.storage is
   * set to the KafkaOffsetBackingStore
   */
  public fun offsetStorageReplicationFactor(offsetStorageReplicationFactor: Int) {
    it.property("offsetStorageReplicationFactor", offsetStorageReplicationFactor.toString())
  }

  /**
   * The name of the Kafka topic where offsets are to be stored. Required when offset.storage is set
   * to the KafkaOffsetBackingStore.
   */
  public fun offsetStorageTopic(offsetStorageTopic: String) {
    it.property("offsetStorageTopic", offsetStorageTopic)
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
   * The method used to capture changes from MongoDB server. Options include: 'change_streams' to
   * capture changes via MongoDB Change Streams, update events do not contain full documents;
   * 'change_streams_update_full' (the default) to capture changes via MongoDB Change Streams, update
   * events contain full documents
   */
  public fun captureMode(captureMode: String) {
    it.property("captureMode", captureMode)
  }

  /**
   * A comma-separated list of regular expressions or literals that match the collection names for
   * which changes are to be excluded
   */
  public fun collectionExcludeList(collectionExcludeList: String) {
    it.property("collectionExcludeList", collectionExcludeList)
  }

  /**
   * A comma-separated list of regular expressions or literals that match the collection names for
   * which changes are to be captured
   */
  public fun collectionIncludeList(collectionIncludeList: String) {
    it.property("collectionIncludeList", collectionIncludeList)
  }

  /**
   * Optional list of custom converters that would be used instead of default ones. The converters
   * are defined using '.type' config option and configured using options '.'
   */
  public fun converters(converters: String) {
    it.property("converters", converters)
  }

  /**
   * The maximum processing time in milliseconds to wait for the oplog cursor to process a single
   * poll request
   */
  public fun cursorMaxAwaitTimeMs(cursorMaxAwaitTimeMs: String) {
    it.property("cursorMaxAwaitTimeMs", cursorMaxAwaitTimeMs)
  }

  /**
   * The custom metric tags will accept key-value pairs to customize the MBean object name which
   * should be appended the end of regular name, each key would represent a tag for the MBean object
   * name, and the corresponding value would be the value of that tag the key is. For example:
   * k1=v1,k2=v2
   */
  public fun customMetricTags(customMetricTags: String) {
    it.property("customMetricTags", customMetricTags)
  }

  /**
   * A comma-separated list of regular expressions or literals that match the database names for
   * which changes are to be excluded
   */
  public fun databaseExcludeList(databaseExcludeList: String) {
    it.property("databaseExcludeList", databaseExcludeList)
  }

  /**
   * A comma-separated list of regular expressions or literals that match the database names for
   * which changes are to be captured
   */
  public fun databaseIncludeList(databaseIncludeList: String) {
    it.property("databaseIncludeList", databaseIncludeList)
  }

  /**
   * The maximum number of retries on connection errors before failing (-1 = no limit, 0 = disabled,
   * 0 = num of retries).
   */
  public fun errorsMaxRetries(errorsMaxRetries: String) {
    it.property("errorsMaxRetries", errorsMaxRetries)
  }

  /**
   * The maximum number of retries on connection errors before failing (-1 = no limit, 0 = disabled,
   * 0 = num of retries).
   */
  public fun errorsMaxRetries(errorsMaxRetries: Int) {
    it.property("errorsMaxRetries", errorsMaxRetries.toString())
  }

  /**
   * Specify how failures during processing of events (i.e. when encountering a corrupted event)
   * should be handled, including: 'fail' (the default) an exception indicating the problematic event
   * and its position is raised, causing the connector to be stopped; 'warn' the problematic event and
   * its position will be logged and the event will be skipped; 'ignore' the problematic event will be
   * skipped.
   */
  public fun eventProcessingFailureHandlingMode(eventProcessingFailureHandlingMode: String) {
    it.property("eventProcessingFailureHandlingMode", eventProcessingFailureHandlingMode)
  }

  /**
   * A comma-separated list of the fully-qualified names of fields that should be excluded from
   * change event message values
   */
  public fun fieldExcludeList(fieldExcludeList: String) {
    it.property("fieldExcludeList", fieldExcludeList)
  }

  /**
   * A comma-separated list of the fully-qualified replacements of fields that should be used to
   * rename fields in change event message values. Fully-qualified replacements for fields are of the
   * form databaseName.collectionName.fieldName.nestedFieldName:newNestedFieldName, where databaseName
   * and collectionName may contain the wildcard () which matches any characters, the colon character
   * (:) is used to determine rename mapping of field.
   */
  public fun fieldRenames(fieldRenames: String) {
    it.property("fieldRenames", fieldRenames)
  }

  /**
   * Length of an interval in milli-seconds in in which the connector periodically sends heartbeat
   * messages to a heartbeat topic. Use 0 to disable heartbeat messages. Disabled by default.
   */
  public fun heartbeatIntervalMs(heartbeatIntervalMs: String) {
    it.property("heartbeatIntervalMs", heartbeatIntervalMs)
  }

  /**
   * The prefix that is used to name heartbeat topics.Defaults to __debezium-heartbeat.
   */
  public fun heartbeatTopicsPrefix(heartbeatTopicsPrefix: String) {
    it.property("heartbeatTopicsPrefix", heartbeatTopicsPrefix)
  }

  /**
   * Specify the strategy used for watermarking during an incremental snapshot: 'insert_insert' both
   * open and close signal is written into signal data collection (default); 'insert_delete' only open
   * signal is written on signal data collection, the close will delete the relative open signal;
   */
  public
      fun incrementalSnapshotWatermarkingStrategy(incrementalSnapshotWatermarkingStrategy: String) {
    it.property("incrementalSnapshotWatermarkingStrategy", incrementalSnapshotWatermarkingStrategy)
  }

  /**
   * Maximum size of each batch of source records. Defaults to 2048.
   */
  public fun maxBatchSize(maxBatchSize: String) {
    it.property("maxBatchSize", maxBatchSize)
  }

  /**
   * Maximum size of each batch of source records. Defaults to 2048.
   */
  public fun maxBatchSize(maxBatchSize: Int) {
    it.property("maxBatchSize", maxBatchSize.toString())
  }

  /**
   * Maximum size of the queue for change events read from the database log but not yet recorded or
   * forwarded. Defaults to 8192, and should always be larger than the maximum batch size.
   */
  public fun maxQueueSize(maxQueueSize: String) {
    it.property("maxQueueSize", maxQueueSize)
  }

  /**
   * Maximum size of the queue for change events read from the database log but not yet recorded or
   * forwarded. Defaults to 8192, and should always be larger than the maximum batch size.
   */
  public fun maxQueueSize(maxQueueSize: Int) {
    it.property("maxQueueSize", maxQueueSize.toString())
  }

  /**
   * Maximum size of the queue in bytes for change events read from the database log but not yet
   * recorded or forwarded. Defaults to 0. Mean the feature is not enabled
   */
  public fun maxQueueSizeInBytes(maxQueueSizeInBytes: String) {
    it.property("maxQueueSizeInBytes", maxQueueSizeInBytes)
  }

  /**
   * Maximum size of the queue in bytes for change events read from the database log but not yet
   * recorded or forwarded. Defaults to 0. Mean the feature is not enabled
   */
  public fun maxQueueSizeInBytes(maxQueueSizeInBytes: Int) {
    it.property("maxQueueSizeInBytes", maxQueueSizeInBytes.toString())
  }

  /**
   * Database containing user credentials.
   */
  public fun mongodbAuthsource(mongodbAuthsource: String) {
    it.property("mongodbAuthsource", mongodbAuthsource)
  }

  /**
   * Database connection string.
   */
  public fun mongodbConnectionString(mongodbConnectionString: String) {
    it.property("mongodbConnectionString", mongodbConnectionString)
  }

  /**
   * The connection timeout, given in milliseconds. Defaults to 10 seconds (10,000 ms).
   */
  public fun mongodbConnectTimeoutMs(mongodbConnectTimeoutMs: String) {
    it.property("mongodbConnectTimeoutMs", mongodbConnectTimeoutMs)
  }

  /**
   * The frequency that the cluster monitor attempts to reach each server. Defaults to 10 seconds
   * (10,000 ms).
   */
  public fun mongodbHeartbeatFrequencyMs(mongodbHeartbeatFrequencyMs: String) {
    it.property("mongodbHeartbeatFrequencyMs", mongodbHeartbeatFrequencyMs)
  }

  /**
   * Password to be used when connecting to MongoDB, if necessary.
   */
  public fun mongodbPassword(mongodbPassword: String) {
    it.property("mongodbPassword", mongodbPassword)
  }

  /**
   * Interval for looking for new, removed, or changed replica sets, given in milliseconds. Defaults
   * to 30 seconds (30,000 ms).
   */
  public fun mongodbPollIntervalMs(mongodbPollIntervalMs: String) {
    it.property("mongodbPollIntervalMs", mongodbPollIntervalMs)
  }

  /**
   * The server selection timeout, given in milliseconds. Defaults to 10 seconds (10,000 ms).
   */
  public fun mongodbServerSelectionTimeoutMs(mongodbServerSelectionTimeoutMs: String) {
    it.property("mongodbServerSelectionTimeoutMs", mongodbServerSelectionTimeoutMs)
  }

  /**
   * The socket timeout, given in milliseconds. Defaults to 0 ms.
   */
  public fun mongodbSocketTimeoutMs(mongodbSocketTimeoutMs: String) {
    it.property("mongodbSocketTimeoutMs", mongodbSocketTimeoutMs)
  }

  /**
   * Should connector use SSL to connect to MongoDB instances
   */
  public fun mongodbSslEnabled(mongodbSslEnabled: String) {
    it.property("mongodbSslEnabled", mongodbSslEnabled)
  }

  /**
   * Should connector use SSL to connect to MongoDB instances
   */
  public fun mongodbSslEnabled(mongodbSslEnabled: Boolean) {
    it.property("mongodbSslEnabled", mongodbSslEnabled.toString())
  }

  /**
   * Whether invalid host names are allowed when using SSL. If true the connection will not prevent
   * man-in-the-middle attacks
   */
  public fun mongodbSslInvalidHostnameAllowed(mongodbSslInvalidHostnameAllowed: String) {
    it.property("mongodbSslInvalidHostnameAllowed", mongodbSslInvalidHostnameAllowed)
  }

  /**
   * Whether invalid host names are allowed when using SSL. If true the connection will not prevent
   * man-in-the-middle attacks
   */
  public fun mongodbSslInvalidHostnameAllowed(mongodbSslInvalidHostnameAllowed: Boolean) {
    it.property("mongodbSslInvalidHostnameAllowed", mongodbSslInvalidHostnameAllowed.toString())
  }

  /**
   * Database user for connecting to MongoDB, if necessary.
   */
  public fun mongodbUser(mongodbUser: String) {
    it.property("mongodbUser", mongodbUser)
  }

  /**
   * List of notification channels names that are enabled.
   */
  public fun notificationEnabledChannels(notificationEnabledChannels: String) {
    it.property("notificationEnabledChannels", notificationEnabledChannels)
  }

  /**
   * The name of the topic for the notifications. This is required in case 'sink' is in the list of
   * enabled channels
   */
  public fun notificationSinkTopicName(notificationSinkTopicName: String) {
    it.property("notificationSinkTopicName", notificationSinkTopicName)
  }

  /**
   * Time to wait for new change events to appear after receiving no events, given in milliseconds.
   * Defaults to 500 ms.
   */
  public fun pollIntervalMs(pollIntervalMs: String) {
    it.property("pollIntervalMs", pollIntervalMs)
  }

  /**
   * Optional list of post processors. The processors are defined using '.type' config option and
   * configured using options ''
   */
  public fun postProcessors(postProcessors: String) {
    it.property("postProcessors", postProcessors)
  }

  /**
   * Enables transaction metadata extraction together with event counting
   */
  public fun provideTransactionMetadata(provideTransactionMetadata: String) {
    it.property("provideTransactionMetadata", provideTransactionMetadata)
  }

  /**
   * Enables transaction metadata extraction together with event counting
   */
  public fun provideTransactionMetadata(provideTransactionMetadata: Boolean) {
    it.property("provideTransactionMetadata", provideTransactionMetadata.toString())
  }

  /**
   * The maximum number of records that should be loaded into memory while streaming. A value of '0'
   * uses the default JDBC fetch size.
   */
  public fun queryFetchSize(queryFetchSize: String) {
    it.property("queryFetchSize", queryFetchSize)
  }

  /**
   * The maximum number of records that should be loaded into memory while streaming. A value of '0'
   * uses the default JDBC fetch size.
   */
  public fun queryFetchSize(queryFetchSize: Int) {
    it.property("queryFetchSize", queryFetchSize.toString())
  }

  /**
   * Time to wait before restarting connector after retriable exception occurs. Defaults to 10000ms.
   */
  public fun retriableRestartConnectorWaitMs(retriableRestartConnectorWaitMs: String) {
    it.property("retriableRestartConnectorWaitMs", retriableRestartConnectorWaitMs)
  }

  /**
   * The path to the file that will be used to record the database schema history
   */
  public fun schemaHistoryInternalFileFilename(schemaHistoryInternalFileFilename: String) {
    it.property("schemaHistoryInternalFileFilename", schemaHistoryInternalFileFilename)
  }

  /**
   * Specify how schema names should be adjusted for compatibility with the message converter used
   * by the connector, including: 'avro' replaces the characters that cannot be used in the Avro type
   * name with underscore; 'avro_unicode' replaces the underscore or characters that cannot be used in
   * the Avro type name with corresponding unicode like _uxxxx. Note: _ is an escape sequence like
   * backslash in Java;'none' does not apply any adjustment (default)
   */
  public fun schemaNameAdjustmentMode(schemaNameAdjustmentMode: String) {
    it.property("schemaNameAdjustmentMode", schemaNameAdjustmentMode)
  }

  /**
   * The name of the data collection that is used to send signals/commands to Debezium. Signaling is
   * disabled when not set.
   */
  public fun signalDataCollection(signalDataCollection: String) {
    it.property("signalDataCollection", signalDataCollection)
  }

  /**
   * List of channels names that are enabled. Source channel is enabled by default
   */
  public fun signalEnabledChannels(signalEnabledChannels: String) {
    it.property("signalEnabledChannels", signalEnabledChannels)
  }

  /**
   * Interval for looking for new signals in registered channels, given in milliseconds. Defaults to
   * 5 seconds.
   */
  public fun signalPollIntervalMs(signalPollIntervalMs: String) {
    it.property("signalPollIntervalMs", signalPollIntervalMs)
  }

  /**
   * The comma-separated list of operations to skip during streaming, defined as: 'c' for
   * inserts/create; 'u' for updates; 'd' for deletes, 't' for truncates, and 'none' to indicate
   * nothing skipped. By default, only truncate operations will be skipped.
   */
  public fun skippedOperations(skippedOperations: String) {
    it.property("skippedOperations", skippedOperations)
  }

  /**
   * This property contains a comma-separated list of ., for which the initial snapshot may be a
   * subset of data present in the data source. The subset would be defined by mongodb filter query
   * specified as value for property snapshot.collection.filter.override..
   */
  public fun snapshotCollectionFilterOverrides(snapshotCollectionFilterOverrides: String) {
    it.property("snapshotCollectionFilterOverrides", snapshotCollectionFilterOverrides)
  }

  /**
   * A delay period before a snapshot will begin, given in milliseconds. Defaults to 0 ms.
   */
  public fun snapshotDelayMs(snapshotDelayMs: String) {
    it.property("snapshotDelayMs", snapshotDelayMs)
  }

  /**
   * The maximum number of records that should be loaded into memory while performing a snapshot.
   */
  public fun snapshotFetchSize(snapshotFetchSize: String) {
    it.property("snapshotFetchSize", snapshotFetchSize)
  }

  /**
   * The maximum number of records that should be loaded into memory while performing a snapshot.
   */
  public fun snapshotFetchSize(snapshotFetchSize: Int) {
    it.property("snapshotFetchSize", snapshotFetchSize.toString())
  }

  /**
   * This setting must be set to specify a list of tables/collections whose snapshot must be taken
   * on creating or restarting the connector.
   */
  public fun snapshotIncludeCollectionList(snapshotIncludeCollectionList: String) {
    it.property("snapshotIncludeCollectionList", snapshotIncludeCollectionList)
  }

  /**
   * The maximum number of threads used to perform the snapshot. Defaults to 1.
   */
  public fun snapshotMaxThreads(snapshotMaxThreads: String) {
    it.property("snapshotMaxThreads", snapshotMaxThreads)
  }

  /**
   * The maximum number of threads used to perform the snapshot. Defaults to 1.
   */
  public fun snapshotMaxThreads(snapshotMaxThreads: Int) {
    it.property("snapshotMaxThreads", snapshotMaxThreads.toString())
  }

  /**
   * The criteria for running a snapshot upon startup of the connector. Select one of the following
   * snapshot options: 'initial' (default): If the connector does not detect any offsets for the
   * logical server name, it runs a snapshot that captures the current full state of the configured
   * tables. After the snapshot completes, the connector begins to stream changes from the oplog.
   * 'never': The connector does not run a snapshot. Upon first startup, the connector immediately
   * begins reading from the beginning of the oplog.
   */
  public fun snapshotMode(snapshotMode: String) {
    it.property("snapshotMode", snapshotMode)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the data should be snapshotted or not.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotData(snapshotModeConfigurationBasedSnapshotData: String) {
    it.property("snapshotModeConfigurationBasedSnapshotData",
        snapshotModeConfigurationBasedSnapshotData)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the data should be snapshotted or not.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotData(snapshotModeConfigurationBasedSnapshotData: Boolean) {
    it.property("snapshotModeConfigurationBasedSnapshotData",
        snapshotModeConfigurationBasedSnapshotData.toString())
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the data should be snapshotted or not in case of error.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotOnDataError(snapshotModeConfigurationBasedSnapshotOnDataError: String) {
    it.property("snapshotModeConfigurationBasedSnapshotOnDataError",
        snapshotModeConfigurationBasedSnapshotOnDataError)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the data should be snapshotted or not in case of error.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotOnDataError(snapshotModeConfigurationBasedSnapshotOnDataError: Boolean) {
    it.property("snapshotModeConfigurationBasedSnapshotOnDataError",
        snapshotModeConfigurationBasedSnapshotOnDataError.toString())
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the schema should be snapshotted or not in case of error.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotOnSchemaError(snapshotModeConfigurationBasedSnapshotOnSchemaError: String) {
    it.property("snapshotModeConfigurationBasedSnapshotOnSchemaError",
        snapshotModeConfigurationBasedSnapshotOnSchemaError)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the schema should be snapshotted or not in case of error.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotOnSchemaError(snapshotModeConfigurationBasedSnapshotOnSchemaError: Boolean) {
    it.property("snapshotModeConfigurationBasedSnapshotOnSchemaError",
        snapshotModeConfigurationBasedSnapshotOnSchemaError.toString())
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the schema should be snapshotted or not.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotSchema(snapshotModeConfigurationBasedSnapshotSchema: String) {
    it.property("snapshotModeConfigurationBasedSnapshotSchema",
        snapshotModeConfigurationBasedSnapshotSchema)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the schema should be snapshotted or not.
   */
  public
      fun snapshotModeConfigurationBasedSnapshotSchema(snapshotModeConfigurationBasedSnapshotSchema: Boolean) {
    it.property("snapshotModeConfigurationBasedSnapshotSchema",
        snapshotModeConfigurationBasedSnapshotSchema.toString())
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the stream should start or not after snapshot.
   */
  public
      fun snapshotModeConfigurationBasedStartStream(snapshotModeConfigurationBasedStartStream: String) {
    it.property("snapshotModeConfigurationBasedStartStream",
        snapshotModeConfigurationBasedStartStream)
  }

  /**
   * When 'snapshot.mode' is set as configuration_based, this setting permits to specify whenever
   * the stream should start or not after snapshot.
   */
  public
      fun snapshotModeConfigurationBasedStartStream(snapshotModeConfigurationBasedStartStream: Boolean) {
    it.property("snapshotModeConfigurationBasedStartStream",
        snapshotModeConfigurationBasedStartStream.toString())
  }

  /**
   * When 'snapshot.mode' is set as custom, this setting must be set to specify a the name of the
   * custom implementation provided in the 'name()' method. The implementations must implement the
   * 'Snapshotter' interface and is called on each app boot to determine whether to do a snapshot.
   */
  public fun snapshotModeCustomName(snapshotModeCustomName: String) {
    it.property("snapshotModeCustomName", snapshotModeCustomName)
  }

  /**
   * The name of the SourceInfoStructMaker class that returns SourceInfo schema and struct.
   */
  public fun sourceinfoStructMaker(sourceinfoStructMaker: String) {
    it.property("sourceinfoStructMaker", sourceinfoStructMaker)
  }

  /**
   * A delay period after the snapshot is completed and the streaming begins, given in milliseconds.
   * Defaults to 0 ms.
   */
  public fun streamingDelayMs(streamingDelayMs: String) {
    it.property("streamingDelayMs", streamingDelayMs)
  }

  /**
   * Whether delete operations should be represented by a delete event and a subsequent tombstone
   * event (true) or only by a delete event (false). Emitting the tombstone event (the default
   * behavior) allows Kafka to completely delete all events pertaining to the given key once the source
   * record got deleted.
   */
  public fun tombstonesOnDelete(tombstonesOnDelete: String) {
    it.property("tombstonesOnDelete", tombstonesOnDelete)
  }

  /**
   * Whether delete operations should be represented by a delete event and a subsequent tombstone
   * event (true) or only by a delete event (false). Emitting the tombstone event (the default
   * behavior) allows Kafka to completely delete all events pertaining to the given key once the source
   * record got deleted.
   */
  public fun tombstonesOnDelete(tombstonesOnDelete: Boolean) {
    it.property("tombstonesOnDelete", tombstonesOnDelete.toString())
  }

  /**
   * The name of the TopicNamingStrategy class that should be used to determine the topic name for
   * data change, schema change, transaction, heartbeat event etc.
   */
  public fun topicNamingStrategy(topicNamingStrategy: String) {
    it.property("topicNamingStrategy", topicNamingStrategy)
  }

  /**
   * Topic prefix that identifies and provides a namespace for the particular database
   * server/cluster is capturing changes. The topic prefix should be unique across all other
   * connectors, since it is used as a prefix for all Kafka topic names that receive events emitted by
   * this connector. Only alphanumeric characters, hyphens, dots and underscores must be accepted.
   */
  public fun topicPrefix(topicPrefix: String) {
    it.property("topicPrefix", topicPrefix)
  }

  /**
   * Class to make transaction context & transaction struct/schemas
   */
  public fun transactionMetadataFactory(transactionMetadataFactory: String) {
    it.property("transactionMetadataFactory", transactionMetadataFactory)
  }
}
