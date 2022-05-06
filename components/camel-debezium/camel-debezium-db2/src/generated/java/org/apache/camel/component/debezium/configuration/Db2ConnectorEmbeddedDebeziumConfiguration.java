package org.apache.camel.component.debezium.configuration;

import io.debezium.config.Configuration;
import io.debezium.connector.db2.Db2Connector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class Db2ConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,db2";
    @UriParam(label = LABEL_NAME)
    private String messageKeyColumns;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int queryFetchSize = 0;
    @UriParam(label = LABEL_NAME)
    private String columnBlacklist;
    @UriParam(label = LABEL_NAME)
    private String tableBlacklist;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME, defaultValue = "100ms", javaType = "java.time.Duration")
    private int databaseHistoryKafkaRecoveryPollIntervalMs = 100;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean databaseHistoryStoreOnlyCapturedTablesDdl = false;
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "3s", javaType = "java.time.Duration")
    private long databaseHistoryKafkaQueryTimeoutMs = 3000;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String databaseDbname;
    @UriParam(label = LABEL_NAME)
    private String datatypePropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean sanitizeFieldNames = false;
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaBootstrapServers;
    @UriParam(label = LABEL_NAME, defaultValue = "v2")
    private String sourceStructVersion = "v2";
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    private String columnWhitelist;
    @UriParam(label = LABEL_NAME)
    private String columnIncludeList;
    @UriParam(label = LABEL_NAME)
    private String columnPropagateSourceType;
    @UriParam(label = LABEL_NAME)
    private String tableExcludeList;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean databaseHistoryStoreOnlyMonitoredTablesDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME)
    private String skippedOperations;
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.relational.history.KafkaDatabaseHistory")
    private String databaseHistory = "io.debezium.relational.history.KafkaDatabaseHistory";
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME, defaultValue = "1024")
    private int incrementalSnapshotChunkSize = 1024;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaTopic;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "100")
    private int databaseHistoryKafkaRecoveryAttempts = 100;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME)
    private String tableWhitelist;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean databaseHistorySkipUnparseableDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "${database.server.name}.transaction")
    private String transactionTopic = "${database.server.name}.transaction";
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive")
    private String timePrecisionMode = "adaptive";
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseServerName;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "50000")
    private int databasePort = 50000;
    @UriParam(label = LABEL_NAME)
    private String columnExcludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "avro")
    private String schemaNameAdjustmentMode = "avro";
    @UriParam(label = LABEL_NAME)
    private String tableIncludeList;

    /**
     * A semicolon-separated list of expressions that match fully-qualified
     * tables and column(s) to be used as message key. Each expression must
     * match the pattern '<fully-qualified table name>:<key columns>',where the
     * table names could be defined as (DB_NAME.TABLE_NAME) or
     * (SCHEMA_NAME.TABLE_NAME), depending on the specific connector,and the key
     * columns are a comma-separated list of columns representing the custom
     * key. For any table without an explicit key configuration the table's
     * primary key column(s) will be used as message key.Example:
     * dbserver1.inventory.orderlines:orderId,orderLineId;dbserver1.inventory.orders:id
     */
    public void setMessageKeyColumns(String messageKeyColumns) {
        this.messageKeyColumns = messageKeyColumns;
    }

    public String getMessageKeyColumns() {
        return messageKeyColumns;
    }

    /**
     * The maximum number of records that should be loaded into memory while
     * streaming.  A value of `0` uses the default JDBC fetch size.
     */
    public void setQueryFetchSize(int queryFetchSize) {
        this.queryFetchSize = queryFetchSize;
    }

    public int getQueryFetchSize() {
        return queryFetchSize;
    }

    /**
     * Regular expressions matching columns to exclude from change events
     * (deprecated, use "column.exclude.list" instead)
     */
    public void setColumnBlacklist(String columnBlacklist) {
        this.columnBlacklist = columnBlacklist;
    }

    public String getColumnBlacklist() {
        return columnBlacklist;
    }

    /**
     * A comma-separated list of regular expressions that match the
     * fully-qualified names of tables to be excluded from monitoring
     * (deprecated, use "table.exclude.list" instead)
     */
    public void setTableBlacklist(String tableBlacklist) {
        this.tableBlacklist = tableBlacklist;
    }

    public String getTableBlacklist() {
        return tableBlacklist;
    }

    /**
     * Whether the connector should publish changes in the database schema to a
     * Kafka topic with the same name as the database server ID. Each schema
     * change will be recorded using a key that contains the database name and
     * whose value include logical description of the new schema and optionally
     * the DDL statement(s).The default is 'true'. This is independent of how
     * the connector internally records database history.
     */
    public void setIncludeSchemaChanges(boolean includeSchemaChanges) {
        this.includeSchemaChanges = includeSchemaChanges;
    }

    public boolean isIncludeSchemaChanges() {
        return includeSchemaChanges;
    }

    /**
     * Time to wait for new change events to appear after receiving no events,
     * given in milliseconds. Defaults to 500 ms.
     */
    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * The number of milliseconds to wait while polling for persisted data
     * during recovery.
     */
    public void setDatabaseHistoryKafkaRecoveryPollIntervalMs(
            int databaseHistoryKafkaRecoveryPollIntervalMs) {
        this.databaseHistoryKafkaRecoveryPollIntervalMs = databaseHistoryKafkaRecoveryPollIntervalMs;
    }

    public int getDatabaseHistoryKafkaRecoveryPollIntervalMs() {
        return databaseHistoryKafkaRecoveryPollIntervalMs;
    }

    /**
     * The name of the data collection that is used to send signals/commands to
     * Debezium. Signaling is disabled when not set.
     */
    public void setSignalDataCollection(String signalDataCollection) {
        this.signalDataCollection = signalDataCollection;
    }

    public String getSignalDataCollection() {
        return signalDataCollection;
    }

    /**
     * Controls what DDL will Debezium store in database history. By default
     * (false) Debezium will store all incoming DDL statements. If set to true,
     * then only DDL that manipulates a captured table will be stored.
     */
    public void setDatabaseHistoryStoreOnlyCapturedTablesDdl(
            boolean databaseHistoryStoreOnlyCapturedTablesDdl) {
        this.databaseHistoryStoreOnlyCapturedTablesDdl = databaseHistoryStoreOnlyCapturedTablesDdl;
    }

    public boolean isDatabaseHistoryStoreOnlyCapturedTablesDdl() {
        return databaseHistoryStoreOnlyCapturedTablesDdl;
    }

    /**
     * Optional list of custom converters that would be used instead of default
     * ones. The converters are defined using '<converter.prefix>.type' config
     * option and configured using options '<converter.prefix>.<option>'
     */
    public void setConverters(String converters) {
        this.converters = converters;
    }

    public String getConverters() {
        return converters;
    }

    /**
     * The prefix that is used to name heartbeat topics.Defaults to
     * __debezium-heartbeat.
     */
    public void setHeartbeatTopicsPrefix(String heartbeatTopicsPrefix) {
        this.heartbeatTopicsPrefix = heartbeatTopicsPrefix;
    }

    public String getHeartbeatTopicsPrefix() {
        return heartbeatTopicsPrefix;
    }

    /**
     * The maximum number of records that should be loaded into memory while
     * performing a snapshot
     */
    public void setSnapshotFetchSize(int snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public int getSnapshotFetchSize() {
        return snapshotFetchSize;
    }

    /**
     * The maximum number of millis to wait for table locks at the beginning of
     * a snapshot. If locks cannot be acquired in this time frame, the snapshot
     * will be aborted. Defaults to 10 seconds
     */
    public void setSnapshotLockTimeoutMs(long snapshotLockTimeoutMs) {
        this.snapshotLockTimeoutMs = snapshotLockTimeoutMs;
    }

    public long getSnapshotLockTimeoutMs() {
        return snapshotLockTimeoutMs;
    }

    /**
     * The number of milliseconds to wait while fetching cluster information
     * using Kafka admin client.
     */
    public void setDatabaseHistoryKafkaQueryTimeoutMs(
            long databaseHistoryKafkaQueryTimeoutMs) {
        this.databaseHistoryKafkaQueryTimeoutMs = databaseHistoryKafkaQueryTimeoutMs;
    }

    public long getDatabaseHistoryKafkaQueryTimeoutMs() {
        return databaseHistoryKafkaQueryTimeoutMs;
    }

    /**
     * Name of the database user to be used when connecting to the database.
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public String getDatabaseUser() {
        return databaseUser;
    }

    /**
     * The name of the database from which the connector should capture changes
     */
    public void setDatabaseDbname(String databaseDbname) {
        this.databaseDbname = databaseDbname;
    }

    public String getDatabaseDbname() {
        return databaseDbname;
    }

    /**
     * A comma-separated list of regular expressions matching the
     * database-specific data type names that adds the data type's original type
     * and original length as parameters to the corresponding field schemas in
     * the emitted change records.
     */
    public void setDatatypePropagateSourceType(
            String datatypePropagateSourceType) {
        this.datatypePropagateSourceType = datatypePropagateSourceType;
    }

    public String getDatatypePropagateSourceType() {
        return datatypePropagateSourceType;
    }

    /**
     * Whether field names will be sanitized to Avro naming conventions
     */
    public void setSanitizeFieldNames(boolean sanitizeFieldNames) {
        this.sanitizeFieldNames = sanitizeFieldNames;
    }

    public boolean isSanitizeFieldNames() {
        return sanitizeFieldNames;
    }

    /**
     *  This property contains a comma-separated list of fully-qualified tables
     * (DB_NAME.TABLE_NAME) or (SCHEMA_NAME.TABLE_NAME), depending on
     * thespecific connectors. Select statements for the individual tables are
     * specified in further configuration properties, one for each table,
     * identified by the id
     * 'snapshot.select.statement.overrides.[DB_NAME].[TABLE_NAME]' or
     * 'snapshot.select.statement.overrides.[SCHEMA_NAME].[TABLE_NAME]',
     * respectively. The value of those properties is the select statement to
     * use when retrieving data from the specific table during snapshotting. A
     * possible use case for large append-only tables is setting a specific
     * point where to start (resume) snapshotting, in case a previous
     * snapshotting was interrupted.
     */
    public void setSnapshotSelectStatementOverrides(
            String snapshotSelectStatementOverrides) {
        this.snapshotSelectStatementOverrides = snapshotSelectStatementOverrides;
    }

    public String getSnapshotSelectStatementOverrides() {
        return snapshotSelectStatementOverrides;
    }

    /**
     * A list of host/port pairs that the connector will use for establishing
     * the initial connection to the Kafka cluster for retrieving database
     * schema history previously stored by the connector. This should point to
     * the same Kafka cluster used by the Kafka Connect process.
     */
    public void setDatabaseHistoryKafkaBootstrapServers(
            String databaseHistoryKafkaBootstrapServers) {
        this.databaseHistoryKafkaBootstrapServers = databaseHistoryKafkaBootstrapServers;
    }

    public String getDatabaseHistoryKafkaBootstrapServers() {
        return databaseHistoryKafkaBootstrapServers;
    }

    /**
     * A version of the format of the publicly visible source part in the
     * message
     */
    public void setSourceStructVersion(String sourceStructVersion) {
        this.sourceStructVersion = sourceStructVersion;
    }

    public String getSourceStructVersion() {
        return sourceStructVersion;
    }

    /**
     * Length of an interval in milli-seconds in in which the connector
     * periodically sends heartbeat messages to a heartbeat topic. Use 0 to
     * disable heartbeat messages. Disabled by default.
     */
    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /**
     * Regular expressions matching columns to include in change events
     * (deprecated, use "column.include.list" instead)
     */
    public void setColumnWhitelist(String columnWhitelist) {
        this.columnWhitelist = columnWhitelist;
    }

    public String getColumnWhitelist() {
        return columnWhitelist;
    }

    /**
     * Regular expressions matching columns to include in change events
     */
    public void setColumnIncludeList(String columnIncludeList) {
        this.columnIncludeList = columnIncludeList;
    }

    public String getColumnIncludeList() {
        return columnIncludeList;
    }

    /**
     * A comma-separated list of regular expressions matching fully-qualified
     * names of columns that  adds the columns original type and original length
     * as parameters to the corresponding field schemas in the emitted change
     * records.
     */
    public void setColumnPropagateSourceType(String columnPropagateSourceType) {
        this.columnPropagateSourceType = columnPropagateSourceType;
    }

    public String getColumnPropagateSourceType() {
        return columnPropagateSourceType;
    }

    /**
     * A comma-separated list of regular expressions that match the
     * fully-qualified names of tables to be excluded from monitoring
     */
    public void setTableExcludeList(String tableExcludeList) {
        this.tableExcludeList = tableExcludeList;
    }

    public String getTableExcludeList() {
        return tableExcludeList;
    }

    /**
     * Password of the database user to be used when connecting to the database.
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    /**
     * Controls what DDL will Debezium store in database history. By default
     * (false) Debezium will store all incoming DDL statements. If set to true,
     * then only DDL that manipulates a monitored table will be stored
     * (deprecated, use "database.history.store.only.captured.tables.ddl"
     * instead)
     */
    public void setDatabaseHistoryStoreOnlyMonitoredTablesDdl(
            boolean databaseHistoryStoreOnlyMonitoredTablesDdl) {
        this.databaseHistoryStoreOnlyMonitoredTablesDdl = databaseHistoryStoreOnlyMonitoredTablesDdl;
    }

    public boolean isDatabaseHistoryStoreOnlyMonitoredTablesDdl() {
        return databaseHistoryStoreOnlyMonitoredTablesDdl;
    }

    /**
     * Maximum size of each batch of source records. Defaults to 2048.
     */
    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * The comma-separated list of operations to skip during streaming, defined
     * as: 'c' for inserts/create; 'u' for updates; 'd' for deletes, 't' for
     * truncates, and 'none' to indicate nothing skipped. By default, no
     * operations will be skipped.
     */
    public void setSkippedOperations(String skippedOperations) {
        this.skippedOperations = skippedOperations;
    }

    public String getSkippedOperations() {
        return skippedOperations;
    }

    /**
     * The criteria for running a snapshot upon startup of the connector.
     * Options include: 'initial' (the default) to specify the connector should
     * run a snapshot only when no offsets are available for the logical server
     * name; 'schema_only' to specify the connector should run a snapshot of the
     * schema when no offsets are available for the logical server name. 
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * The name of the DatabaseHistory class that should be used to store and
     * recover database schema changes. The configuration properties for the
     * history are prefixed with the 'database.history.' string.
     */
    public void setDatabaseHistory(String databaseHistory) {
        this.databaseHistory = databaseHistory;
    }

    public String getDatabaseHistory() {
        return databaseHistory;
    }

    /**
     * Maximum size of the queue for change events read from the database log
     * but not yet recorded or forwarded. Defaults to 8192, and should always be
     * larger than the maximum batch size.
     */
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * The maximum size of chunk for incremental snapshotting
     */
    public void setIncrementalSnapshotChunkSize(int incrementalSnapshotChunkSize) {
        this.incrementalSnapshotChunkSize = incrementalSnapshotChunkSize;
    }

    public int getIncrementalSnapshotChunkSize() {
        return incrementalSnapshotChunkSize;
    }

    /**
     * The name of the topic for the database schema history
     */
    public void setDatabaseHistoryKafkaTopic(String databaseHistoryKafkaTopic) {
        this.databaseHistoryKafkaTopic = databaseHistoryKafkaTopic;
    }

    public String getDatabaseHistoryKafkaTopic() {
        return databaseHistoryKafkaTopic;
    }

    /**
     * Time to wait before restarting connector after retriable exception
     * occurs. Defaults to 10000ms.
     */
    public void setRetriableRestartConnectorWaitMs(
            long retriableRestartConnectorWaitMs) {
        this.retriableRestartConnectorWaitMs = retriableRestartConnectorWaitMs;
    }

    public long getRetriableRestartConnectorWaitMs() {
        return retriableRestartConnectorWaitMs;
    }

    /**
     * A delay period before a snapshot will begin, given in milliseconds.
     * Defaults to 0 ms.
     */
    public void setSnapshotDelayMs(long snapshotDelayMs) {
        this.snapshotDelayMs = snapshotDelayMs;
    }

    public long getSnapshotDelayMs() {
        return snapshotDelayMs;
    }

    /**
     * The number of attempts in a row that no data are returned from Kafka
     * before recover completes. The maximum amount of time to wait after
     * receiving no data is (recovery.attempts) x (recovery.poll.interval.ms).
     */
    public void setDatabaseHistoryKafkaRecoveryAttempts(
            int databaseHistoryKafkaRecoveryAttempts) {
        this.databaseHistoryKafkaRecoveryAttempts = databaseHistoryKafkaRecoveryAttempts;
    }

    public int getDatabaseHistoryKafkaRecoveryAttempts() {
        return databaseHistoryKafkaRecoveryAttempts;
    }

    /**
     * Enables transaction metadata extraction together with event counting
     */
    public void setProvideTransactionMetadata(boolean provideTransactionMetadata) {
        this.provideTransactionMetadata = provideTransactionMetadata;
    }

    public boolean isProvideTransactionMetadata() {
        return provideTransactionMetadata;
    }

    /**
     * The tables for which changes are to be captured (deprecated, use
     * "table.include.list" instead)
     */
    public void setTableWhitelist(String tableWhitelist) {
        this.tableWhitelist = tableWhitelist;
    }

    public String getTableWhitelist() {
        return tableWhitelist;
    }

    /**
     * Whether delete operations should be represented by a delete event and a
     * subsquenttombstone event (true) or only by a delete event (false).
     * Emitting the tombstone event (the default behavior) allows Kafka to
     * completely delete all events pertaining to the given key once the source
     * record got deleted.
     */
    public void setTombstonesOnDelete(boolean tombstonesOnDelete) {
        this.tombstonesOnDelete = tombstonesOnDelete;
    }

    public boolean isTombstonesOnDelete() {
        return tombstonesOnDelete;
    }

    /**
     * Specify how DECIMAL and NUMERIC columns should be represented in change
     * events, including:'precise' (the default) uses java.math.BigDecimal to
     * represent values, which are encoded in the change events using a binary
     * representation and Kafka Connect's
     * 'org.apache.kafka.connect.data.Decimal' type; 'string' uses string to
     * represent values; 'double' represents values using Java's 'double', which
     * may not offer the precision but will be far easier to use in consumers.
     */
    public void setDecimalHandlingMode(String decimalHandlingMode) {
        this.decimalHandlingMode = decimalHandlingMode;
    }

    public String getDecimalHandlingMode() {
        return decimalHandlingMode;
    }

    /**
     * Controls the action Debezium will take when it meets a DDL statement in
     * binlog, that it cannot parse.By default the connector will stop operating
     * but by changing the setting it can ignore the statements which it cannot
     * parse. If skipping is enabled then Debezium can miss metadata changes.
     */
    public void setDatabaseHistorySkipUnparseableDdl(
            boolean databaseHistorySkipUnparseableDdl) {
        this.databaseHistorySkipUnparseableDdl = databaseHistorySkipUnparseableDdl;
    }

    public boolean isDatabaseHistorySkipUnparseableDdl() {
        return databaseHistorySkipUnparseableDdl;
    }

    /**
     * Flag specifying whether built-in tables should be ignored.
     */
    public void setTableIgnoreBuiltin(boolean tableIgnoreBuiltin) {
        this.tableIgnoreBuiltin = tableIgnoreBuiltin;
    }

    public boolean isTableIgnoreBuiltin() {
        return tableIgnoreBuiltin;
    }

    /**
     * this setting must be set to specify a list of tables/collections whose
     * snapshot must be taken on creating or restarting the connector.
     */
    public void setSnapshotIncludeCollectionList(
            String snapshotIncludeCollectionList) {
        this.snapshotIncludeCollectionList = snapshotIncludeCollectionList;
    }

    public String getSnapshotIncludeCollectionList() {
        return snapshotIncludeCollectionList;
    }

    /**
     * The path to the file that will be used to record the database history
     */
    public void setDatabaseHistoryFileFilename(
            String databaseHistoryFileFilename) {
        this.databaseHistoryFileFilename = databaseHistoryFileFilename;
    }

    public String getDatabaseHistoryFileFilename() {
        return databaseHistoryFileFilename;
    }

    /**
     * Maximum size of the queue in bytes for change events read from the
     * database log but not yet recorded or forwarded. Defaults to 0. Mean the
     * feature is not enabled
     */
    public void setMaxQueueSizeInBytes(long maxQueueSizeInBytes) {
        this.maxQueueSizeInBytes = maxQueueSizeInBytes;
    }

    public long getMaxQueueSizeInBytes() {
        return maxQueueSizeInBytes;
    }

    /**
     * The name of the transaction metadata topic. The placeholder
     * ${database.server.name} can be used for referring to the connector's
     * logical name; defaults to ${database.server.name}.transaction.
     */
    public void setTransactionTopic(String transactionTopic) {
        this.transactionTopic = transactionTopic;
    }

    public String getTransactionTopic() {
        return transactionTopic;
    }

    /**
     * Time, date, and timestamps can be represented with different kinds of
     * precisions, including:'adaptive' (the default) bases the precision of
     * time, date, and timestamp values on the database column's precision;
     * 'adaptive_time_microseconds' like 'adaptive' mode, but TIME fields always
     * use microseconds precision;'connect' always represents time, date, and
     * timestamp values using Kafka Connect's built-in representations for Time,
     * Date, and Timestamp, which uses millisecond precision regardless of the
     * database columns' precision .
     */
    public void setTimePrecisionMode(String timePrecisionMode) {
        this.timePrecisionMode = timePrecisionMode;
    }

    public String getTimePrecisionMode() {
        return timePrecisionMode;
    }

    /**
     * Unique name that identifies the database server and all recorded offsets,
     * and that is used as a prefix for all schemas and topics. Each distinct
     * installation should have a separate namespace and be monitored by at most
     * one Debezium connector.
     */
    public void setDatabaseServerName(String databaseServerName) {
        this.databaseServerName = databaseServerName;
    }

    public String getDatabaseServerName() {
        return databaseServerName;
    }

    /**
     * Specify how failures during processing of events (i.e. when encountering
     * a corrupted event) should be handled, including:'fail' (the default) an
     * exception indicating the problematic event and its position is raised,
     * causing the connector to be stopped; 'warn' the problematic event and its
     * position will be logged and the event will be skipped;'ignore' the
     * problematic event will be skipped.
     */
    public void setEventProcessingFailureHandlingMode(
            String eventProcessingFailureHandlingMode) {
        this.eventProcessingFailureHandlingMode = eventProcessingFailureHandlingMode;
    }

    public String getEventProcessingFailureHandlingMode() {
        return eventProcessingFailureHandlingMode;
    }

    /**
     * Port of the database server.
     */
    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    /**
     * Regular expressions matching columns to exclude from change events
     */
    public void setColumnExcludeList(String columnExcludeList) {
        this.columnExcludeList = columnExcludeList;
    }

    public String getColumnExcludeList() {
        return columnExcludeList;
    }

    /**
     * Resolvable hostname or IP address of the database server.
     */
    public void setDatabaseHostname(String databaseHostname) {
        this.databaseHostname = databaseHostname;
    }

    public String getDatabaseHostname() {
        return databaseHostname;
    }

    /**
     * Specify how schema names should be adjusted for compatibility with the
     * message converter used by the connector, including:'avro' replaces the
     * characters that cannot be used in the Avro type name with underscore
     * (default)'none' does not apply any adjustment
     */
    public void setSchemaNameAdjustmentMode(String schemaNameAdjustmentMode) {
        this.schemaNameAdjustmentMode = schemaNameAdjustmentMode;
    }

    public String getSchemaNameAdjustmentMode() {
        return schemaNameAdjustmentMode;
    }

    /**
     * The tables for which changes are to be captured
     */
    public void setTableIncludeList(String tableIncludeList) {
        this.tableIncludeList = tableIncludeList;
    }

    public String getTableIncludeList() {
        return tableIncludeList;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "column.blacklist", columnBlacklist);
        addPropertyIfNotNull(configBuilder, "table.blacklist", tableBlacklist);
        addPropertyIfNotNull(configBuilder, "include.schema.changes", includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.recovery.poll.interval.ms", databaseHistoryKafkaRecoveryPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "database.history.store.only.captured.tables.ddl", databaseHistoryStoreOnlyCapturedTablesDdl);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.query.timeout.ms", databaseHistoryKafkaQueryTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "database.dbname", databaseDbname);
        addPropertyIfNotNull(configBuilder, "datatype.propagate.source.type", datatypePropagateSourceType);
        addPropertyIfNotNull(configBuilder, "sanitize.field.names", sanitizeFieldNames);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.bootstrap.servers", databaseHistoryKafkaBootstrapServers);
        addPropertyIfNotNull(configBuilder, "source.struct.version", sourceStructVersion);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "column.whitelist", columnWhitelist);
        addPropertyIfNotNull(configBuilder, "column.include.list", columnIncludeList);
        addPropertyIfNotNull(configBuilder, "column.propagate.source.type", columnPropagateSourceType);
        addPropertyIfNotNull(configBuilder, "table.exclude.list", tableExcludeList);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "database.history.store.only.monitored.tables.ddl", databaseHistoryStoreOnlyMonitoredTablesDdl);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "database.history", databaseHistory);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.chunk.size", incrementalSnapshotChunkSize);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.topic", databaseHistoryKafkaTopic);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.recovery.attempts", databaseHistoryKafkaRecoveryAttempts);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "table.whitelist", tableWhitelist);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.history.skip.unparseable.ddl", databaseHistorySkipUnparseableDdl);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "database.history.file.filename", databaseHistoryFileFilename);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "transaction.topic", transactionTopic);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "database.server.name", databaseServerName);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "column.exclude.list", columnExcludeList);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "table.include.list", tableIncludeList);
        
        return configBuilder.build();
    }

    @Override
    protected Class configureConnectorClass() {
        return Db2Connector.class;
    }

    @Override
    protected ConfigurationValidation validateConnectorConfiguration() {
        if (isFieldValueNotSet(databasePassword)) {
        	return ConfigurationValidation.notValid("Required field 'databasePassword' must be set.");
        }
        if (isFieldValueNotSet(databaseServerName)) {
        	return ConfigurationValidation.notValid("Required field 'databaseServerName' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "db2";
    }
}