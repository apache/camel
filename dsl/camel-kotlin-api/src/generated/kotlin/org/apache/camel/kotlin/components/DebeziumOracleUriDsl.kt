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
 * Capture changes from an Oracle database.
 */
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
   * Sets the specific archive log destination as the source for reading archive logs.When not set,
   * the connector will automatically select the first LOCAL and VALID destination.
   */
  public fun archiveDestinationName(archiveDestinationName: String) {
    it.property("archiveDestinationName", archiveDestinationName)
  }

  /**
   * The number of hours in the past from SYSDATE to mine archive logs. Using 0 mines all available
   * archive logs
   */
  public fun archiveLogHours(archiveLogHours: String) {
    it.property("archiveLogHours", archiveLogHours)
  }

  /**
   * The number of hours in the past from SYSDATE to mine archive logs. Using 0 mines all available
   * archive logs
   */
  public fun archiveLogHours(archiveLogHours: Int) {
    it.property("archiveLogHours", archiveLogHours.toString())
  }

  /**
   * Specify how binary (blob, binary, etc.) columns should be represented in change events,
   * including: 'bytes' represents binary data as byte array (default); 'base64' represents binary data
   * as base64-encoded string; 'base64-url-safe' represents binary data as base64-url-safe-encoded
   * string; 'hex' represents binary data as hex-encoded (base16) string
   */
  public fun binaryHandlingMode(binaryHandlingMode: String) {
    it.property("binaryHandlingMode", binaryHandlingMode)
  }

  /**
   * Regular expressions matching columns to exclude from change events
   */
  public fun columnExcludeList(columnExcludeList: String) {
    it.property("columnExcludeList", columnExcludeList)
  }

  /**
   * Regular expressions matching columns to include in change events
   */
  public fun columnIncludeList(columnIncludeList: String) {
    it.property("columnIncludeList", columnIncludeList)
  }

  /**
   * A comma-separated list of regular expressions matching fully-qualified names of columns that
   * adds the columns original type and original length as parameters to the corresponding field
   * schemas in the emitted change records.
   */
  public fun columnPropagateSourceType(columnPropagateSourceType: String) {
    it.property("columnPropagateSourceType", columnPropagateSourceType)
  }

  /**
   * Optional list of custom converters that would be used instead of default ones. The converters
   * are defined using '.type' config option and configured using options '.'
   */
  public fun converters(converters: String) {
    it.property("converters", converters)
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
   * The adapter to use when capturing changes from the database. Options include: 'logminer': (the
   * default) to capture changes using native Oracle LogMiner; 'xstream' to capture changes using
   * Oracle XStreams
   */
  public fun databaseConnectionAdapter(databaseConnectionAdapter: String) {
    it.property("databaseConnectionAdapter", databaseConnectionAdapter)
  }

  /**
   * The name of the database from which the connector should capture changes
   */
  public fun databaseDbname(databaseDbname: String) {
    it.property("databaseDbname", databaseDbname)
  }

  /**
   * Resolvable hostname or IP address of the database server.
   */
  public fun databaseHostname(databaseHostname: String) {
    it.property("databaseHostname", databaseHostname)
  }

  /**
   * Name of the XStream Out server to connect to.
   */
  public fun databaseOutServerName(databaseOutServerName: String) {
    it.property("databaseOutServerName", databaseOutServerName)
  }

  /**
   * Password of the database user to be used when connecting to the database.
   */
  public fun databasePassword(databasePassword: String) {
    it.property("databasePassword", databasePassword)
  }

  /**
   * Name of the pluggable database when working with a multi-tenant set-up. The CDB name must be
   * given via database.dbname in this case.
   */
  public fun databasePdbName(databasePdbName: String) {
    it.property("databasePdbName", databasePdbName)
  }

  /**
   * Port of the database server.
   */
  public fun databasePort(databasePort: String) {
    it.property("databasePort", databasePort)
  }

  /**
   * Port of the database server.
   */
  public fun databasePort(databasePort: Int) {
    it.property("databasePort", databasePort.toString())
  }

  /**
   * Time to wait for a query to execute, given in milliseconds. Defaults to 600 seconds (600,000
   * ms); zero means there is no limit.
   */
  public fun databaseQueryTimeoutMs(databaseQueryTimeoutMs: String) {
    it.property("databaseQueryTimeoutMs", databaseQueryTimeoutMs)
  }

  /**
   * Complete JDBC URL as an alternative to specifying hostname, port and database provided as a way
   * to support alternative connection scenarios.
   */
  public fun databaseUrl(databaseUrl: String) {
    it.property("databaseUrl", databaseUrl)
  }

  /**
   * Name of the database user to be used when connecting to the database.
   */
  public fun databaseUser(databaseUser: String) {
    it.property("databaseUser", databaseUser)
  }

  /**
   * A comma-separated list of regular expressions matching the database-specific data type names
   * that adds the data type's original type and original length as parameters to the corresponding
   * field schemas in the emitted change records.
   */
  public fun datatypePropagateSourceType(datatypePropagateSourceType: String) {
    it.property("datatypePropagateSourceType", datatypePropagateSourceType)
  }

  /**
   * Specify how DECIMAL and NUMERIC columns should be represented in change events, including:
   * 'precise' (the default) uses java.math.BigDecimal to represent values, which are encoded in the
   * change events using a binary representation and Kafka Connect's
   * 'org.apache.kafka.connect.data.Decimal' type; 'string' uses string to represent values; 'double'
   * represents values using Java's 'double', which may not offer the precision but will be far easier
   * to use in consumers.
   */
  public fun decimalHandlingMode(decimalHandlingMode: String) {
    it.property("decimalHandlingMode", decimalHandlingMode)
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
   * The query executed with every heartbeat.
   */
  public fun heartbeatActionQuery(heartbeatActionQuery: String) {
    it.property("heartbeatActionQuery", heartbeatActionQuery)
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
   * Whether the connector should publish changes in the database schema to a Kafka topic with the
   * same name as the database server ID. Each schema change will be recorded using a key that contains
   * the database name and whose value include logical description of the new schema and optionally the
   * DDL statement(s). The default is 'true'. This is independent of how the connector internally
   * records database schema history.
   */
  public fun includeSchemaChanges(includeSchemaChanges: String) {
    it.property("includeSchemaChanges", includeSchemaChanges)
  }

  /**
   * Whether the connector should publish changes in the database schema to a Kafka topic with the
   * same name as the database server ID. Each schema change will be recorded using a key that contains
   * the database name and whose value include logical description of the new schema and optionally the
   * DDL statement(s). The default is 'true'. This is independent of how the connector internally
   * records database schema history.
   */
  public fun includeSchemaChanges(includeSchemaChanges: Boolean) {
    it.property("includeSchemaChanges", includeSchemaChanges.toString())
  }

  /**
   * Whether the connector parse table and column's comment to metadata object. Note: Enable this
   * option will bring the implications on memory usage. The number and size of ColumnImpl objects is
   * what largely impacts how much memory is consumed by the Debezium connectors, and adding a String
   * to each of them can potentially be quite heavy. The default is 'false'.
   */
  public fun includeSchemaComments(includeSchemaComments: String) {
    it.property("includeSchemaComments", includeSchemaComments)
  }

  /**
   * Whether the connector parse table and column's comment to metadata object. Note: Enable this
   * option will bring the implications on memory usage. The number and size of ColumnImpl objects is
   * what largely impacts how much memory is consumed by the Debezium connectors, and adding a String
   * to each of them can potentially be quite heavy. The default is 'false'.
   */
  public fun includeSchemaComments(includeSchemaComments: Boolean) {
    it.property("includeSchemaComments", includeSchemaComments.toString())
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
   * Specify how INTERVAL columns should be represented in change events, including: 'string'
   * represents values as an exact ISO formatted string; 'numeric' (default) represents values using
   * the inexact conversion into microseconds
   */
  public fun intervalHandlingMode(intervalHandlingMode: String) {
    it.property("intervalHandlingMode", intervalHandlingMode)
  }

  /**
   * When set to 'false', the default, LOB fields will not be captured nor emitted. When set to
   * 'true', the connector will capture LOB fields and emit changes for those fields like any other
   * column type.
   */
  public fun lobEnabled(lobEnabled: String) {
    it.property("lobEnabled", lobEnabled)
  }

  /**
   * When set to 'false', the default, LOB fields will not be captured nor emitted. When set to
   * 'true', the connector will capture LOB fields and emit changes for those fields like any other
   * column type.
   */
  public fun lobEnabled(lobEnabled: Boolean) {
    it.property("lobEnabled", lobEnabled.toString())
  }

  /**
   * When set to 'false', the default, the connector will mine both archive log and redo logs to
   * emit change events. When set to 'true', the connector will only mine archive logs. There are
   * circumstances where its advantageous to only mine archive logs and accept latency in event
   * emission due to frequent revolving redo logs.
   */
  public fun logMiningArchiveLogOnlyMode(logMiningArchiveLogOnlyMode: String) {
    it.property("logMiningArchiveLogOnlyMode", logMiningArchiveLogOnlyMode)
  }

  /**
   * When set to 'false', the default, the connector will mine both archive log and redo logs to
   * emit change events. When set to 'true', the connector will only mine archive logs. There are
   * circumstances where its advantageous to only mine archive logs and accept latency in event
   * emission due to frequent revolving redo logs.
   */
  public fun logMiningArchiveLogOnlyMode(logMiningArchiveLogOnlyMode: Boolean) {
    it.property("logMiningArchiveLogOnlyMode", logMiningArchiveLogOnlyMode.toString())
  }

  /**
   * The interval in milliseconds to wait between polls checking to see if the SCN is in the archive
   * logs.
   */
  public
      fun logMiningArchiveLogOnlyScnPollIntervalMs(logMiningArchiveLogOnlyScnPollIntervalMs: String) {
    it.property("logMiningArchiveLogOnlyScnPollIntervalMs",
        logMiningArchiveLogOnlyScnPollIntervalMs)
  }

  /**
   * The starting SCN interval size that the connector will use for reading data from redo/archive
   * logs.
   */
  public fun logMiningBatchSizeDefault(logMiningBatchSizeDefault: String) {
    it.property("logMiningBatchSizeDefault", logMiningBatchSizeDefault)
  }

  /**
   * The starting SCN interval size that the connector will use for reading data from redo/archive
   * logs.
   */
  public fun logMiningBatchSizeDefault(logMiningBatchSizeDefault: Int) {
    it.property("logMiningBatchSizeDefault", logMiningBatchSizeDefault.toString())
  }

  /**
   * The maximum SCN interval size that this connector will use when reading from redo/archive logs.
   */
  public fun logMiningBatchSizeMax(logMiningBatchSizeMax: String) {
    it.property("logMiningBatchSizeMax", logMiningBatchSizeMax)
  }

  /**
   * The maximum SCN interval size that this connector will use when reading from redo/archive logs.
   */
  public fun logMiningBatchSizeMax(logMiningBatchSizeMax: Int) {
    it.property("logMiningBatchSizeMax", logMiningBatchSizeMax.toString())
  }

  /**
   * The minimum SCN interval size that this connector will try to read from redo/archive logs.
   * Active batch size will be also increased/decreased by this amount for tuning connector throughput
   * when needed.
   */
  public fun logMiningBatchSizeMin(logMiningBatchSizeMin: String) {
    it.property("logMiningBatchSizeMin", logMiningBatchSizeMin)
  }

  /**
   * The minimum SCN interval size that this connector will try to read from redo/archive logs.
   * Active batch size will be also increased/decreased by this amount for tuning connector throughput
   * when needed.
   */
  public fun logMiningBatchSizeMin(logMiningBatchSizeMin: Int) {
    it.property("logMiningBatchSizeMin", logMiningBatchSizeMin.toString())
  }

  /**
   * When set to true the underlying buffer cache is not retained when the connector is stopped.
   * When set to false (the default), the buffer cache is retained across restarts.
   */
  public fun logMiningBufferDropOnStop(logMiningBufferDropOnStop: String) {
    it.property("logMiningBufferDropOnStop", logMiningBufferDropOnStop)
  }

  /**
   * When set to true the underlying buffer cache is not retained when the connector is stopped.
   * When set to false (the default), the buffer cache is retained across restarts.
   */
  public fun logMiningBufferDropOnStop(logMiningBufferDropOnStop: Boolean) {
    it.property("logMiningBufferDropOnStop", logMiningBufferDropOnStop.toString())
  }

  /**
   * Specifies the XML configuration for the Infinispan 'events' cache
   */
  public fun logMiningBufferInfinispanCacheEvents(logMiningBufferInfinispanCacheEvents: String) {
    it.property("logMiningBufferInfinispanCacheEvents", logMiningBufferInfinispanCacheEvents)
  }

  /**
   * Specifies the XML configuration for the Infinispan 'global' configuration
   */
  public fun logMiningBufferInfinispanCacheGlobal(logMiningBufferInfinispanCacheGlobal: String) {
    it.property("logMiningBufferInfinispanCacheGlobal", logMiningBufferInfinispanCacheGlobal)
  }

  /**
   * Specifies the XML configuration for the Infinispan 'processed-transactions' cache
   */
  public
      fun logMiningBufferInfinispanCacheProcessedTransactions(logMiningBufferInfinispanCacheProcessedTransactions: String) {
    it.property("logMiningBufferInfinispanCacheProcessedTransactions",
        logMiningBufferInfinispanCacheProcessedTransactions)
  }

  /**
   * Specifies the XML configuration for the Infinispan 'schema-changes' cache
   */
  public
      fun logMiningBufferInfinispanCacheSchemaChanges(logMiningBufferInfinispanCacheSchemaChanges: String) {
    it.property("logMiningBufferInfinispanCacheSchemaChanges",
        logMiningBufferInfinispanCacheSchemaChanges)
  }

  /**
   * Specifies the XML configuration for the Infinispan 'transactions' cache
   */
  public
      fun logMiningBufferInfinispanCacheTransactions(logMiningBufferInfinispanCacheTransactions: String) {
    it.property("logMiningBufferInfinispanCacheTransactions",
        logMiningBufferInfinispanCacheTransactions)
  }

  /**
   * The number of events a transaction can include before the transaction is discarded. This is
   * useful for managing buffer memory and/or space when dealing with very large transactions. Defaults
   * to 0, meaning that no threshold is applied and transactions can have unlimited events.
   */
  public
      fun logMiningBufferTransactionEventsThreshold(logMiningBufferTransactionEventsThreshold: String) {
    it.property("logMiningBufferTransactionEventsThreshold",
        logMiningBufferTransactionEventsThreshold)
  }

  /**
   * The number of events a transaction can include before the transaction is discarded. This is
   * useful for managing buffer memory and/or space when dealing with very large transactions. Defaults
   * to 0, meaning that no threshold is applied and transactions can have unlimited events.
   */
  public
      fun logMiningBufferTransactionEventsThreshold(logMiningBufferTransactionEventsThreshold: Int) {
    it.property("logMiningBufferTransactionEventsThreshold",
        logMiningBufferTransactionEventsThreshold.toString())
  }

  /**
   * The buffer type controls how the connector manages buffering transaction data. memory - Uses
   * the JVM process' heap to buffer all transaction data. infinispan_embedded - This option uses an
   * embedded Infinispan cache to buffer transaction data and persist it to disk. infinispan_remote -
   * This option uses a remote Infinispan cluster to buffer transaction data and persist it to disk.
   */
  public fun logMiningBufferType(logMiningBufferType: String) {
    it.property("logMiningBufferType", logMiningBufferType)
  }

  /**
   * The name of the flush table used by the connector, defaults to LOG_MINING_FLUSH.
   */
  public fun logMiningFlushTableName(logMiningFlushTableName: String) {
    it.property("logMiningFlushTableName", logMiningFlushTableName)
  }

  /**
   * When enabled, the transaction log REDO SQL will be included in the source information block.
   */
  public fun logMiningIncludeRedoSql(logMiningIncludeRedoSql: String) {
    it.property("logMiningIncludeRedoSql", logMiningIncludeRedoSql)
  }

  /**
   * When enabled, the transaction log REDO SQL will be included in the source information block.
   */
  public fun logMiningIncludeRedoSql(logMiningIncludeRedoSql: Boolean) {
    it.property("logMiningIncludeRedoSql", logMiningIncludeRedoSql.toString())
  }

  /**
   * Specifies how the filter configuration is applied to the LogMiner database query. none - The
   * query does not apply any schema or table filters, all filtering is at runtime by the connector.
   * in - The query uses SQL in-clause expressions to specify the schema or table filters. regex - The
   * query uses Oracle REGEXP_LIKE expressions to specify the schema or table filters.
   */
  public fun logMiningQueryFilterMode(logMiningQueryFilterMode: String) {
    it.property("logMiningQueryFilterMode", logMiningQueryFilterMode)
  }

  /**
   * Debezium opens a database connection and keeps that connection open throughout the entire
   * streaming phase. In some situations, this can lead to excessive SGA memory usage. By setting this
   * option to 'true' (the default is 'false'), the connector will close and re-open a database
   * connection after every detected log switch or if the log.mining.session.max.ms has been reached.
   */
  public fun logMiningRestartConnection(logMiningRestartConnection: String) {
    it.property("logMiningRestartConnection", logMiningRestartConnection)
  }

  /**
   * Debezium opens a database connection and keeps that connection open throughout the entire
   * streaming phase. In some situations, this can lead to excessive SGA memory usage. By setting this
   * option to 'true' (the default is 'false'), the connector will close and re-open a database
   * connection after every detected log switch or if the log.mining.session.max.ms has been reached.
   */
  public fun logMiningRestartConnection(logMiningRestartConnection: Boolean) {
    it.property("logMiningRestartConnection", logMiningRestartConnection.toString())
  }

  /**
   * Used for SCN gap detection, if the difference between current SCN and previous end SCN is
   * bigger than this value, and the time difference of current SCN and previous end SCN is smaller
   * than log.mining.scn.gap.detection.time.interval.max.ms, consider it a SCN gap.
   */
  public fun logMiningScnGapDetectionGapSizeMin(logMiningScnGapDetectionGapSizeMin: String) {
    it.property("logMiningScnGapDetectionGapSizeMin", logMiningScnGapDetectionGapSizeMin)
  }

  /**
   * Used for SCN gap detection, if the difference between current SCN and previous end SCN is
   * bigger than this value, and the time difference of current SCN and previous end SCN is smaller
   * than log.mining.scn.gap.detection.time.interval.max.ms, consider it a SCN gap.
   */
  public fun logMiningScnGapDetectionGapSizeMin(logMiningScnGapDetectionGapSizeMin: Int) {
    it.property("logMiningScnGapDetectionGapSizeMin", logMiningScnGapDetectionGapSizeMin.toString())
  }

  /**
   * Used for SCN gap detection, if the difference between current SCN and previous end SCN is
   * bigger than log.mining.scn.gap.detection.gap.size.min, and the time difference of current SCN and
   * previous end SCN is smaller than this value, consider it a SCN gap.
   */
  public
      fun logMiningScnGapDetectionTimeIntervalMaxMs(logMiningScnGapDetectionTimeIntervalMaxMs: String) {
    it.property("logMiningScnGapDetectionTimeIntervalMaxMs",
        logMiningScnGapDetectionTimeIntervalMaxMs)
  }

  /**
   * The maximum number of milliseconds that a LogMiner session lives for before being restarted.
   * Defaults to 0 (indefinite until a log switch occurs)
   */
  public fun logMiningSessionMaxMs(logMiningSessionMaxMs: String) {
    it.property("logMiningSessionMaxMs", logMiningSessionMaxMs)
  }

  /**
   * The amount of time that the connector will sleep after reading data from redo/archive logs and
   * before starting reading data again. Value is in milliseconds.
   */
  public fun logMiningSleepTimeDefaultMs(logMiningSleepTimeDefaultMs: String) {
    it.property("logMiningSleepTimeDefaultMs", logMiningSleepTimeDefaultMs)
  }

  /**
   * The maximum amount of time that the connector will use to tune the optimal sleep time when
   * reading data from LogMiner. Value is in milliseconds.
   */
  public fun logMiningSleepTimeIncrementMs(logMiningSleepTimeIncrementMs: String) {
    it.property("logMiningSleepTimeIncrementMs", logMiningSleepTimeIncrementMs)
  }

  /**
   * The maximum amount of time that the connector will sleep after reading data from redo/archive
   * logs and before starting reading data again. Value is in milliseconds.
   */
  public fun logMiningSleepTimeMaxMs(logMiningSleepTimeMaxMs: String) {
    it.property("logMiningSleepTimeMaxMs", logMiningSleepTimeMaxMs)
  }

  /**
   * The minimum amount of time that the connector will sleep after reading data from redo/archive
   * logs and before starting reading data again. Value is in milliseconds.
   */
  public fun logMiningSleepTimeMinMs(logMiningSleepTimeMinMs: String) {
    it.property("logMiningSleepTimeMinMs", logMiningSleepTimeMinMs)
  }

  /**
   * There are strategies: Online catalog with faster mining but no captured DDL. Another - with
   * data dictionary loaded into REDO LOG files
   */
  public fun logMiningStrategy(logMiningStrategy: String) {
    it.property("logMiningStrategy", logMiningStrategy)
  }

  /**
   * Duration in milliseconds to keep long running transactions in transaction buffer between log
   * mining sessions. By default, all transactions are retained.
   */
  public fun logMiningTransactionRetentionMs(logMiningTransactionRetentionMs: String) {
    it.property("logMiningTransactionRetentionMs", logMiningTransactionRetentionMs)
  }

  /**
   * Comma separated list of usernames to exclude from LogMiner query.
   */
  public fun logMiningUsernameExcludeList(logMiningUsernameExcludeList: String) {
    it.property("logMiningUsernameExcludeList", logMiningUsernameExcludeList)
  }

  /**
   * Comma separated list of usernames to include from LogMiner query.
   */
  public fun logMiningUsernameIncludeList(logMiningUsernameIncludeList: String) {
    it.property("logMiningUsernameIncludeList", logMiningUsernameIncludeList)
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
   * A semicolon-separated list of expressions that match fully-qualified tables and column(s) to be
   * used as message key. Each expression must match the pattern ':', where the table names could be
   * defined as (DB_NAME.TABLE_NAME) or (SCHEMA_NAME.TABLE_NAME), depending on the specific connector,
   * and the key columns are a comma-separated list of columns representing the custom key. For any
   * table without an explicit key configuration the table's primary key column(s) will be used as
   * message key. Example:
   * dbserver1.inventory.orderlines:orderId,orderLineId;dbserver1.inventory.orders:id
   */
  public fun messageKeyColumns(messageKeyColumns: String) {
    it.property("messageKeyColumns", messageKeyColumns)
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
   * The hostname of the OpenLogReplicator network service
   */
  public fun openlogreplicatorHost(openlogreplicatorHost: String) {
    it.property("openlogreplicatorHost", openlogreplicatorHost)
  }

  /**
   * The port of the OpenLogReplicator network service
   */
  public fun openlogreplicatorPort(openlogreplicatorPort: String) {
    it.property("openlogreplicatorPort", openlogreplicatorPort)
  }

  /**
   * The port of the OpenLogReplicator network service
   */
  public fun openlogreplicatorPort(openlogreplicatorPort: Int) {
    it.property("openlogreplicatorPort", openlogreplicatorPort.toString())
  }

  /**
   * The configured logical source name in the OpenLogReplicator configuration that is to stream
   * changes
   */
  public fun openlogreplicatorSource(openlogreplicatorSource: String) {
    it.property("openlogreplicatorSource", openlogreplicatorSource)
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
   * uses the default JDBC fetch size, defaults to '2000'.
   */
  public fun queryFetchSize(queryFetchSize: String) {
    it.property("queryFetchSize", queryFetchSize)
  }

  /**
   * The maximum number of records that should be loaded into memory while streaming. A value of '0'
   * uses the default JDBC fetch size, defaults to '2000'.
   */
  public fun queryFetchSize(queryFetchSize: Int) {
    it.property("queryFetchSize", queryFetchSize.toString())
  }

  /**
   * A comma-separated list of RAC node hostnames or ip addresses
   */
  public fun racNodes(racNodes: String) {
    it.property("racNodes", racNodes)
  }

  /**
   * Time to wait before restarting connector after retriable exception occurs. Defaults to 10000ms.
   */
  public fun retriableRestartConnectorWaitMs(retriableRestartConnectorWaitMs: String) {
    it.property("retriableRestartConnectorWaitMs", retriableRestartConnectorWaitMs)
  }

  /**
   * The name of the SchemaHistory class that should be used to store and recover database schema
   * changes. The configuration properties for the history are prefixed with the
   * 'schema.history.internal.' string.
   */
  public fun schemaHistoryInternal(schemaHistoryInternal: String) {
    it.property("schemaHistoryInternal", schemaHistoryInternal)
  }

  /**
   * The path to the file that will be used to record the database schema history
   */
  public fun schemaHistoryInternalFileFilename(schemaHistoryInternalFileFilename: String) {
    it.property("schemaHistoryInternalFileFilename", schemaHistoryInternalFileFilename)
  }

  /**
   * Controls the action Debezium will take when it meets a DDL statement in binlog, that it cannot
   * parse.By default the connector will stop operating but by changing the setting it can ignore the
   * statements which it cannot parse. If skipping is enabled then Debezium can miss metadata changes.
   */
  public
      fun schemaHistoryInternalSkipUnparseableDdl(schemaHistoryInternalSkipUnparseableDdl: String) {
    it.property("schemaHistoryInternalSkipUnparseableDdl", schemaHistoryInternalSkipUnparseableDdl)
  }

  /**
   * Controls the action Debezium will take when it meets a DDL statement in binlog, that it cannot
   * parse.By default the connector will stop operating but by changing the setting it can ignore the
   * statements which it cannot parse. If skipping is enabled then Debezium can miss metadata changes.
   */
  public
      fun schemaHistoryInternalSkipUnparseableDdl(schemaHistoryInternalSkipUnparseableDdl: Boolean) {
    it.property("schemaHistoryInternalSkipUnparseableDdl",
        schemaHistoryInternalSkipUnparseableDdl.toString())
  }

  /**
   * Controls what DDL will Debezium store in database schema history. By default (true) only DDL
   * that manipulates a table from captured schema/database will be stored. If set to false, then
   * Debezium will store all incoming DDL statements.
   */
  public
      fun schemaHistoryInternalStoreOnlyCapturedDatabasesDdl(schemaHistoryInternalStoreOnlyCapturedDatabasesDdl: String) {
    it.property("schemaHistoryInternalStoreOnlyCapturedDatabasesDdl",
        schemaHistoryInternalStoreOnlyCapturedDatabasesDdl)
  }

  /**
   * Controls what DDL will Debezium store in database schema history. By default (true) only DDL
   * that manipulates a table from captured schema/database will be stored. If set to false, then
   * Debezium will store all incoming DDL statements.
   */
  public
      fun schemaHistoryInternalStoreOnlyCapturedDatabasesDdl(schemaHistoryInternalStoreOnlyCapturedDatabasesDdl: Boolean) {
    it.property("schemaHistoryInternalStoreOnlyCapturedDatabasesDdl",
        schemaHistoryInternalStoreOnlyCapturedDatabasesDdl.toString())
  }

  /**
   * Controls what DDL will Debezium store in database schema history. By default (false) Debezium
   * will store all incoming DDL statements. If set to true, then only DDL that manipulates a captured
   * table will be stored.
   */
  public
      fun schemaHistoryInternalStoreOnlyCapturedTablesDdl(schemaHistoryInternalStoreOnlyCapturedTablesDdl: String) {
    it.property("schemaHistoryInternalStoreOnlyCapturedTablesDdl",
        schemaHistoryInternalStoreOnlyCapturedTablesDdl)
  }

  /**
   * Controls what DDL will Debezium store in database schema history. By default (false) Debezium
   * will store all incoming DDL statements. If set to true, then only DDL that manipulates a captured
   * table will be stored.
   */
  public
      fun schemaHistoryInternalStoreOnlyCapturedTablesDdl(schemaHistoryInternalStoreOnlyCapturedTablesDdl: Boolean) {
    it.property("schemaHistoryInternalStoreOnlyCapturedTablesDdl",
        schemaHistoryInternalStoreOnlyCapturedTablesDdl.toString())
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
   * The number of attempts to retry database errors during snapshots before failing.
   */
  public fun snapshotDatabaseErrorsMaxRetries(snapshotDatabaseErrorsMaxRetries: String) {
    it.property("snapshotDatabaseErrorsMaxRetries", snapshotDatabaseErrorsMaxRetries)
  }

  /**
   * The number of attempts to retry database errors during snapshots before failing.
   */
  public fun snapshotDatabaseErrorsMaxRetries(snapshotDatabaseErrorsMaxRetries: Int) {
    it.property("snapshotDatabaseErrorsMaxRetries", snapshotDatabaseErrorsMaxRetries.toString())
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
   * Controls how the connector holds locks on tables while performing the schema snapshot. The
   * default is 'shared', which means the connector will hold a table lock that prevents exclusive
   * table access for just the initial portion of the snapshot while the database schemas and other
   * metadata are being read. The remaining work in a snapshot involves selecting all rows from each
   * table, and this is done using a flashback query that requires no locks. However, in some cases it
   * may be desirable to avoid locks entirely which can be done by specifying 'none'. This mode is only
   * safe to use if no schema changes are happening while the snapshot is taken.
   */
  public fun snapshotLockingMode(snapshotLockingMode: String) {
    it.property("snapshotLockingMode", snapshotLockingMode)
  }

  /**
   * The maximum number of millis to wait for table locks at the beginning of a snapshot. If locks
   * cannot be acquired in this time frame, the snapshot will be aborted. Defaults to 10 seconds
   */
  public fun snapshotLockTimeoutMs(snapshotLockTimeoutMs: String) {
    it.property("snapshotLockTimeoutMs", snapshotLockTimeoutMs)
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
   * snapshot options: 'always': The connector runs a snapshot every time that it starts. After the
   * snapshot completes, the connector begins to stream changes from the redo logs.; 'initial'
   * (default): If the connector does not detect any offsets for the logical server name, it runs a
   * snapshot that captures the current full state of the configured tables. After the snapshot
   * completes, the connector begins to stream changes from the redo logs. 'initial_only': The
   * connector performs a snapshot as it does for the 'initial' option, but after the connector
   * completes the snapshot, it stops, and does not stream changes from the redo logs.; 'schema_only':
   * If the connector does not detect any offsets for the logical server name, it runs a snapshot that
   * captures only the schema (table structures), but not any table data. After the snapshot completes,
   * the connector begins to stream changes from the redo logs.; 'schema_only_recovery': The connector
   * performs a snapshot that captures only the database schema history. The connector then transitions
   * to streaming from the redo logs. Use this setting to restore a corrupted or lost database schema
   * history topic. Do not use if the database schema was modified after the connector stopped.
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
   * This property contains a comma-separated list of fully-qualified tables (DB_NAME.TABLE_NAME) or
   * (SCHEMA_NAME.TABLE_NAME), depending on the specific connectors. Select statements for the
   * individual tables are specified in further configuration properties, one for each table,
   * identified by the id 'snapshot.select.statement.overrides.DB_NAME.TABLE_NAME' or
   * 'snapshot.select.statement.overrides.SCHEMA_NAME.TABLE_NAME', respectively. The value of those
   * properties is the select statement to use when retrieving data from the specific table during
   * snapshotting. A possible use case for large append-only tables is setting a specific point where
   * to start (resume) snapshotting, in case a previous snapshotting was interrupted.
   */
  public fun snapshotSelectStatementOverrides(snapshotSelectStatementOverrides: String) {
    it.property("snapshotSelectStatementOverrides", snapshotSelectStatementOverrides)
  }

  /**
   * Controls the order in which tables are processed in the initial snapshot. A descending value
   * will order the tables by row count descending. A ascending value will order the tables by row
   * count ascending. A value of disabled (the default) will disable ordering by row count.
   */
  public fun snapshotTablesOrderByRowCount(snapshotTablesOrderByRowCount: String) {
    it.property("snapshotTablesOrderByRowCount", snapshotTablesOrderByRowCount)
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
   * A comma-separated list of regular expressions that match the fully-qualified names of tables to
   * be excluded from monitoring
   */
  public fun tableExcludeList(tableExcludeList: String) {
    it.property("tableExcludeList", tableExcludeList)
  }

  /**
   * The tables for which changes are to be captured
   */
  public fun tableIncludeList(tableIncludeList: String) {
    it.property("tableIncludeList", tableIncludeList)
  }

  /**
   * Time, date, and timestamps can be represented with different kinds of precisions, including:
   * 'adaptive' (the default) bases the precision of time, date, and timestamp values on the database
   * column's precision; 'adaptive_time_microseconds' like 'adaptive' mode, but TIME fields always use
   * microseconds precision; 'connect' always represents time, date, and timestamp values using Kafka
   * Connect's built-in representations for Time, Date, and Timestamp, which uses millisecond precision
   * regardless of the database columns' precision.
   */
  public fun timePrecisionMode(timePrecisionMode: String) {
    it.property("timePrecisionMode", timePrecisionMode)
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

  /**
   * Specify the constant that will be provided by Debezium to indicate that the original value is
   * unavailable and not provided by the database.
   */
  public fun unavailableValuePlaceholder(unavailableValuePlaceholder: String) {
    it.property("unavailableValuePlaceholder", unavailableValuePlaceholder)
  }
}
