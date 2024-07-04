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
 * Capture changes from a PostgresSQL database.
 */
public fun UriDsl.`debezium-postgres`(i: DebeziumPostgresUriDsl.() -> Unit) {
  DebeziumPostgresUriDsl(this).apply(i)
}

@CamelDslMarker
public class DebeziumPostgresUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("debezium-postgres")
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
   * A semicolon separated list of SQL statements to be executed when a JDBC connection to the
   * database is established. Note that the connector may establish JDBC connections at its own
   * discretion, so this should typically be used for configuration of session parameters only, but not
   * for executing DML statements. Use doubled semicolon (';;') to use a semicolon as a character and
   * not as a delimiter.
   */
  public fun databaseInitialStatements(databaseInitialStatements: String) {
    it.property("databaseInitialStatements", databaseInitialStatements)
  }

  /**
   * Password of the database user to be used when connecting to the database.
   */
  public fun databasePassword(databasePassword: String) {
    it.property("databasePassword", databasePassword)
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
   * File containing the SSL Certificate for the client. See the Postgres SSL docs for further
   * information
   */
  public fun databaseSslcert(databaseSslcert: String) {
    it.property("databaseSslcert", databaseSslcert)
  }

  /**
   * A name of class to that creates SSL Sockets. Use org.postgresql.ssl.NonValidatingFactory to
   * disable SSL validation in development environments
   */
  public fun databaseSslfactory(databaseSslfactory: String) {
    it.property("databaseSslfactory", databaseSslfactory)
  }

  /**
   * File containing the SSL private key for the client. See the Postgres SSL docs for further
   * information
   */
  public fun databaseSslkey(databaseSslkey: String) {
    it.property("databaseSslkey", databaseSslkey)
  }

  /**
   * Whether to use an encrypted connection to Postgres. Options include: 'disable' (the default) to
   * use an unencrypted connection; 'allow' to try and use an unencrypted connection first and, failing
   * that, a secure (encrypted) connection; 'prefer' (the default) to try and use a secure (encrypted)
   * connection first and, failing that, an unencrypted connection; 'require' to use a secure
   * (encrypted) connection, and fail if one cannot be established; 'verify-ca' like 'required' but
   * additionally verify the server TLS certificate against the configured Certificate Authority (CA)
   * certificates, or fail if no valid matching CA certificates are found; or 'verify-full' like
   * 'verify-ca' but additionally verify that the server certificate matches the host to which the
   * connection is attempted.
   */
  public fun databaseSslmode(databaseSslmode: String) {
    it.property("databaseSslmode", databaseSslmode)
  }

  /**
   * Password to access the client private key from the file specified by 'database.sslkey'. See the
   * Postgres SSL docs for further information
   */
  public fun databaseSslpassword(databaseSslpassword: String) {
    it.property("databaseSslpassword", databaseSslpassword)
  }

  /**
   * File containing the root certificate(s) against which the server is validated. See the Postgres
   * JDBC SSL docs for further information
   */
  public fun databaseSslrootcert(databaseSslrootcert: String) {
    it.property("databaseSslrootcert", databaseSslrootcert)
  }

  /**
   * Enable or disable TCP keep-alive probe to avoid dropping TCP connection
   */
  public fun databaseTcpkeepalive(databaseTcpkeepalive: String) {
    it.property("databaseTcpkeepalive", databaseTcpkeepalive)
  }

  /**
   * Enable or disable TCP keep-alive probe to avoid dropping TCP connection
   */
  public fun databaseTcpkeepalive(databaseTcpkeepalive: Boolean) {
    it.property("databaseTcpkeepalive", databaseTcpkeepalive.toString())
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
   * Boolean to determine if Debezium should flush LSN in the source postgres database. If set to
   * false, user will have to flush the LSN manually outside Debezium.
   */
  public fun flushLsnSource(flushLsnSource: String) {
    it.property("flushLsnSource", flushLsnSource)
  }

  /**
   * Boolean to determine if Debezium should flush LSN in the source postgres database. If set to
   * false, user will have to flush the LSN manually outside Debezium.
   */
  public fun flushLsnSource(flushLsnSource: Boolean) {
    it.property("flushLsnSource", flushLsnSource.toString())
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
   * Specify how HSTORE columns should be represented in change events, including: 'json' represents
   * values as string-ified JSON (default); 'map' represents values as a key/value map
   */
  public fun hstoreHandlingMode(hstoreHandlingMode: String) {
    it.property("hstoreHandlingMode", hstoreHandlingMode)
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
   * Specify whether the fields of data type not supported by Debezium should be processed: 'false'
   * (the default) omits the fields; 'true' converts the field into an implementation dependent binary
   * representation.
   */
  public fun includeUnknownDatatypes(includeUnknownDatatypes: String) {
    it.property("includeUnknownDatatypes", includeUnknownDatatypes)
  }

  /**
   * Specify whether the fields of data type not supported by Debezium should be processed: 'false'
   * (the default) omits the fields; 'true' converts the field into an implementation dependent binary
   * representation.
   */
  public fun includeUnknownDatatypes(includeUnknownDatatypes: Boolean) {
    it.property("includeUnknownDatatypes", includeUnknownDatatypes.toString())
  }

  /**
   * The maximum size of chunk (number of documents/rows) for incremental snapshotting
   */
  public fun incrementalSnapshotChunkSize(incrementalSnapshotChunkSize: String) {
    it.property("incrementalSnapshotChunkSize", incrementalSnapshotChunkSize)
  }

  /**
   * The maximum size of chunk (number of documents/rows) for incremental snapshotting
   */
  public fun incrementalSnapshotChunkSize(incrementalSnapshotChunkSize: Int) {
    it.property("incrementalSnapshotChunkSize", incrementalSnapshotChunkSize.toString())
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
   * A comma-separated list of regular expressions that match the logical decoding message prefixes
   * to be excluded from monitoring.
   */
  public fun messagePrefixExcludeList(messagePrefixExcludeList: String) {
    it.property("messagePrefixExcludeList", messagePrefixExcludeList)
  }

  /**
   * A comma-separated list of regular expressions that match the logical decoding message prefixes
   * to be monitored. All prefixes are monitored by default.
   */
  public fun messagePrefixIncludeList(messagePrefixIncludeList: String) {
    it.property("messagePrefixIncludeList", messagePrefixIncludeList)
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
   * The name of the Postgres logical decoding plugin installed on the server. Supported values are
   * 'decoderbufs' and 'pgoutput'. Defaults to 'decoderbufs'.
   */
  public fun pluginName(pluginName: String) {
    it.property("pluginName", pluginName)
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
   * Applies only when streaming changes using pgoutput.Determine how creation of a publication
   * should work, the default is all_tables.DISABLED - The connector will not attempt to create a
   * publication at all. The expectation is that the user has created the publication up-front. If the
   * publication isn't found to exist upon startup, the connector will throw an exception and
   * stop.ALL_TABLES - If no publication exists, the connector will create a new publication for all
   * tables. Note this requires that the configured user has access. If the publication already exists,
   * it will be used. i.e CREATE PUBLICATION FOR ALL TABLES;FILTERED - If no publication exists, the
   * connector will create a new publication for all those tables matchingthe current filter
   * configuration (see table/database include/exclude list properties). If the publication already
   * exists, it will be used. i.e CREATE PUBLICATION FOR TABLE
   */
  public fun publicationAutocreateMode(publicationAutocreateMode: String) {
    it.property("publicationAutocreateMode", publicationAutocreateMode)
  }

  /**
   * The name of the Postgres 10 publication used for streaming changes from a plugin. Defaults to
   * 'dbz_publication'
   */
  public fun publicationName(publicationName: String) {
    it.property("publicationName", publicationName)
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
   * Applies only when streaming changes using pgoutput.Determines the value for Replica Identity at
   * table level. This option will overwrite the existing value in databaseA comma-separated list of
   * regular expressions that match fully-qualified tables and Replica Identity value to be used in the
   * table. Each expression must match the pattern ':', where the table names could be defined as
   * (SCHEMA_NAME.TABLE_NAME), and the replica identity values are: DEFAULT - Records the old values of
   * the columns of the primary key, if any. This is the default for non-system tables.INDEX
   * index_name - Records the old values of the columns covered by the named index, that must be
   * unique, not partial, not deferrable, and include only columns marked NOT NULL. If this index is
   * dropped, the behavior is the same as NOTHING.FULL - Records the old values of all columns in the
   * row.NOTHING - Records no information about the old row. This is the default for system tables.
   */
  public fun replicaIdentityAutosetValues(replicaIdentityAutosetValues: String) {
    it.property("replicaIdentityAutosetValues", replicaIdentityAutosetValues)
  }

  /**
   * Time to wait before restarting connector after retriable exception occurs. Defaults to 10000ms.
   */
  public fun retriableRestartConnectorWaitMs(retriableRestartConnectorWaitMs: String) {
    it.property("retriableRestartConnectorWaitMs", retriableRestartConnectorWaitMs)
  }

  /**
   * The schemas for which events must not be captured
   */
  public fun schemaExcludeList(schemaExcludeList: String) {
    it.property("schemaExcludeList", schemaExcludeList)
  }

  /**
   * The path to the file that will be used to record the database schema history
   */
  public fun schemaHistoryInternalFileFilename(schemaHistoryInternalFileFilename: String) {
    it.property("schemaHistoryInternalFileFilename", schemaHistoryInternalFileFilename)
  }

  /**
   * The schemas for which events should be captured
   */
  public fun schemaIncludeList(schemaIncludeList: String) {
    it.property("schemaIncludeList", schemaIncludeList)
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
   * Specify the conditions that trigger a refresh of the in-memory schema for a table.
   * 'columns_diff' (the default) is the safest mode, ensuring the in-memory schema stays in-sync with
   * the database table's schema at all times. 'columns_diff_exclude_unchanged_toast' instructs the
   * connector to refresh the in-memory schema cache if there is a discrepancy between it and the
   * schema derived from the incoming message, unless unchanged TOASTable data fully accounts for the
   * discrepancy. This setting can improve connector performance significantly if there are
   * frequently-updated tables that have TOASTed data that are rarely part of these updates. However,
   * it is possible for the in-memory schema to become outdated if TOASTable columns are dropped from
   * the table.
   */
  public fun schemaRefreshMode(schemaRefreshMode: String) {
    it.property("schemaRefreshMode", schemaRefreshMode)
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
   * Whether or not to drop the logical replication slot when the connector finishes orderly. By
   * default the replication is kept so that on restart progress can resume from the last recorded
   * location
   */
  public fun slotDropOnStop(slotDropOnStop: String) {
    it.property("slotDropOnStop", slotDropOnStop)
  }

  /**
   * Whether or not to drop the logical replication slot when the connector finishes orderly. By
   * default the replication is kept so that on restart progress can resume from the last recorded
   * location
   */
  public fun slotDropOnStop(slotDropOnStop: Boolean) {
    it.property("slotDropOnStop", slotDropOnStop.toString())
  }

  /**
   * How many times to retry connecting to a replication slot when an attempt fails.
   */
  public fun slotMaxRetries(slotMaxRetries: String) {
    it.property("slotMaxRetries", slotMaxRetries)
  }

  /**
   * How many times to retry connecting to a replication slot when an attempt fails.
   */
  public fun slotMaxRetries(slotMaxRetries: Int) {
    it.property("slotMaxRetries", slotMaxRetries.toString())
  }

  /**
   * The name of the Postgres logical decoding slot created for streaming changes from a plugin.
   * Defaults to 'debezium
   */
  public fun slotName(slotName: String) {
    it.property("slotName", slotName)
  }

  /**
   * Time to wait between retry attempts when the connector fails to connect to a replication slot,
   * given in milliseconds. Defaults to 10 seconds (10,000 ms).
   */
  public fun slotRetryDelayMs(slotRetryDelayMs: String) {
    it.property("slotRetryDelayMs", slotRetryDelayMs)
  }

  /**
   * Any optional parameters used by logical decoding plugin. Semi-colon separated. E.g.
   * 'add-tables=public.table,public.table2;include-lsn=true'
   */
  public fun slotStreamParams(slotStreamParams: String) {
    it.property("slotStreamParams", slotStreamParams)
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
   * 'shared' which means the connector will hold a table lock that prevents exclusive table access for
   * just the initial portion of the snapshot while the database schemas and other metadata are being
   * read. The remaining work in a snapshot involves selecting all rows from each table, and this is
   * done using a flashback query that requires no locks. However, in some cases it may be desirable to
   * avoid locks entirely which can be done by specifying 'none'. This mode is only safe to use if no
   * schema changes are happening while the snapshot is taken.
   */
  public fun snapshotLockingMode(snapshotLockingMode: String) {
    it.property("snapshotLockingMode", snapshotLockingMode)
  }

  /**
   * When 'snapshot.locking.mode' is set as custom, this setting must be set to specify a the name
   * of the custom implementation provided in the 'name()' method. The implementations must implement
   * the 'SnapshotterLocking' interface and is called to determine how to lock tables during schema
   * snapshot.
   */
  public fun snapshotLockingModeCustomName(snapshotLockingModeCustomName: String) {
    it.property("snapshotLockingModeCustomName", snapshotLockingModeCustomName)
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
   * snapshot completes, the connector begins to stream changes from the transaction log.; 'initial'
   * (default): If the connector does not detect any offsets for the logical server name, it runs a
   * snapshot that captures the current full state of the configured tables. After the snapshot
   * completes, the connector begins to stream changes from the transaction log. 'initial_only': The
   * connector performs a snapshot as it does for the 'initial' option, but after the connector
   * completes the snapshot, it stops, and does not stream changes from the transaction log.; 'never':
   * The connector does not run a snapshot. Upon first startup, the connector immediately begins
   * reading from the beginning of the transaction log. 'exported': This option is deprecated; use
   * 'initial' instead.; 'custom': The connector loads a custom class to specify how the connector
   * performs snapshots. For more information, see Custom snapshotter SPI in the PostgreSQL connector
   * documentation.
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
   * Controls query used during the snapshot
   */
  public fun snapshotQueryMode(snapshotQueryMode: String) {
    it.property("snapshotQueryMode", snapshotQueryMode)
  }

  /**
   * When 'snapshot.query.mode' is set as custom, this setting must be set to specify a the name of
   * the custom implementation provided in the 'name()' method. The implementations must implement the
   * 'SnapshotterQuery' interface and is called to determine how to build queries during snapshot.
   */
  public fun snapshotQueryModeCustomName(snapshotQueryModeCustomName: String) {
    it.property("snapshotQueryModeCustomName", snapshotQueryModeCustomName)
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
   * Frequency for sending replication connection status updates to the server, given in
   * milliseconds. Defaults to 10 seconds (10,000 ms).
   */
  public fun statusUpdateIntervalMs(statusUpdateIntervalMs: String) {
    it.property("statusUpdateIntervalMs", statusUpdateIntervalMs)
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
   * Flag specifying whether built-in tables should be ignored.
   */
  public fun tableIgnoreBuiltin(tableIgnoreBuiltin: String) {
    it.property("tableIgnoreBuiltin", tableIgnoreBuiltin)
  }

  /**
   * Flag specifying whether built-in tables should be ignored.
   */
  public fun tableIgnoreBuiltin(tableIgnoreBuiltin: Boolean) {
    it.property("tableIgnoreBuiltin", tableIgnoreBuiltin.toString())
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
   * Specify the constant that will be provided by Debezium to indicate that the original value is a
   * toasted value not provided by the database. If starts with 'hex:' prefix it is expected that the
   * rest of the string represents hexadecimal encoded octets.
   */
  public fun unavailableValuePlaceholder(unavailableValuePlaceholder: String) {
    it.property("unavailableValuePlaceholder", unavailableValuePlaceholder)
  }

  /**
   * Specify how often (in ms) the xmin will be fetched from the replication slot. This xmin value
   * is exposed by the slot which gives a lower bound of where a new replication slot could start from.
   * The lower the value, the more likely this value is to be the current 'true' value, but the bigger
   * the performance cost. The bigger the value, the less likely this value is to be the current 'true'
   * value, but the lower the performance penalty. The default is set to 0 ms, which disables tracking
   * xmin.
   */
  public fun xminFetchIntervalMs(xminFetchIntervalMs: String) {
    it.property("xminFetchIntervalMs", xminFetchIntervalMs)
  }
}
