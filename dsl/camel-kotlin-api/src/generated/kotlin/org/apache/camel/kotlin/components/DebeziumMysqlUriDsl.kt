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
 * Capture changes from a MySQL database.
 */
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
   * Specify how BIGINT UNSIGNED columns should be represented in change events, including:
   * 'precise' uses java.math.BigDecimal to represent values, which are encoded in the change events
   * using a binary representation and Kafka Connect's 'org.apache.kafka.connect.data.Decimal' type;
   * 'long' (the default) represents values using Java's 'long', which may not offer the precision but
   * will be far easier to use in consumers.
   */
  public fun bigintUnsignedHandlingMode(bigintUnsignedHandlingMode: String) {
    it.property("bigintUnsignedHandlingMode", bigintUnsignedHandlingMode)
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
   * The size of a look-ahead buffer used by the binlog reader to decide whether the transaction in
   * progress is going to be committed or rolled back. Use 0 to disable look-ahead buffering. Defaults
   * to 0 (i.e. buffering is disabled).
   */
  public fun binlogBufferSize(binlogBufferSize: String) {
    it.property("binlogBufferSize", binlogBufferSize)
  }

  /**
   * The size of a look-ahead buffer used by the binlog reader to decide whether the transaction in
   * progress is going to be committed or rolled back. Use 0 to disable look-ahead buffering. Defaults
   * to 0 (i.e. buffering is disabled).
   */
  public fun binlogBufferSize(binlogBufferSize: Int) {
    it.property("binlogBufferSize", binlogBufferSize.toString())
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
   * Whether a separate thread should be used to ensure the connection is kept alive.
   */
  public fun connectKeepAlive(connectKeepAlive: String) {
    it.property("connectKeepAlive", connectKeepAlive)
  }

  /**
   * Whether a separate thread should be used to ensure the connection is kept alive.
   */
  public fun connectKeepAlive(connectKeepAlive: Boolean) {
    it.property("connectKeepAlive", connectKeepAlive.toString())
  }

  /**
   * Interval for connection checking if keep alive thread is used, given in milliseconds Defaults
   * to 1 minute (60,000 ms).
   */
  public fun connectKeepAliveIntervalMs(connectKeepAliveIntervalMs: String) {
    it.property("connectKeepAliveIntervalMs", connectKeepAliveIntervalMs)
  }

  /**
   * Specifies the connection adapter to be used
   */
  public fun connectorAdapter(connectorAdapter: String) {
    it.property("connectorAdapter", connectorAdapter)
  }

  /**
   * Maximum time to wait after trying to connect to the database before timing out, given in
   * milliseconds. Defaults to 30 seconds (30,000 ms).
   */
  public fun connectTimeoutMs(connectTimeoutMs: String) {
    it.property("connectTimeoutMs", connectTimeoutMs)
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
   * A comma-separated list of regular expressions that match database names to be excluded from
   * monitoring
   */
  public fun databaseExcludeList(databaseExcludeList: String) {
    it.property("databaseExcludeList", databaseExcludeList)
  }

  /**
   * Resolvable hostname or IP address of the database server.
   */
  public fun databaseHostname(databaseHostname: String) {
    it.property("databaseHostname", databaseHostname)
  }

  /**
   * The databases for which changes are to be captured
   */
  public fun databaseIncludeList(databaseIncludeList: String) {
    it.property("databaseIncludeList", databaseIncludeList)
  }

  /**
   * A semicolon separated list of SQL statements to be executed when a JDBC connection (not binlog
   * reading connection) to the database is established. Note that the connector may establish JDBC
   * connections at its own discretion, so this should typically be used for configuration of session
   * parameters only, but not for executing DML statements. Use doubled semicolon (';;') to use a
   * semicolon as a character and not as a delimiter.
   */
  public fun databaseInitialStatements(databaseInitialStatements: String) {
    it.property("databaseInitialStatements", databaseInitialStatements)
  }

  /**
   * JDBC Driver class name used to connect to the MySQL database server.
   */
  public fun databaseJdbcDriver(databaseJdbcDriver: String) {
    it.property("databaseJdbcDriver", databaseJdbcDriver)
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
   * A numeric ID of this database client, which must be unique across all currently-running
   * database processes in the cluster. This connector joins the MySQL database cluster as another
   * server (with this unique ID) so it can read the binlog.
   */
  public fun databaseServerId(databaseServerId: String) {
    it.property("databaseServerId", databaseServerId)
  }

  /**
   * A numeric ID of this database client, which must be unique across all currently-running
   * database processes in the cluster. This connector joins the MySQL database cluster as another
   * server (with this unique ID) so it can read the binlog.
   */
  public fun databaseServerId(databaseServerId: Int) {
    it.property("databaseServerId", databaseServerId.toString())
  }

  /**
   * Only relevant if parallel snapshotting is configured. During parallel snapshotting, multiple
   * (4) connections open to the database client, and they each need their own unique connection ID.
   * This offset is used to generate those IDs from the base configured cluster ID.
   */
  public fun databaseServerIdOffset(databaseServerIdOffset: String) {
    it.property("databaseServerIdOffset", databaseServerIdOffset)
  }

  /**
   * Only relevant if parallel snapshotting is configured. During parallel snapshotting, multiple
   * (4) connections open to the database client, and they each need their own unique connection ID.
   * This offset is used to generate those IDs from the base configured cluster ID.
   */
  public fun databaseServerIdOffset(databaseServerIdOffset: Int) {
    it.property("databaseServerIdOffset", databaseServerIdOffset.toString())
  }

  /**
   * The location of the key store file. This is optional and can be used for two-way authentication
   * between the client and the MySQL Server.
   */
  public fun databaseSslKeystore(databaseSslKeystore: String) {
    it.property("databaseSslKeystore", databaseSslKeystore)
  }

  /**
   * The password for the key store file. This is optional and only needed if
   * 'database.ssl.keystore' is configured.
   */
  public fun databaseSslKeystorePassword(databaseSslKeystorePassword: String) {
    it.property("databaseSslKeystorePassword", databaseSslKeystorePassword)
  }

  /**
   * Whether to use an encrypted connection to MySQL. Options include: 'disabled' to use an
   * unencrypted connection; 'preferred' (the default) to establish a secure (encrypted) connection if
   * the server supports secure connections, but fall back to an unencrypted connection otherwise;
   * 'required' to use a secure (encrypted) connection, and fail if one cannot be established;
   * 'verify_ca' like 'required' but additionally verify the server TLS certificate against the
   * configured Certificate Authority (CA) certificates, or fail if no valid matching CA certificates
   * are found; or'verify_identity' like 'verify_ca' but additionally verify that the server
   * certificate matches the host to which the connection is attempted.
   */
  public fun databaseSslMode(databaseSslMode: String) {
    it.property("databaseSslMode", databaseSslMode)
  }

  /**
   * The location of the trust store file for the server certificate verification.
   */
  public fun databaseSslTruststore(databaseSslTruststore: String) {
    it.property("databaseSslTruststore", databaseSslTruststore)
  }

  /**
   * The password for the trust store file. Used to check the integrity of the truststore, and
   * unlock the truststore.
   */
  public fun databaseSslTruststorePassword(databaseSslTruststorePassword: String) {
    it.property("databaseSslTruststorePassword", databaseSslTruststorePassword)
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
   * MySQL allows user to insert year value as either 2-digit or 4-digit. In case of two digit the
   * value is automatically mapped into 1970 - 2069.false - delegates the implicit conversion to the
   * databasetrue - (the default) Debezium makes the conversion
   */
  public fun enableTimeAdjuster(enableTimeAdjuster: String) {
    it.property("enableTimeAdjuster", enableTimeAdjuster)
  }

  /**
   * MySQL allows user to insert year value as either 2-digit or 4-digit. In case of two digit the
   * value is automatically mapped into 1970 - 2069.false - delegates the implicit conversion to the
   * databasetrue - (the default) Debezium makes the conversion
   */
  public fun enableTimeAdjuster(enableTimeAdjuster: Boolean) {
    it.property("enableTimeAdjuster", enableTimeAdjuster.toString())
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
   * Specify how failures during deserialization of binlog events (i.e. when encountering a
   * corrupted event) should be handled, including: 'fail' (the default) an exception indicating the
   * problematic event and its binlog position is raised, causing the connector to be stopped; 'warn'
   * the problematic event and its binlog position will be logged and the event will be skipped;
   * 'ignore' the problematic event will be skipped.
   */
  public
      fun eventDeserializationFailureHandlingMode(eventDeserializationFailureHandlingMode: String) {
    it.property("eventDeserializationFailureHandlingMode", eventDeserializationFailureHandlingMode)
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
   * The source UUIDs used to exclude GTID ranges when determine the starting position in the MySQL
   * server's binlog.
   */
  public fun gtidSourceExcludes(gtidSourceExcludes: String) {
    it.property("gtidSourceExcludes", gtidSourceExcludes)
  }

  /**
   * If set to true, we will only produce DML events into Kafka for transactions that were written
   * on mysql servers with UUIDs matching the filters defined by the gtid.source.includes or
   * gtid.source.excludes configuration options, if they are specified.
   */
  public fun gtidSourceFilterDmlEvents(gtidSourceFilterDmlEvents: String) {
    it.property("gtidSourceFilterDmlEvents", gtidSourceFilterDmlEvents)
  }

  /**
   * If set to true, we will only produce DML events into Kafka for transactions that were written
   * on mysql servers with UUIDs matching the filters defined by the gtid.source.includes or
   * gtid.source.excludes configuration options, if they are specified.
   */
  public fun gtidSourceFilterDmlEvents(gtidSourceFilterDmlEvents: Boolean) {
    it.property("gtidSourceFilterDmlEvents", gtidSourceFilterDmlEvents.toString())
  }

  /**
   * The source UUIDs used to include GTID ranges when determine the starting position in the MySQL
   * server's binlog.
   */
  public fun gtidSourceIncludes(gtidSourceIncludes: String) {
    it.property("gtidSourceIncludes", gtidSourceIncludes)
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
   * Whether the connector should include the original SQL query that generated the change event.
   * Note: This option requires MySQL be configured with the binlog_rows_query_log_events option set to
   * ON. If using MariaDB, configure the binlog_annotate_row_events option must be set to ON. Query
   * will not be present for events generated from snapshot. WARNING: Enabling this option may expose
   * tables or fields explicitly excluded or masked by including the original SQL statement in the
   * change event. For this reason the default value is 'false'.
   */
  public fun includeQuery(includeQuery: String) {
    it.property("includeQuery", includeQuery)
  }

  /**
   * Whether the connector should include the original SQL query that generated the change event.
   * Note: This option requires MySQL be configured with the binlog_rows_query_log_events option set to
   * ON. If using MariaDB, configure the binlog_annotate_row_events option must be set to ON. Query
   * will not be present for events generated from snapshot. WARNING: Enabling this option may expose
   * tables or fields explicitly excluded or masked by including the original SQL statement in the
   * change event. For this reason the default value is 'false'.
   */
  public fun includeQuery(includeQuery: Boolean) {
    it.property("includeQuery", includeQuery.toString())
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
   * Specify how binlog events that belong to a table missing from internal schema representation
   * (i.e. internal representation is not consistent with database) should be handled, including:
   * 'fail' (the default) an exception indicating the problematic event and its binlog position is
   * raised, causing the connector to be stopped; 'warn' the problematic event and its binlog position
   * will be logged and the event will be skipped; 'skip' the problematic event will be skipped.
   */
  public fun inconsistentSchemaHandlingMode(inconsistentSchemaHandlingMode: String) {
    it.property("inconsistentSchemaHandlingMode", inconsistentSchemaHandlingMode)
  }

  /**
   * Detect schema change during an incremental snapshot and re-select a current chunk to avoid
   * locking DDLs. Note that changes to a primary key are not supported and can cause incorrect results
   * if performed during an incremental snapshot. Another limitation is that if a schema change affects
   * only columns' default values, then the change won't be detected until the DDL is processed from
   * the binlog stream. This doesn't affect the snapshot events' values, but the schema of snapshot
   * events may have outdated defaults.
   */
  public fun incrementalSnapshotAllowSchemaChanges(incrementalSnapshotAllowSchemaChanges: String) {
    it.property("incrementalSnapshotAllowSchemaChanges", incrementalSnapshotAllowSchemaChanges)
  }

  /**
   * Detect schema change during an incremental snapshot and re-select a current chunk to avoid
   * locking DDLs. Note that changes to a primary key are not supported and can cause incorrect results
   * if performed during an incremental snapshot. Another limitation is that if a schema change affects
   * only columns' default values, then the change won't be detected until the DDL is processed from
   * the binlog stream. This doesn't affect the snapshot events' values, but the schema of snapshot
   * events may have outdated defaults.
   */
  public fun incrementalSnapshotAllowSchemaChanges(incrementalSnapshotAllowSchemaChanges: Boolean) {
    it.property("incrementalSnapshotAllowSchemaChanges",
        incrementalSnapshotAllowSchemaChanges.toString())
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
   * The number of rows a table must contain to stream results rather than pull all into memory
   * during snapshots. Defaults to 1,000. Use 0 to stream all results and completely avoid checking the
   * size of each table.
   */
  public fun minRowCountToStreamResults(minRowCountToStreamResults: String) {
    it.property("minRowCountToStreamResults", minRowCountToStreamResults)
  }

  /**
   * The number of rows a table must contain to stream results rather than pull all into memory
   * during snapshots. Defaults to 1,000. Use 0 to stream all results and completely avoid checking the
   * size of each table.
   */
  public fun minRowCountToStreamResults(minRowCountToStreamResults: Int) {
    it.property("minRowCountToStreamResults", minRowCountToStreamResults.toString())
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
   * Controls how long the connector holds onto the global read lock while it is performing a
   * snapshot. The default is 'minimal', which means the connector holds the global read lock (and thus
   * prevents any updates) for just the initial portion of the snapshot while the database schemas and
   * other metadata are being read. The remaining work in a snapshot involves selecting all rows from
   * each table, and this can be done using the snapshot process' REPEATABLE READ transaction even when
   * the lock is no longer held and other operations are updating the database. However, in some cases
   * it may be desirable to block all writes for the entire duration of the snapshot; in such cases set
   * this property to 'extended'. Using a value of 'none' will prevent the connector from acquiring any
   * table locks during the snapshot process. This mode can only be used in combination with
   * snapshot.mode values of 'schema_only' or 'schema_only_recovery' and is only safe to use if no
   * schema changes are happening while the snapshot is taken.
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
   * snapshot options: 'when_needed': On startup, the connector runs a snapshot if one is needed.;
   * 'schema_only': If the connector does not detect any offsets for the logical server name, it runs a
   * snapshot that captures only the schema (table structures), but not any table data. After the
   * snapshot completes, the connector begins to stream changes from the binlog.;
   * 'schema_only_recovery': The connector performs a snapshot that captures only the database schema
   * history. The connector then transitions back to streaming. Use this setting to restore a corrupted
   * or lost database schema history topic. Do not use if the database schema was modified after the
   * connector stopped.; 'initial' (default): If the connector does not detect any offsets for the
   * logical server name, it runs a snapshot that captures the current full state of the configured
   * tables. After the snapshot completes, the connector begins to stream changes from the binlog.;
   * 'initial_only': The connector performs a snapshot as it does for the 'initial' option, but after
   * the connector completes the snapshot, it stops, and does not stream changes from the binlog.;
   * 'never': The connector does not run a snapshot. Upon first startup, the connector immediately
   * begins reading from the beginning of the binlog. The 'never' mode should be used with care, and
   * only when the binlog is known to contain all history.
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
   * BETA FEATURE: On connector restart, the connector will check if there have been any new tables
   * added to the configuration, and snapshot them. There is presently only two options: 'off': Default
   * behavior. Do not snapshot new tables. 'parallel': The snapshot of the new tables will occur in
   * parallel to the continued binlog reading of the old tables. When the snapshot completes, an
   * independent binlog reader will begin reading the events for the new tables until it catches up to
   * present time. At this point, both old and new binlog readers will be momentarily halted and new
   * binlog reader will start that will read the binlog for all configured tables. The parallel binlog
   * reader will have a configured server id of 10000 the primary binlog reader's server id.
   */
  public fun snapshotNewTables(snapshotNewTables: String) {
    it.property("snapshotNewTables", snapshotNewTables)
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
   * Time, date and timestamps can be represented with different kinds of precisions, including:
   * 'adaptive_time_microseconds': the precision of date and timestamp values is based the database
   * column's precision; but time fields always use microseconds precision; 'connect': always
   * represents time, date and timestamp values using Kafka Connect's built-in representations for
   * Time, Date, and Timestamp, which uses millisecond precision regardless of the database columns'
   * precision.
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
}
