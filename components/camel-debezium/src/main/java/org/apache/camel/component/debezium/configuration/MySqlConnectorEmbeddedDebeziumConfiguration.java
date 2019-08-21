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
package org.apache.camel.component.debezium.configuration;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.relational.history.FileDatabaseHistory;
import io.debezium.relational.history.KafkaDatabaseHistory;

import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class MySqlConnectorEmbeddedDebeziumConfiguration extends EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,mysql";

    // database.hostname
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseHostName;
    // database.port
    @UriParam(label = LABEL_NAME, defaultValue = "3306")
    private int databasePort = 3306;
    // database.user
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseUser;
    // database.password
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true, secret = true)
    private String databasePassword;
    // database.server.name
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseServerName;
    // database.server.id
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private int databaseServerId;
    // database.history
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.relational.history.FileDatabaseHistory")
    private String databaseHistory = DebeziumConstants.DEFAULT_DATABASE_HISTORY;
    // database.history.filename
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileName;
    // database.history.kafka.topic
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaTopic;
    // database.history​.kafka.bootstrap.servers
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaBootstrapServers;
    // database.whitelist
    @UriParam(label = LABEL_NAME)
    private String databaseWhitelist;
    // database.blacklist
    @UriParam(label = LABEL_NAME)
    private String databaseBlacklist;
    // table.whitelist
    @UriParam(label = LABEL_NAME)
    private String tableWhitelist;
    // table.blacklist
    @UriParam(label = LABEL_NAME)
    private String tableBlacklist;
    // column.blacklist
    @UriParam(label = LABEL_NAME)
    private String columnBlacklist;
    // time.precision.mode
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive_time_microseconds")
    private String timePrecisionMode = "adaptive_time_microseconds";
    // decimal.handling.mode
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    // bigint.unsigned.handling.mode
    @UriParam(label = LABEL_NAME, defaultValue = "long")
    private String bigintUnsignedHandlingMode = "long";
    // include.schema.changes
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    // include.query
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeQuery;
    // event.deserialization​.failure.handling.mode
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventDeserializationFailureHandlingMode = "fail";
    // inconsistent.schema.handling.mode
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String inconsistentSchemaHandlingMode = "fail";
    // max.queue.size
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    // max.batch.size
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    // poll.interval.ms
    @UriParam(label = LABEL_NAME, defaultValue = "1000")
    private long pollIntervalMs = 1000;
    // connect.timeout.ms
    @UriParam(label = LABEL_NAME, defaultValue = "30000")
    private long connectTimeoutMs = 30000;
    // gtid.source.includes
    @UriParam(label = LABEL_NAME)
    private String gtidSourceIncludes;
    // gtid.source.excludes
    @UriParam(label = LABEL_NAME)
    private String gtidSourceExcludes;
    // gtid.new.channel.position
    @UriParam(label = LABEL_NAME, defaultValue = "latest")
    private String gtidNewChannelPosition = "latest";
    // tombstones.on.delete
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete;
    // ddl.parser.mode
    @UriParam(label = LABEL_NAME, defaultValue = "antlr")
    private String ddlParserMode = "antlr";

    @Override
    protected Configuration createConnectorConfiguration() {
        return createDebeziumMySqlConnectorConfiguration();
    }

    private Configuration createDebeziumMySqlConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();

        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.HOSTNAME, databaseHostName);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.PORT, databasePort);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.USER, databaseUser);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.PASSWORD, databasePassword);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.SERVER_ID, databaseServerId);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.SERVER_NAME, databaseServerName);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DATABASE_WHITELIST, databaseWhitelist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DATABASE_HISTORY, databaseHistory);
        addPropertyIfNotNull(configBuilder, FileDatabaseHistory.FILE_PATH, databaseHistoryFileName);
        addPropertyIfNotNull(configBuilder, KafkaDatabaseHistory.TOPIC, databaseHistoryKafkaTopic);
        addPropertyIfNotNull(configBuilder, KafkaDatabaseHistory.BOOTSTRAP_SERVERS,
                             databaseHistoryKafkaBootstrapServers);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DATABASE_WHITELIST, databaseWhitelist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DATABASE_BLACKLIST, databaseBlacklist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.TABLE_WHITELIST, tableWhitelist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.TABLE_BLACKLIST, tableBlacklist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.COLUMN_BLACKLIST, columnBlacklist);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.TIME_PRECISION_MODE, timePrecisionMode);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DECIMAL_HANDLING_MODE, decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.BIGINT_UNSIGNED_HANDLING_MODE,
                             bigintUnsignedHandlingMode);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.INCLUDE_SCHEMA_CHANGES,
                             includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.INCLUDE_SQL_QUERY, includeQuery);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.EVENT_DESERIALIZATION_FAILURE_HANDLING_MODE,
                             eventDeserializationFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.INCONSISTENT_SCHEMA_HANDLING_MODE,
                             inconsistentSchemaHandlingMode);
        addPropertyIfNotNull(configBuilder, CommonConnectorConfig.MAX_QUEUE_SIZE, maxQueueSize);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.MAX_BATCH_SIZE, maxBatchSize);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.POLL_INTERVAL_MS, pollIntervalMs);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.CONNECTION_TIMEOUT_MS, connectTimeoutMs);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.GTID_SOURCE_INCLUDES, gtidSourceIncludes);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.GTID_SOURCE_EXCLUDES, gtidSourceExcludes);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.GTID_NEW_CHANNEL_POSITION,
                             gtidNewChannelPosition);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.TOMBSTONES_ON_DELETE, tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, MySqlConnectorConfig.DDL_PARSER_MODE, ddlParserMode);

        return configBuilder.build();
    }

    @Override
    protected ConfigurationValidation validateConnectorConfiguration() {
        return validateMySqlConnectorConfiguration();
    }

    private ConfigurationValidation validateMySqlConnectorConfiguration() {
        if (isFieldValueNotSet(databasePassword)) {
            return ConfigurationValidation.notValid("Required field 'databasePassword' must be set.");
        }
        if (isFieldValueNotSet(databaseServerId)) {
            return ConfigurationValidation.notValid("Required field 'databaseServerId' must be set.");
        }
        if (isFieldValueNotSet(databaseServerName)) {
            return ConfigurationValidation.notValid("Required field 'databaseServerName' must be set.");
        }
        // check for databaseHistory
        if (databaseHistory.equals(DebeziumConstants.DEFAULT_DATABASE_HISTORY)
            && isFieldValueNotSet(databaseHistoryFileName)) {
            return ConfigurationValidation.notValid(String
                .format("Required field 'databaseHistoryFileName' must be set since 'databaseHistory' is set to '%s'",
                        DebeziumConstants.DEFAULT_DATABASE_HISTORY));
        }
        return ConfigurationValidation.valid();
    }

    @Override
    protected Class<?> configureConnectorClass() {
        return MySqlConnector.class;
    }

    /**
     * IP address or hostname of the target database server.
     */
    public String getDatabaseHostName() {
        return databaseHostName;
    }

    public void setDatabaseHostName(String databaseHostName) {
        this.databaseHostName = databaseHostName;
    }

    /**
     * Integer port number of the database server.
     */
    public int getDatabasePort() {
        return databasePort;
    }

    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    /**
     * Name of the MySQL database to use when connecting to the database server.
     */
    public String getDatabaseUser() {
        return databaseUser;
    }

    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    /**
     * Password to use when connecting to the database server.
     */
    public String getDatabasePassword() {
        return databasePassword;
    }

    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    /**
     * Logical name that identifies and provides a namespace for the particular
     * database server/cluster being monitored.
     */
    public String getDatabaseServerName() {
        return databaseServerName;
    }

    public void setDatabaseServerName(String databaseServerName) {
        this.databaseServerName = databaseServerName;
    }

    /**
     * A numeric ID of this database client, which must be unique across all
     * currently-running database processes in the database cluster. This connector
     * joins the database cluster as another server (with this unique ID) so it can
     * read the binlog.
     */
    public int getDatabaseServerId() {
        return databaseServerId;
    }

    public void setDatabaseServerId(int databaseServerId) {
        this.databaseServerId = databaseServerId;
    }

    /**
     * The full name of the Kafka topic where the connector will store the database
     * schema history.
     */
    public String getDatabaseHistoryKafkaTopic() {
        return databaseHistoryKafkaTopic;
    }

    public void setDatabaseHistoryKafkaTopic(String databaseHistoryKafkaTopic) {
        this.databaseHistoryKafkaTopic = databaseHistoryKafkaTopic;
    }

    /**
     * An optional comma-separated list of regular expressions that match database
     * names to be monitored; any database name not included in the whitelist will
     * be excluded from monitoring. By default all databases will be monitored. May
     * not be used with database.blacklist.
     */
    public String getDatabaseWhitelist() {
        return databaseWhitelist;
    }

    public void setDatabaseWhitelist(String databaseWhitelist) {
        this.databaseWhitelist = databaseWhitelist;
    }

    /**
     * The name of the DatabaseHistory class that should be used to store and
     * recover database schema changes.
     */
    public String getDatabaseHistory() {
        return databaseHistory;
    }

    public void setDatabaseHistory(String databaseHistory) {
        this.databaseHistory = databaseHistory;
    }

    /**
     * The path to the file that will be used to record the database history
     */
    public String getDatabaseHistoryFileName() {
        return databaseHistoryFileName;
    }

    public void setDatabaseHistoryFileName(String databaseHistoryFileName) {
        this.databaseHistoryFileName = databaseHistoryFileName;
    }

    /**
     * The full name of the Kafka topic where the connector will store the database
     * schema history.
     */
    public String getDatabaseHistoryKafkaBootstrapServers() {
        return databaseHistoryKafkaBootstrapServers;
    }

    public void setDatabaseHistoryKafkaBootstrapServers(String databaseHistoryKafkaBootstrapServers) {
        this.databaseHistoryKafkaBootstrapServers = databaseHistoryKafkaBootstrapServers;
    }

    /**
     * An optional comma-separated list of regular expressions that match database
     * names to be excluded from monitoring; any database name not included in the
     * blacklist will be monitored. May not be used with database.whitelist.
     */

    public String getDatabaseBlacklist() {
        return databaseBlacklist;
    }

    public void setDatabaseBlacklist(String databaseBlacklist) {
        this.databaseBlacklist = databaseBlacklist;
    }

    /**
     * An optional comma-separated list of regular expressions that match
     * fully-qualified table identifiers for tables to be monitored; any table not
     * included in the whitelist will be excluded from monitoring. Each identifier
     * is of the form databaseName.tableName. By default the connector will monitor
     * every non-system table in each monitored database. May not be used with
     * table.blacklist.
     */
    public String getTableWhitelist() {
        return tableWhitelist;
    }

    public void setTableWhitelist(String tableWhitelist) {
        this.tableWhitelist = tableWhitelist;
    }

    /**
     * An optional comma-separated list of regular expressions that match
     * fully-qualified table identifiers for tables to be excluded from monitoring;
     * any table not included in the blacklist will be monitored. Each identifier is
     * of the form databaseName.tableName. May not be used with table.whitelist.
     */
    public String getTableBlacklist() {
        return tableBlacklist;
    }

    public void setTableBlacklist(String tableBlacklist) {
        this.tableBlacklist = tableBlacklist;
    }

    /**
     * An optional comma-separated list of regular expressions that match the
     * fully-qualified names of columns that should be excluded from change event
     * message values. Fully-qualified names for columns are of the form
     * databaseName.tableName.columnName, or
     * databaseName.schemaName.tableName.columnName.
     */
    public String getColumnBlacklist() {
        return columnBlacklist;
    }

    public void setColumnBlacklist(String columnBlacklist) {
        this.columnBlacklist = columnBlacklist;
    }

    /**
     * Time, date, and timestamps can be represented with different kinds of
     * precision, including: adaptive_time_microseconds (the default) captures the
     * date, datetime and timestamp values exactly as in the database using either
     * millisecond, microsecond, or nanosecond precision values based on the
     * database column’s type, with the exception of TIME type fields, which are
     * always captured as microseconds; adaptive (deprecated) captures the time and
     * timestamp values exactly as in the database using either millisecond,
     * microsecond, or nanosecond precision values based on the database column’s
     * type; or connect always represents time and timestamp values using Kafka
     * Connect’s built-in representations for Time, Date, and Timestamp, which uses
     * millisecond precision regardless of the database columns' precision. See
     * Temporal values.
     */
    public String getTimePrecisionMode() {
        return timePrecisionMode;
    }

    public void setTimePrecisionMode(String timePrecisionMode) {
        this.timePrecisionMode = timePrecisionMode;
    }

    /**
     * Specifies how the connector should handle values for DECIMAL and NUMERIC
     * columns: precise (the default) represents them precisely using
     * java.math.BigDecimal values represented in change events in a binary form; or
     * double represents them using double values, which may result in a loss of
     * precision but will be far easier to use. string option encodes values as
     * formatted string which is easy to consume but a semantic information about
     * the real type is lost. See Decimal values.
     */
    public String getDecimalHandlingMode() {
        return decimalHandlingMode;
    }

    public void setDecimalHandlingMode(String decimalHandlingMode) {
        this.decimalHandlingMode = decimalHandlingMode;
    }

    /**
     * Specifies how BIGINT UNSIGNED columns should be represented in change events,
     * including: precise uses java.math.BigDecimal to represent values, which are
     * encoded in the change events using a binary representation and Kafka
     * Connect’s org.apache.kafka.connect.data.Decimal type; long (the default)
     * represents values using Java’s long, which may not offer the precision but
     * will be far easier to use in consumers. long is usually the preferable
     * setting. Only when working with values larger than 2^63, the precise setting
     * should be used as those values can’t be conveyed using long. See Data types.
     */
    public String getBigintUnsignedHandlingMode() {
        return bigintUnsignedHandlingMode;
    }

    public void setBigintUnsignedHandlingMode(String bigintUnsignedHandlingMode) {
        this.bigintUnsignedHandlingMode = bigintUnsignedHandlingMode;
    }

    /**
     * Boolean value that specifies whether the connector should publish changes in
     * the database schema to a Kafka topic with the same name as the database
     * server ID. Each schema change will be recorded using a key that contains the
     * database name and whose value includes the DDL statement(s). This is
     * independent of how the connector internally records database history. The
     * default is true.
     */
    public boolean isIncludeSchemaChanges() {
        return includeSchemaChanges;
    }

    public void setIncludeSchemaChanges(boolean includeSchemaChanges) {
        this.includeSchemaChanges = includeSchemaChanges;
    }

    /**
     * Boolean value that specifies whether the connector should include the
     * original SQL query that generated the change event. Note: This option
     * requires MySQL be configured with the binlog_rows_query_log_events option set
     * to ON. Query will not be present for events generated from the snapshot
     * process. Warning: Enabling this option may expose tables or fields explicitly
     * blacklisted or masked by including the original SQL statement in the change
     * event. For this reason this option is defaulted to 'false'.
     */
    public boolean isIncludeQuery() {
        return includeQuery;
    }

    public void setIncludeQuery(boolean includeQuery) {
        this.includeQuery = includeQuery;
    }

    /**
     * Specifies how the connector should react to exceptions during deserialization
     * of binlog events. fail will propagate the exception (indicating the
     * problematic event and its binlog offset), causing the connector to stop. warn
     * will cause the problematic event to be skipped and the problematic event and
     * its binlog offset to be logged (make sure that the logger is set to the WARN
     * or ERROR level). ignore will cause problematic event will be skipped.
     */
    public String getEventDeserializationFailureHandlingMode() {
        return eventDeserializationFailureHandlingMode;
    }

    public void setEventDeserializationFailureHandlingMode(String eventDeserializationFailureHandlingMode) {
        this.eventDeserializationFailureHandlingMode = eventDeserializationFailureHandlingMode;
    }

    /**
     * Specifies how the connector should react to binlog events that relate to
     * tables that are not present in internal schema representation (i.e. internal
     * representation is not consistent with database) fail will throw an exception
     * (indicating the problematic event and its binlog offset), causing the
     * connector to stop. warn will cause the problematic event to be skipped and
     * the problematic event and its binlog offset to be logged (make sure that the
     * logger is set to the WARN or ERROR level). ignore will cause the problematic
     * event to be skipped.
     */
    public String getInconsistentSchemaHandlingMode() {
        return inconsistentSchemaHandlingMode;
    }

    public void setInconsistentSchemaHandlingMode(String inconsistentSchemaHandlingMode) {
        this.inconsistentSchemaHandlingMode = inconsistentSchemaHandlingMode;
    }

    /**
     * Positive integer value that specifies the maximum size of the blocking queue
     * into which change events read from the database log are placed before they
     * are written to Kafka. This queue can provide backpressure to the binlog
     * reader when, for example, writes to Kafka are slower or if Kafka is not
     * available. Events that appear in the queue are not included in the offsets
     * periodically recorded by this connector. Defaults to 8192, and should always
     * be larger than the maximum batch size specified in the max.batch.size
     * property.
     */
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Positive integer value that specifies the maximum size of each batch of
     * events that should be processed during each iteration of this connector.
     * Defaults to 2048.
     */
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * Positive integer value that specifies the number of milliseconds the
     * connector should wait during each iteration for new change events to appear.
     * Defaults to 1000 milliseconds, or 1 second.
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * A positive integer value that specifies the maximum time in milliseconds this
     * connector should wait after trying to connect to the MySQL database server
     * before timing out. Defaults to 30 seconds.
     */
    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * A comma-separated list of regular expressions that match source UUIDs in the
     * GTID set used to find the binlog position in the MySQL server. Only the GTID
     * ranges that have sources matching one of these include patterns will be used.
     * May not be used with gtid.source.excludes.
     */
    public String getGtidSourceIncludes() {
        return gtidSourceIncludes;
    }

    public void setGtidSourceIncludes(String gtidSourceIncludes) {
        this.gtidSourceIncludes = gtidSourceIncludes;
    }

    /**
     * A comma-separated list of regular expressions that match source UUIDs in the
     * GTID set used to find the binlog position in the MySQL server. Only the GTID
     * ranges that have sources matching none of these exclude patterns will be
     * used. May not be used with gtid.source.includes.
     */
    public String getGtidSourceExcludes() {
        return gtidSourceExcludes;
    }

    public void setGtidSourceExcludes(String gtidSourceExcludes) {
        this.gtidSourceExcludes = gtidSourceExcludes;
    }

    /**
     * When set to latest, when the connector sees a new GTID channel, it will start
     * consuming from the last executed transaction in that GTID channel. If set to
     * earliest, the connector starts reading that channel from the first available
     * (not purged) GTID position. earliest is useful when you have a active-passive
     * MySQL setup where Debezium is connected to master, in this case during
     * failover the slave with new UUID (and GTID channel) starts receiving writes
     * before Debezium is connected. These writes would be lost when using latest.
     */
    public String getGtidNewChannelPosition() {
        return gtidNewChannelPosition;
    }

    public void setGtidNewChannelPosition(String gtidNewChannelPosition) {
        this.gtidNewChannelPosition = gtidNewChannelPosition;
    }

    /**
     * Controls whether a tombstone event should be generated after a delete event.
     * When true the delete operations are represented by a delete event and a
     * subsequent tombstone event. When false only a delete event is sent. Emitting
     * the tombstone event (the default behavior) allows Kafka to completely delete
     * all events pertaining to the given key once the source record got deleted.
     */
    public boolean isTombstonesOnDelete() {
        return tombstonesOnDelete;
    }

    public void setTombstonesOnDelete(boolean tombstonesOnDelete) {
        this.tombstonesOnDelete = tombstonesOnDelete;
    }

    /**
     * Controls which parser should be used for parsing DDL statements when building
     * up the meta-model of the captured database structure. Can be one of legacy
     * (for the legacy hand-written parser implementation) or antlr (for new Antlr
     * based implementation introduced in Debezium 0.8.0). While the legacy parser
     * remains the default for Debezium 0.8.x, please try out the new implementation
     * and report back any issues you encounter. The new parser is the default as of
     * 0.9. The legacy parser as well as this configuration property has been
     * removed as of 0.10.
     */
    public String getDdlParserMode() {
        return ddlParserMode;
    }

    public void setDdlParserMode(String ddlParserMode) {
        this.ddlParserMode = ddlParserMode;
    }
}
