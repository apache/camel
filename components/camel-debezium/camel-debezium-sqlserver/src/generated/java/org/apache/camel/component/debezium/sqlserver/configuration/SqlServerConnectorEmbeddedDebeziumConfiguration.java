package org.apache.camel.component.debezium.sqlserver.configuration;

import io.debezium.config.Configuration;
import io.debezium.connector.sqlserver.SqlServerConnector;
import org.apache.camel.component.debezium.configuration.ConfigurationValidation;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class SqlServerConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,sqlserver";
    @UriParam(label = LABEL_NAME)
    private String messageKeyColumns;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.pipeline.txmetadata.DefaultTransactionMetadataFactory")
    private String transactionMetadataFactory = "io.debezium.pipeline.txmetadata.DefaultTransactionMetadataFactory";
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long streamingDelayMs = 0;
    @UriParam(label = LABEL_NAME)
    private String customMetricTags;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationJobNamespace;
    @UriParam(label = LABEL_NAME, defaultValue = "10m", javaType = "java.time.Duration")
    private int databaseQueryTimeoutMs = 600000;
    @UriParam(label = LABEL_NAME, defaultValue = "function")
    private String dataQueryMode = "function";
    @UriParam(label = LABEL_NAME, defaultValue = "source")
    private String signalEnabledChannels = "source";
    @UriParam(label = LABEL_NAME)
    private String databaseInstance;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    @UriParam(label = LABEL_NAME)
    private String heartbeatActionQuery;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationJobTags;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String datatypePropagateSourceType;
    @UriParam(label = LABEL_NAME)
    private String databaseNames;
    @UriParam(label = LABEL_NAME, defaultValue = "disabled")
    private String snapshotTablesOrderByRowCount = "disabled";
    @UriParam(label = LABEL_NAME, defaultValue = "INSERT_INSERT")
    private String incrementalSnapshotWatermarkingStrategy = "INSERT_INSERT";
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotOnSchemaError = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean incrementalSnapshotAllowSchemaChanges = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalSkipUnparseableDdl = false;
    @UriParam(label = LABEL_NAME)
    private String columnIncludeList;
    @UriParam(label = LABEL_NAME)
    private String columnPropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "-1")
    private int errorsMaxRetries = -1;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int streamingFetchSize = 0;
    @UriParam(label = LABEL_NAME)
    private String tableExcludeList;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "t")
    private String skippedOperations = "t";
    @UriParam(label = LABEL_NAME, defaultValue = "Debezium change data capture job")
    private String openlineageIntegrationJobDescription = "Debezium change data capture job";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.schema.SchemaTopicNamingStrategy")
    private String topicNamingStrategy = "io.debezium.schema.SchemaTopicNamingStrategy";
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotData = false;
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME, defaultValue = "1024")
    private int incrementalSnapshotChunkSize = 1024;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationJobOwners;
    @UriParam(label = LABEL_NAME, defaultValue = "./openlineage.yml")
    private String openlineageIntegrationConfigFilePath = "./openlineage.yml";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "4s", javaType = "java.time.Duration")
    private long executorShutdownTimeoutMs = 4000;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalStoreOnlyCapturedTablesDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalStoreOnlyCapturedDatabasesDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotOnDataError = false;
    @UriParam(label = LABEL_NAME)
    private String schemaHistoryInternalFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String topicPrefix;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "bytes")
    private String binaryHandlingMode = "bytes";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeSchemaComments = false;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.connector.sqlserver.SqlServerSourceInfoStructMaker")
    private String sourceinfoStructMaker = "io.debezium.connector.sqlserver.SqlServerSourceInfoStructMaker";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean incrementalSnapshotOptionRecompile = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean openlineageIntegrationEnabled = false;
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedStartStream = false;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotSchema = false;
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive")
    private String timePrecisionMode = "adaptive";
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private long signalPollIntervalMs = 5000;
    @UriParam(label = LABEL_NAME)
    private String postProcessors;
    @UriParam(label = LABEL_NAME)
    private String notificationEnabledChannels;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "repeatable_read")
    private String snapshotIsolationMode = "repeatable_read";
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int snapshotMaxThreads = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "1433")
    private int databasePort = 1433;
    @UriParam(label = LABEL_NAME)
    private String notificationSinkTopicName;
    @UriParam(label = LABEL_NAME)
    private String snapshotModeCustomName;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.storage.kafka.history.KafkaSchemaHistory")
    private String schemaHistoryInternal = "io.debezium.storage.kafka.history.KafkaSchemaHistory";
    @UriParam(label = LABEL_NAME, defaultValue = "500")
    private int maxIterationTransactions = 500;
    @UriParam(label = LABEL_NAME)
    private String columnExcludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String schemaNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME)
    private String tableIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "1m", javaType = "java.time.Duration")
    private long connectionValidationTimeoutMs = 60000;

    /**
     * A semicolon-separated list of expressions that match fully-qualified
     * tables and column(s) to be used as message key. Each expression must
     * match the pattern '<fully-qualified table name>:<key columns>', where the
     * table names could be defined as (DB_NAME.TABLE_NAME) or
     * (SCHEMA_NAME.TABLE_NAME), depending on the specific connector, and the
     * key columns are a comma-separated list of columns representing the custom
     * key. For any table without an explicit key configuration the table's
     * primary key column(s) will be used as message key. Example:
     * dbserver1.inventory.orderlines:orderId,orderLineId;dbserver1.inventory.orders:id
     */
    public void setMessageKeyColumns(String messageKeyColumns) {
        this.messageKeyColumns = messageKeyColumns;
    }

    public String getMessageKeyColumns() {
        return messageKeyColumns;
    }

    /**
     * Class to make transaction context & transaction struct/schemas
     */
    public void setTransactionMetadataFactory(String transactionMetadataFactory) {
        this.transactionMetadataFactory = transactionMetadataFactory;
    }

    public String getTransactionMetadataFactory() {
        return transactionMetadataFactory;
    }

    /**
     * A delay period after the snapshot is completed and the streaming begins,
     * given in milliseconds. Defaults to 0 ms.
     */
    public void setStreamingDelayMs(long streamingDelayMs) {
        this.streamingDelayMs = streamingDelayMs;
    }

    public long getStreamingDelayMs() {
        return streamingDelayMs;
    }

    /**
     * The custom metric tags will accept key-value pairs to customize the MBean
     * object name which should be appended the end of regular name, each key
     * would represent a tag for the MBean object name, and the corresponding
     * value would be the value of that tag the key is. For example: k1=v1,k2=v2
     */
    public void setCustomMetricTags(String customMetricTags) {
        this.customMetricTags = customMetricTags;
    }

    public String getCustomMetricTags() {
        return customMetricTags;
    }

    /**
     * The job's namespace emitted by Debezium
     */
    public void setOpenlineageIntegrationJobNamespace(
            String openlineageIntegrationJobNamespace) {
        this.openlineageIntegrationJobNamespace = openlineageIntegrationJobNamespace;
    }

    public String getOpenlineageIntegrationJobNamespace() {
        return openlineageIntegrationJobNamespace;
    }

    /**
     * Time to wait for a query to execute, given in milliseconds. Defaults to
     * 600 seconds (600,000 ms); zero means there is no limit.
     */
    public void setDatabaseQueryTimeoutMs(int databaseQueryTimeoutMs) {
        this.databaseQueryTimeoutMs = databaseQueryTimeoutMs;
    }

    public int getDatabaseQueryTimeoutMs() {
        return databaseQueryTimeoutMs;
    }

    /**
     * Controls how the connector queries CDC data. The default is 'function',
     * which means the data is queried by means of calling
     * cdc.[fn_cdc_get_all_changes_#] function. The value of 'direct' makes the
     * connector to query the change tables directly.
     */
    public void setDataQueryMode(String dataQueryMode) {
        this.dataQueryMode = dataQueryMode;
    }

    public String getDataQueryMode() {
        return dataQueryMode;
    }

    /**
     * List of channels names that are enabled. Source channel is enabled by
     * default
     */
    public void setSignalEnabledChannels(String signalEnabledChannels) {
        this.signalEnabledChannels = signalEnabledChannels;
    }

    public String getSignalEnabledChannels() {
        return signalEnabledChannels;
    }

    /**
     * The SQL Server instance name
     */
    public void setDatabaseInstance(String databaseInstance) {
        this.databaseInstance = databaseInstance;
    }

    public String getDatabaseInstance() {
        return databaseInstance;
    }

    /**
     * Whether the connector should publish changes in the database schema to a
     * Kafka topic with the same name as the database server ID. Each schema
     * change will be recorded using a key that contains the database name and
     * whose value include logical description of the new schema and optionally
     * the DDL statement(s). The default is 'true'. This is independent of how
     * the connector internally records database schema history.
     */
    public void setIncludeSchemaChanges(boolean includeSchemaChanges) {
        this.includeSchemaChanges = includeSchemaChanges;
    }

    public boolean isIncludeSchemaChanges() {
        return includeSchemaChanges;
    }

    /**
     * The query executed with every heartbeat.
     */
    public void setHeartbeatActionQuery(String heartbeatActionQuery) {
        this.heartbeatActionQuery = heartbeatActionQuery;
    }

    public String getHeartbeatActionQuery() {
        return heartbeatActionQuery;
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
     * performing a snapshot.
     */
    public void setSnapshotFetchSize(int snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public int getSnapshotFetchSize() {
        return snapshotFetchSize;
    }

    /**
     * The job's tags emitted by Debezium. A comma-separated list of key-value
     * pairs.For example: k1=v1,k2=v2
     */
    public void setOpenlineageIntegrationJobTags(
            String openlineageIntegrationJobTags) {
        this.openlineageIntegrationJobTags = openlineageIntegrationJobTags;
    }

    public String getOpenlineageIntegrationJobTags() {
        return openlineageIntegrationJobTags;
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
     * Name of the database user to be used when connecting to the database.
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public String getDatabaseUser() {
        return databaseUser;
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
     * The names of the databases from which the connector should capture
     * changes
     */
    public void setDatabaseNames(String databaseNames) {
        this.databaseNames = databaseNames;
    }

    public String getDatabaseNames() {
        return databaseNames;
    }

    /**
     * Controls the order in which tables are processed in the initial snapshot.
     * A `descending` value will order the tables by row count descending. A
     * `ascending` value will order the tables by row count ascending. A value
     * of `disabled` (the default) will disable ordering by row count.
     */
    public void setSnapshotTablesOrderByRowCount(
            String snapshotTablesOrderByRowCount) {
        this.snapshotTablesOrderByRowCount = snapshotTablesOrderByRowCount;
    }

    public String getSnapshotTablesOrderByRowCount() {
        return snapshotTablesOrderByRowCount;
    }

    /**
     * Specify the strategy used for watermarking during an incremental
     * snapshot: 'insert_insert' both open and close signal is written into
     * signal data collection (default); 'insert_delete' only open signal is
     * written on signal data collection, the close will delete the relative
     * open signal;
     */
    public void setIncrementalSnapshotWatermarkingStrategy(
            String incrementalSnapshotWatermarkingStrategy) {
        this.incrementalSnapshotWatermarkingStrategy = incrementalSnapshotWatermarkingStrategy;
    }

    public String getIncrementalSnapshotWatermarkingStrategy() {
        return incrementalSnapshotWatermarkingStrategy;
    }

    /**
     *  This property contains a comma-separated list of fully-qualified tables
     * (DB_NAME.TABLE_NAME) or (SCHEMA_NAME.TABLE_NAME), depending on the
     * specific connectors. Select statements for the individual tables are
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
     * When 'snapshot.mode' is set as configuration_based, this setting permits
     * to specify whenever the schema should be snapshotted or not in case of
     * error.
     */
    public void setSnapshotModeConfigurationBasedSnapshotOnSchemaError(
            boolean snapshotModeConfigurationBasedSnapshotOnSchemaError) {
        this.snapshotModeConfigurationBasedSnapshotOnSchemaError = snapshotModeConfigurationBasedSnapshotOnSchemaError;
    }

    public boolean isSnapshotModeConfigurationBasedSnapshotOnSchemaError() {
        return snapshotModeConfigurationBasedSnapshotOnSchemaError;
    }

    /**
     * Detect schema change during an incremental snapshot and re-select a
     * current chunk to avoid locking DDLs. Note that changes to a primary key
     * are not supported and can cause incorrect results if performed during an
     * incremental snapshot. Another limitation is that if a schema change
     * affects only columns' default values, then the change won't be detected
     * until the DDL is processed from the binlog stream. This doesn't affect
     * the snapshot events' values, but the schema of snapshot events may have
     * outdated defaults.
     */
    public void setIncrementalSnapshotAllowSchemaChanges(
            boolean incrementalSnapshotAllowSchemaChanges) {
        this.incrementalSnapshotAllowSchemaChanges = incrementalSnapshotAllowSchemaChanges;
    }

    public boolean isIncrementalSnapshotAllowSchemaChanges() {
        return incrementalSnapshotAllowSchemaChanges;
    }

    /**
     * Controls the action Debezium will take when it meets a DDL statement in
     * binlog, that it cannot parse.By default the connector will stop operating
     * but by changing the setting it can ignore the statements which it cannot
     * parse. If skipping is enabled then Debezium can miss metadata changes.
     */
    public void setSchemaHistoryInternalSkipUnparseableDdl(
            boolean schemaHistoryInternalSkipUnparseableDdl) {
        this.schemaHistoryInternalSkipUnparseableDdl = schemaHistoryInternalSkipUnparseableDdl;
    }

    public boolean isSchemaHistoryInternalSkipUnparseableDdl() {
        return schemaHistoryInternalSkipUnparseableDdl;
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
     * names of columns that adds the columns original type and original length
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
     * The maximum number of retries on connection errors before failing (-1 =
     * no limit, 0 = disabled, > 0 = num of retries).
     */
    public void setErrorsMaxRetries(int errorsMaxRetries) {
        this.errorsMaxRetries = errorsMaxRetries;
    }

    public int getErrorsMaxRetries() {
        return errorsMaxRetries;
    }

    /**
     * Specifies the maximum number of rows that should be read in one go from
     * each table while streaming. The connector will read the table contents in
     * multiple batches of this size. Defaults to 0 which means no limit.
     */
    public void setStreamingFetchSize(int streamingFetchSize) {
        this.streamingFetchSize = streamingFetchSize;
    }

    public int getStreamingFetchSize() {
        return streamingFetchSize;
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
     * truncates, and 'none' to indicate nothing skipped. By default, only
     * truncate operations will be skipped.
     */
    public void setSkippedOperations(String skippedOperations) {
        this.skippedOperations = skippedOperations;
    }

    public String getSkippedOperations() {
        return skippedOperations;
    }

    /**
     * The job's description emitted by Debezium
     */
    public void setOpenlineageIntegrationJobDescription(
            String openlineageIntegrationJobDescription) {
        this.openlineageIntegrationJobDescription = openlineageIntegrationJobDescription;
    }

    public String getOpenlineageIntegrationJobDescription() {
        return openlineageIntegrationJobDescription;
    }

    /**
     * The name of the TopicNamingStrategy class that should be used to
     * determine the topic name for data change, schema change, transaction,
     * heartbeat event etc.
     */
    public void setTopicNamingStrategy(String topicNamingStrategy) {
        this.topicNamingStrategy = topicNamingStrategy;
    }

    public String getTopicNamingStrategy() {
        return topicNamingStrategy;
    }

    /**
     * The criteria for running a snapshot upon startup of the connector. Select
     * one of the following snapshot options: 'initial' (default): If the
     * connector does not detect any offsets for the logical server name, it
     * runs a snapshot that captures the current full state of the configured
     * tables. After the snapshot completes, the connector begins to stream
     * changes from the transaction log.; 'initial_only': The connector performs
     * a snapshot as it does for the 'initial' option, but after the connector
     * completes the snapshot, it stops, and does not stream changes from the
     * transaction log.; 'schema_only': If the connector does not detect any
     * offsets for the logical server name, it runs a snapshot that captures
     * only the schema (table structures), but not any table data. After the
     * snapshot completes, the connector begins to stream changes from the
     * transaction log.
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * When 'snapshot.mode' is set as configuration_based, this setting permits
     * to specify whenever the data should be snapshotted or not.
     */
    public void setSnapshotModeConfigurationBasedSnapshotData(
            boolean snapshotModeConfigurationBasedSnapshotData) {
        this.snapshotModeConfigurationBasedSnapshotData = snapshotModeConfigurationBasedSnapshotData;
    }

    public boolean isSnapshotModeConfigurationBasedSnapshotData() {
        return snapshotModeConfigurationBasedSnapshotData;
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
     * The maximum size of chunk (number of documents/rows) for incremental
     * snapshotting
     */
    public void setIncrementalSnapshotChunkSize(int incrementalSnapshotChunkSize) {
        this.incrementalSnapshotChunkSize = incrementalSnapshotChunkSize;
    }

    public int getIncrementalSnapshotChunkSize() {
        return incrementalSnapshotChunkSize;
    }

    /**
     * The job's owners emitted by Debezium. A comma-separated list of key-value
     * pairs.For example: k1=v1,k2=v2
     */
    public void setOpenlineageIntegrationJobOwners(
            String openlineageIntegrationJobOwners) {
        this.openlineageIntegrationJobOwners = openlineageIntegrationJobOwners;
    }

    public String getOpenlineageIntegrationJobOwners() {
        return openlineageIntegrationJobOwners;
    }

    /**
     * Path to OpenLineage file configuration. See
     * https://openlineage.io/docs/client/java/configuration
     */
    public void setOpenlineageIntegrationConfigFilePath(
            String openlineageIntegrationConfigFilePath) {
        this.openlineageIntegrationConfigFilePath = openlineageIntegrationConfigFilePath;
    }

    public String getOpenlineageIntegrationConfigFilePath() {
        return openlineageIntegrationConfigFilePath;
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
     * The maximum time in milliseconds to wait for task executor to shut down.
     */
    public void setExecutorShutdownTimeoutMs(long executorShutdownTimeoutMs) {
        this.executorShutdownTimeoutMs = executorShutdownTimeoutMs;
    }

    public long getExecutorShutdownTimeoutMs() {
        return executorShutdownTimeoutMs;
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
     * Controls what DDL will Debezium store in database schema history. By
     * default (false) Debezium will store all incoming DDL statements. If set
     * to true, then only DDL that manipulates a captured table will be stored.
     */
    public void setSchemaHistoryInternalStoreOnlyCapturedTablesDdl(
            boolean schemaHistoryInternalStoreOnlyCapturedTablesDdl) {
        this.schemaHistoryInternalStoreOnlyCapturedTablesDdl = schemaHistoryInternalStoreOnlyCapturedTablesDdl;
    }

    public boolean isSchemaHistoryInternalStoreOnlyCapturedTablesDdl() {
        return schemaHistoryInternalStoreOnlyCapturedTablesDdl;
    }

    /**
     * Controls what DDL will Debezium store in database schema history. By
     * default (false) Debezium will store all incoming DDL statements. If set
     * to true, then only DDL that manipulates a table from captured
     * schema/database will be stored.
     */
    public void setSchemaHistoryInternalStoreOnlyCapturedDatabasesDdl(
            boolean schemaHistoryInternalStoreOnlyCapturedDatabasesDdl) {
        this.schemaHistoryInternalStoreOnlyCapturedDatabasesDdl = schemaHistoryInternalStoreOnlyCapturedDatabasesDdl;
    }

    public boolean isSchemaHistoryInternalStoreOnlyCapturedDatabasesDdl() {
        return schemaHistoryInternalStoreOnlyCapturedDatabasesDdl;
    }

    /**
     * When 'snapshot.mode' is set as configuration_based, this setting permits
     * to specify whenever the data should be snapshotted or not in case of
     * error.
     */
    public void setSnapshotModeConfigurationBasedSnapshotOnDataError(
            boolean snapshotModeConfigurationBasedSnapshotOnDataError) {
        this.snapshotModeConfigurationBasedSnapshotOnDataError = snapshotModeConfigurationBasedSnapshotOnDataError;
    }

    public boolean isSnapshotModeConfigurationBasedSnapshotOnDataError() {
        return snapshotModeConfigurationBasedSnapshotOnDataError;
    }

    /**
     * The path to the file that will be used to record the database schema
     * history
     */
    public void setSchemaHistoryInternalFileFilename(
            String schemaHistoryInternalFileFilename) {
        this.schemaHistoryInternalFileFilename = schemaHistoryInternalFileFilename;
    }

    public String getSchemaHistoryInternalFileFilename() {
        return schemaHistoryInternalFileFilename;
    }

    /**
     * Whether delete operations should be represented by a delete event and a
     * subsequent tombstone event (true) or only by a delete event (false).
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
     * Topic prefix that identifies and provides a namespace for the particular
     * database server/cluster is capturing changes. The topic prefix should be
     * unique across all other connectors, since it is used as a prefix for all
     * Kafka topic names that receive events emitted by this connector. Only
     * alphanumeric characters, hyphens, dots and underscores must be accepted.
     */
    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    /**
     * Specify how DECIMAL and NUMERIC columns should be represented in change
     * events, including: 'precise' (the default) uses java.math.BigDecimal to
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
     * Specify how binary (blob, binary, etc.) columns should be represented in
     * change events, including: 'bytes' represents binary data as byte array
     * (default); 'base64' represents binary data as base64-encoded string;
     * 'base64-url-safe' represents binary data as base64-url-safe-encoded
     * string; 'hex' represents binary data as hex-encoded (base16) string
     */
    public void setBinaryHandlingMode(String binaryHandlingMode) {
        this.binaryHandlingMode = binaryHandlingMode;
    }

    public String getBinaryHandlingMode() {
        return binaryHandlingMode;
    }

    /**
     * Whether the connector parse table and column's comment to metadata
     * object. Note: Enable this option will bring the implications on memory
     * usage. The number and size of ColumnImpl objects is what largely impacts
     * how much memory is consumed by the Debezium connectors, and adding a
     * String to each of them can potentially be quite heavy. The default is
     * 'false'.
     */
    public void setIncludeSchemaComments(boolean includeSchemaComments) {
        this.includeSchemaComments = includeSchemaComments;
    }

    public boolean isIncludeSchemaComments() {
        return includeSchemaComments;
    }

    /**
     * The name of the SourceInfoStructMaker class that returns SourceInfo
     * schema and struct.
     */
    public void setSourceinfoStructMaker(String sourceinfoStructMaker) {
        this.sourceinfoStructMaker = sourceinfoStructMaker;
    }

    public String getSourceinfoStructMaker() {
        return sourceinfoStructMaker;
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
     * Add OPTION(RECOMPILE) on each SELECT statement during the incremental
     * snapshot process. This prevents parameter sniffing but can cause CPU
     * pressure on the source database.
     */
    public void setIncrementalSnapshotOptionRecompile(
            boolean incrementalSnapshotOptionRecompile) {
        this.incrementalSnapshotOptionRecompile = incrementalSnapshotOptionRecompile;
    }

    public boolean isIncrementalSnapshotOptionRecompile() {
        return incrementalSnapshotOptionRecompile;
    }

    /**
     * Enable Debezium to emit data lineage metadata through OpenLineage API
     */
    public void setOpenlineageIntegrationEnabled(
            boolean openlineageIntegrationEnabled) {
        this.openlineageIntegrationEnabled = openlineageIntegrationEnabled;
    }

    public boolean isOpenlineageIntegrationEnabled() {
        return openlineageIntegrationEnabled;
    }

    /**
     * This setting must be set to specify a list of tables/collections whose
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
     * When 'snapshot.mode' is set as configuration_based, this setting permits
     * to specify whenever the stream should start or not after snapshot.
     */
    public void setSnapshotModeConfigurationBasedStartStream(
            boolean snapshotModeConfigurationBasedStartStream) {
        this.snapshotModeConfigurationBasedStartStream = snapshotModeConfigurationBasedStartStream;
    }

    public boolean isSnapshotModeConfigurationBasedStartStream() {
        return snapshotModeConfigurationBasedStartStream;
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
     * When 'snapshot.mode' is set as configuration_based, this setting permits
     * to specify whenever the schema should be snapshotted or not.
     */
    public void setSnapshotModeConfigurationBasedSnapshotSchema(
            boolean snapshotModeConfigurationBasedSnapshotSchema) {
        this.snapshotModeConfigurationBasedSnapshotSchema = snapshotModeConfigurationBasedSnapshotSchema;
    }

    public boolean isSnapshotModeConfigurationBasedSnapshotSchema() {
        return snapshotModeConfigurationBasedSnapshotSchema;
    }

    /**
     * Time, date, and timestamps can be represented with different kinds of
     * precisions, including: 'adaptive' (the default) bases the precision of
     * time, date, and timestamp values on the database column's precision;
     * 'adaptive_time_microseconds' like 'adaptive' mode, but TIME fields always
     * use microseconds precision; 'connect' always represents time, date, and
     * timestamp values using Kafka Connect's built-in representations for Time,
     * Date, and Timestamp, which uses millisecond precision regardless of the
     * database columns' precision.
     */
    public void setTimePrecisionMode(String timePrecisionMode) {
        this.timePrecisionMode = timePrecisionMode;
    }

    public String getTimePrecisionMode() {
        return timePrecisionMode;
    }

    /**
     * Interval for looking for new signals in registered channels, given in
     * milliseconds. Defaults to 5 seconds.
     */
    public void setSignalPollIntervalMs(long signalPollIntervalMs) {
        this.signalPollIntervalMs = signalPollIntervalMs;
    }

    public long getSignalPollIntervalMs() {
        return signalPollIntervalMs;
    }

    /**
     * Optional list of post processors. The processors are defined using
     * '<post.processor.prefix>.type' config option and configured using options
     * '<post.processor.prefix.<option>'
     */
    public void setPostProcessors(String postProcessors) {
        this.postProcessors = postProcessors;
    }

    public String getPostProcessors() {
        return postProcessors;
    }

    /**
     * List of notification channels names that are enabled.
     */
    public void setNotificationEnabledChannels(
            String notificationEnabledChannels) {
        this.notificationEnabledChannels = notificationEnabledChannels;
    }

    public String getNotificationEnabledChannels() {
        return notificationEnabledChannels;
    }

    /**
     * Specify how failures during processing of events (i.e. when encountering
     * a corrupted event) should be handled, including: 'fail' (the default) an
     * exception indicating the problematic event and its position is raised,
     * causing the connector to be stopped; 'warn' the problematic event and its
     * position will be logged and the event will be skipped; 'ignore' the
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
     * Controls which transaction isolation level is used and how long the
     * connector locks the captured tables. The default is 'repeatable_read',
     * which means that repeatable read isolation level is used. In addition,
     * type of acquired lock during schema snapshot depends on
     * `snapshot.locking.mode` property. Using a value of 'exclusive' ensures
     * that the connector holds the type of lock specified with
     * `snapshot.locking.mode` property (and thus prevents any reads and
     * updates) for all captured tables during the entire snapshot duration.
     * When 'snapshot' is specified, connector runs the initial snapshot in
     * SNAPSHOT isolation level, which guarantees snapshot consistency. In
     * addition, neither table nor row-level locks are held. When
     * 'read_committed' is specified, connector runs the initial snapshot in
     * READ COMMITTED isolation level. No long-running locks are taken, so that
     * initial snapshot does not prevent other transactions from updating table
     * rows. Snapshot consistency is not guaranteed.In 'read_uncommitted' mode
     * neither table nor row-level locks are acquired, but connector does not
     * guarantee snapshot consistency.
     */
    public void setSnapshotIsolationMode(String snapshotIsolationMode) {
        this.snapshotIsolationMode = snapshotIsolationMode;
    }

    public String getSnapshotIsolationMode() {
        return snapshotIsolationMode;
    }

    /**
     * The maximum number of threads used to perform the snapshot. Defaults to
     * 1.
     */
    public void setSnapshotMaxThreads(int snapshotMaxThreads) {
        this.snapshotMaxThreads = snapshotMaxThreads;
    }

    public int getSnapshotMaxThreads() {
        return snapshotMaxThreads;
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
     * The name of the topic for the notifications. This is required in case
     * 'sink' is in the list of enabled channels
     */
    public void setNotificationSinkTopicName(String notificationSinkTopicName) {
        this.notificationSinkTopicName = notificationSinkTopicName;
    }

    public String getNotificationSinkTopicName() {
        return notificationSinkTopicName;
    }

    /**
     * When 'snapshot.mode' is set as custom, this setting must be set to
     * specify a the name of the custom implementation provided in the 'name()'
     * method. The implementations must implement the 'Snapshotter' interface
     * and is called on each app boot to determine whether to do a snapshot.
     */
    public void setSnapshotModeCustomName(String snapshotModeCustomName) {
        this.snapshotModeCustomName = snapshotModeCustomName;
    }

    public String getSnapshotModeCustomName() {
        return snapshotModeCustomName;
    }

    /**
     * The name of the SchemaHistory class that should be used to store and
     * recover database schema changes. The configuration properties for the
     * history are prefixed with the 'schema.history.internal.' string.
     */
    public void setSchemaHistoryInternal(String schemaHistoryInternal) {
        this.schemaHistoryInternal = schemaHistoryInternal;
    }

    public String getSchemaHistoryInternal() {
        return schemaHistoryInternal;
    }

    /**
     * This property can be used to reduce the connector memory usage footprint
     * when changes are streamed from multiple tables per database.
     */
    public void setMaxIterationTransactions(int maxIterationTransactions) {
        this.maxIterationTransactions = maxIterationTransactions;
    }

    public int getMaxIterationTransactions() {
        return maxIterationTransactions;
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
     * message converter used by the connector, including: 'avro' replaces the
     * characters that cannot be used in the Avro type name with underscore;
     * 'avro_unicode' replaces the underscore or characters that cannot be used
     * in the Avro type name with corresponding unicode like _uxxxx. Note: _ is
     * an escape sequence like backslash in Java;'none' does not apply any
     * adjustment (default)
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

    /**
     * The maximum time in milliseconds to wait for connection validation to
     * complete. Defaults to 60 seconds.
     */
    public void setConnectionValidationTimeoutMs(
            long connectionValidationTimeoutMs) {
        this.connectionValidationTimeoutMs = connectionValidationTimeoutMs;
    }

    public long getConnectionValidationTimeoutMs() {
        return connectionValidationTimeoutMs;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "transaction.metadata.factory", transactionMetadataFactory);
        addPropertyIfNotNull(configBuilder, "streaming.delay.ms", streamingDelayMs);
        addPropertyIfNotNull(configBuilder, "custom.metric.tags", customMetricTags);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.namespace", openlineageIntegrationJobNamespace);
        addPropertyIfNotNull(configBuilder, "database.query.timeout.ms", databaseQueryTimeoutMs);
        addPropertyIfNotNull(configBuilder, "data.query.mode", dataQueryMode);
        addPropertyIfNotNull(configBuilder, "signal.enabled.channels", signalEnabledChannels);
        addPropertyIfNotNull(configBuilder, "database.instance", databaseInstance);
        addPropertyIfNotNull(configBuilder, "include.schema.changes", includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, "heartbeat.action.query", heartbeatActionQuery);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.tags", openlineageIntegrationJobTags);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "datatype.propagate.source.type", datatypePropagateSourceType);
        addPropertyIfNotNull(configBuilder, "database.names", databaseNames);
        addPropertyIfNotNull(configBuilder, "snapshot.tables.order.by.row.count", snapshotTablesOrderByRowCount);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.watermarking.strategy", incrementalSnapshotWatermarkingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.on.schema.error", snapshotModeConfigurationBasedSnapshotOnSchemaError);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.allow.schema.changes", incrementalSnapshotAllowSchemaChanges);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.skip.unparseable.ddl", schemaHistoryInternalSkipUnparseableDdl);
        addPropertyIfNotNull(configBuilder, "column.include.list", columnIncludeList);
        addPropertyIfNotNull(configBuilder, "column.propagate.source.type", columnPropagateSourceType);
        addPropertyIfNotNull(configBuilder, "errors.max.retries", errorsMaxRetries);
        addPropertyIfNotNull(configBuilder, "streaming.fetch.size", streamingFetchSize);
        addPropertyIfNotNull(configBuilder, "table.exclude.list", tableExcludeList);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.description", openlineageIntegrationJobDescription);
        addPropertyIfNotNull(configBuilder, "topic.naming.strategy", topicNamingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.data", snapshotModeConfigurationBasedSnapshotData);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.chunk.size", incrementalSnapshotChunkSize);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.owners", openlineageIntegrationJobOwners);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.config.file.path", openlineageIntegrationConfigFilePath);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "executor.shutdown.timeout.ms", executorShutdownTimeoutMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.tables.ddl", schemaHistoryInternalStoreOnlyCapturedTablesDdl);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.databases.ddl", schemaHistoryInternalStoreOnlyCapturedDatabasesDdl);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.on.data.error", snapshotModeConfigurationBasedSnapshotOnDataError);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.file.filename", schemaHistoryInternalFileFilename);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "topic.prefix", topicPrefix);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "binary.handling.mode", binaryHandlingMode);
        addPropertyIfNotNull(configBuilder, "include.schema.comments", includeSchemaComments);
        addPropertyIfNotNull(configBuilder, "sourceinfo.struct.maker", sourceinfoStructMaker);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.option.recompile", incrementalSnapshotOptionRecompile);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.enabled", openlineageIntegrationEnabled);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.start.stream", snapshotModeConfigurationBasedStartStream);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.schema", snapshotModeConfigurationBasedSnapshotSchema);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "signal.poll.interval.ms", signalPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "post.processors", postProcessors);
        addPropertyIfNotNull(configBuilder, "notification.enabled.channels", notificationEnabledChannels);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.isolation.mode", snapshotIsolationMode);
        addPropertyIfNotNull(configBuilder, "snapshot.max.threads", snapshotMaxThreads);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "notification.sink.topic.name", notificationSinkTopicName);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.custom.name", snapshotModeCustomName);
        addPropertyIfNotNull(configBuilder, "schema.history.internal", schemaHistoryInternal);
        addPropertyIfNotNull(configBuilder, "max.iteration.transactions", maxIterationTransactions);
        addPropertyIfNotNull(configBuilder, "column.exclude.list", columnExcludeList);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "table.include.list", tableIncludeList);
        addPropertyIfNotNull(configBuilder, "connection.validation.timeout.ms", connectionValidationTimeoutMs);
        
        return configBuilder.build();
    }

    @Override
    protected Class configureConnectorClass() {
        return SqlServerConnector.class;
    }

    @Override
    protected ConfigurationValidation validateConnectorConfiguration() {
        if (isFieldValueNotSet(databasePassword)) {
        	return ConfigurationValidation.notValid("Required field 'databasePassword' must be set.");
        }
        if (isFieldValueNotSet(topicPrefix)) {
        	return ConfigurationValidation.notValid("Required field 'topicPrefix' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "sqlserver";
    }
}