package org.apache.camel.component.debezium.configuration;

import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class MySqlConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,mysql";
    @UriParam(label = LABEL_NAME, defaultValue = "minimal")
    private String snapshotLockingMode = "minimal";
    @UriParam(label = LABEL_NAME)
    private String messageKeyColumns;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int queryFetchSize = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "source")
    private String signalEnabledChannels = "source";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    @UriParam(label = LABEL_NAME)
    private String gtidSourceIncludes;
    @UriParam(label = LABEL_NAME, defaultValue = "com.mysql.cj.jdbc.Driver")
    private String databaseJdbcDriver = "com.mysql.cj.jdbc.Driver";
    @UriParam(label = LABEL_NAME)
    private String heartbeatActionQuery;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME)
    private String databaseInitialStatements;
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int binlogBufferSize = 0;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String datatypePropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "disabled")
    private String snapshotTablesOrderByRowCount = "disabled";
    @UriParam(label = LABEL_NAME)
    private String gtidSourceExcludes;
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME)
    private String databaseSslKeystore;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    private String databaseSslTruststorePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean incrementalSnapshotAllowSchemaChanges = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalSkipUnparseableDdl = false;
    @UriParam(label = LABEL_NAME)
    private String columnIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean enableTimeAdjuster = true;
    @UriParam(label = LABEL_NAME)
    private String columnPropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String inconsistentSchemaHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "1000")
    private int minRowCountToStreamResults = 1000;
    @UriParam(label = LABEL_NAME, defaultValue = "-1")
    private int errorsMaxRetries = -1;
    @UriParam(label = LABEL_NAME)
    private String tableExcludeList;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME)
    private String databaseExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean gtidSourceFilterDmlEvents = true;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "t")
    private String skippedOperations = "t";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.schema.SchemaTopicNamingStrategy")
    private String topicNamingStrategy = "io.debezium.schema.SchemaTopicNamingStrategy";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean connectKeepAlive = true;
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "30s", javaType = "java.time.Duration")
    private int connectTimeoutMs = 30000;
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME, defaultValue = "1024")
    private int incrementalSnapshotChunkSize = 1024;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean schemaHistoryInternalStoreOnlyCapturedDatabasesDdl = true;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalStoreOnlyCapturedTablesDdl = false;
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
    @UriParam(label = LABEL_NAME, defaultValue = "off")
    private String snapshotNewTables = "off";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.connector.mysql.MySqlSourceInfoStructMaker")
    private String sourceinfoStructMaker = "io.debezium.connector.mysql.MySqlSourceInfoStructMaker";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME, defaultValue = "long")
    private String bigintUnsignedHandlingMode = "long";
    @UriParam(label = LABEL_NAME)
    private long databaseServerId;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive_time_microseconds")
    private String timePrecisionMode = "adaptive_time_microseconds";
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private long signalPollIntervalMs = 5000;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventDeserializationFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME)
    private String notificationEnabledChannels;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int snapshotMaxThreads = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "3306")
    private int databasePort = 3306;
    @UriParam(label = LABEL_NAME)
    private String databaseSslTruststore;
    @UriParam(label = LABEL_NAME)
    private String notificationSinkTopicName;
    @UriParam(label = LABEL_NAME, defaultValue = "preferred")
    private String databaseSslMode = "preferred";
    @UriParam(label = LABEL_NAME)
    private String databaseSslKeystorePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.storage.kafka.history.KafkaSchemaHistory")
    private String schemaHistoryInternal = "io.debezium.storage.kafka.history.KafkaSchemaHistory";
    @UriParam(label = LABEL_NAME)
    private String columnExcludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String schemaNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private long databaseServerIdOffset = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "1m", javaType = "java.time.Duration")
    private long connectKeepAliveIntervalMs = 60000;
    @UriParam(label = LABEL_NAME)
    private String tableIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeQuery = false;
    @UriParam(label = LABEL_NAME)
    private String databaseIncludeList;

    /**
     * Controls how long the connector holds onto the global read lock while it
     * is performing a snapshot. The default is 'minimal', which means the
     * connector holds the global read lock (and thus prevents any updates) for
     * just the initial portion of the snapshot while the database schemas and
     * other metadata are being read. The remaining work in a snapshot involves
     * selecting all rows from each table, and this can be done using the
     * snapshot process' REPEATABLE READ transaction even when the lock is no
     * longer held and other operations are updating the database. However, in
     * some cases it may be desirable to block all writes for the entire
     * duration of the snapshot; in such cases set this property to 'extended'.
     * Using a value of 'none' will prevent the connector from acquiring any
     * table locks during the snapshot process. This mode can only be used in
     * combination with snapshot.mode values of 'schema_only' or
     * 'schema_only_recovery' and is only safe to use if no schema changes are
     * happening while the snapshot is taken.
     */
    public void setSnapshotLockingMode(String snapshotLockingMode) {
        this.snapshotLockingMode = snapshotLockingMode;
    }

    public String getSnapshotLockingMode() {
        return snapshotLockingMode;
    }

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
     * The maximum number of records that should be loaded into memory while
     * streaming. A value of '0' uses the default JDBC fetch size.
     */
    public void setQueryFetchSize(int queryFetchSize) {
        this.queryFetchSize = queryFetchSize;
    }

    public int getQueryFetchSize() {
        return queryFetchSize;
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
     * The source UUIDs used to include GTID ranges when determine the starting
     * position in the MySQL server's binlog.
     */
    public void setGtidSourceIncludes(String gtidSourceIncludes) {
        this.gtidSourceIncludes = gtidSourceIncludes;
    }

    public String getGtidSourceIncludes() {
        return gtidSourceIncludes;
    }

    /**
     * JDBC Driver class name used to connect to the MySQL database server.
     */
    public void setDatabaseJdbcDriver(String databaseJdbcDriver) {
        this.databaseJdbcDriver = databaseJdbcDriver;
    }

    public String getDatabaseJdbcDriver() {
        return databaseJdbcDriver;
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
     * A semicolon separated list of SQL statements to be executed when a JDBC
     * connection (not binlog reading connection) to the database is
     * established. Note that the connector may establish JDBC connections at
     * its own discretion, so this should typically be used for configuration of
     * session parameters only, but not for executing DML statements. Use
     * doubled semicolon (';;') to use a semicolon as a character and not as a
     * delimiter.
     */
    public void setDatabaseInitialStatements(String databaseInitialStatements) {
        this.databaseInitialStatements = databaseInitialStatements;
    }

    public String getDatabaseInitialStatements() {
        return databaseInitialStatements;
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
     * The size of a look-ahead buffer used by the  binlog reader to decide
     * whether the transaction in progress is going to be committed or rolled
     * back. Use 0 to disable look-ahead buffering. Defaults to 0 (i.e.
     * buffering is disabled).
     */
    public void setBinlogBufferSize(int binlogBufferSize) {
        this.binlogBufferSize = binlogBufferSize;
    }

    public int getBinlogBufferSize() {
        return binlogBufferSize;
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
     * The source UUIDs used to exclude GTID ranges when determine the starting
     * position in the MySQL server's binlog.
     */
    public void setGtidSourceExcludes(String gtidSourceExcludes) {
        this.gtidSourceExcludes = gtidSourceExcludes;
    }

    public String getGtidSourceExcludes() {
        return gtidSourceExcludes;
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
     * The location of the key store file. This is optional and can be used for
     * two-way authentication between the client and the MySQL Server.
     */
    public void setDatabaseSslKeystore(String databaseSslKeystore) {
        this.databaseSslKeystore = databaseSslKeystore;
    }

    public String getDatabaseSslKeystore() {
        return databaseSslKeystore;
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
     * The password for the trust store file. Used to check the integrity of the
     * truststore, and unlock the truststore.
     */
    public void setDatabaseSslTruststorePassword(
            String databaseSslTruststorePassword) {
        this.databaseSslTruststorePassword = databaseSslTruststorePassword;
    }

    public String getDatabaseSslTruststorePassword() {
        return databaseSslTruststorePassword;
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
     * MySQL allows user to insert year value as either 2-digit or 4-digit. In
     * case of two digit the value is automatically mapped into 1970 -
     * 2069.false - delegates the implicit conversion to the databasetrue - (the
     * default) Debezium makes the conversion
     */
    public void setEnableTimeAdjuster(boolean enableTimeAdjuster) {
        this.enableTimeAdjuster = enableTimeAdjuster;
    }

    public boolean isEnableTimeAdjuster() {
        return enableTimeAdjuster;
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
     * Specify how binlog events that belong to a table missing from internal
     * schema representation (i.e. internal representation is not consistent
     * with database) should be handled, including: 'fail' (the default) an
     * exception indicating the problematic event and its binlog position is
     * raised, causing the connector to be stopped; 'warn' the problematic event
     * and its binlog position will be logged and the event will be skipped;
     * 'skip' the problematic event will be skipped.
     */
    public void setInconsistentSchemaHandlingMode(
            String inconsistentSchemaHandlingMode) {
        this.inconsistentSchemaHandlingMode = inconsistentSchemaHandlingMode;
    }

    public String getInconsistentSchemaHandlingMode() {
        return inconsistentSchemaHandlingMode;
    }

    /**
     * The number of rows a table must contain to stream results rather than
     * pull all into memory during snapshots. Defaults to 1,000. Use 0 to stream
     * all results and completely avoid checking the size of each table.
     */
    public void setMinRowCountToStreamResults(int minRowCountToStreamResults) {
        this.minRowCountToStreamResults = minRowCountToStreamResults;
    }

    public int getMinRowCountToStreamResults() {
        return minRowCountToStreamResults;
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
     * A comma-separated list of regular expressions that match database names
     * to be excluded from monitoring
     */
    public void setDatabaseExcludeList(String databaseExcludeList) {
        this.databaseExcludeList = databaseExcludeList;
    }

    public String getDatabaseExcludeList() {
        return databaseExcludeList;
    }

    /**
     * If set to true, we will only produce DML events into Kafka for
     * transactions that were written on mysql servers with UUIDs matching the
     * filters defined by the gtid.source.includes or gtid.source.excludes
     * configuration options, if they are specified.
     */
    public void setGtidSourceFilterDmlEvents(boolean gtidSourceFilterDmlEvents) {
        this.gtidSourceFilterDmlEvents = gtidSourceFilterDmlEvents;
    }

    public boolean isGtidSourceFilterDmlEvents() {
        return gtidSourceFilterDmlEvents;
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
     * Whether a separate thread should be used to ensure the connection is kept
     * alive.
     */
    public void setConnectKeepAlive(boolean connectKeepAlive) {
        this.connectKeepAlive = connectKeepAlive;
    }

    public boolean isConnectKeepAlive() {
        return connectKeepAlive;
    }

    /**
     * The criteria for running a snapshot upon startup of the connector. Select
     * one of the following snapshot options: 'when_needed': On startup, the
     * connector runs a snapshot if one is needed.; 'schema_only': If the
     * connector does not detect any offsets for the logical server name, it
     * runs a snapshot that captures only the schema (table structures), but not
     * any table data. After the snapshot completes, the connector begins to
     * stream changes from the binlog.; 'schema_only_recovery': The connector
     * performs a snapshot that captures only the database schema history. The
     * connector then transitions back to streaming. Use this setting to restore
     * a corrupted or lost database schema history topic. Do not use if the
     * database schema was modified after the connector stopped.; 'initial'
     * (default): If the connector does not detect any offsets for the logical
     * server name, it runs a snapshot that captures the current full state of
     * the configured tables. After the snapshot completes, the connector begins
     * to stream changes from the binlog.; 'initial_only': The connector
     * performs a snapshot as it does for the 'initial' option, but after the
     * connector completes the snapshot, it stops, and does not stream changes
     * from the binlog.; 'never': The connector does not run a snapshot. Upon
     * first startup, the connector immediately begins reading from the
     * beginning of the binlog. The 'never' mode should be used with care, and
     * only when the binlog is known to contain all history.
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * Maximum time to wait after trying to connect to the database before
     * timing out, given in milliseconds. Defaults to 30 seconds (30,000 ms).
     */
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
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
     * default (true) only DDL that manipulates a table from captured
     * schema/database will be stored. If set to false, then Debezium will store
     * all incoming DDL statements.
     */
    public void setSchemaHistoryInternalStoreOnlyCapturedDatabasesDdl(
            boolean schemaHistoryInternalStoreOnlyCapturedDatabasesDdl) {
        this.schemaHistoryInternalStoreOnlyCapturedDatabasesDdl = schemaHistoryInternalStoreOnlyCapturedDatabasesDdl;
    }

    public boolean isSchemaHistoryInternalStoreOnlyCapturedDatabasesDdl() {
        return schemaHistoryInternalStoreOnlyCapturedDatabasesDdl;
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
     * BETA FEATURE: On connector restart, the connector will check if there
     * have been any new tables added to the configuration, and snapshot them.
     * There is presently only two options: 'off': Default behavior. Do not
     * snapshot new tables. 'parallel': The snapshot of the new tables will
     * occur in parallel to the continued binlog reading of the old tables. When
     * the snapshot completes, an independent binlog reader will begin reading
     * the events for the new tables until it catches up to present time. At
     * this point, both old and new binlog readers will be momentarily halted
     * and new binlog reader will start that will read the binlog for all
     * configured tables. The parallel binlog reader will have a configured
     * server id of 10000 + the primary binlog reader's server id.
     */
    public void setSnapshotNewTables(String snapshotNewTables) {
        this.snapshotNewTables = snapshotNewTables;
    }

    public String getSnapshotNewTables() {
        return snapshotNewTables;
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
     * Specify how BIGINT UNSIGNED columns should be represented in change
     * events, including: 'precise' uses java.math.BigDecimal to represent
     * values, which are encoded in the change events using a binary
     * representation and Kafka Connect's
     * 'org.apache.kafka.connect.data.Decimal' type; 'long' (the default)
     * represents values using Java's 'long', which may not offer the precision
     * but will be far easier to use in consumers.
     */
    public void setBigintUnsignedHandlingMode(String bigintUnsignedHandlingMode) {
        this.bigintUnsignedHandlingMode = bigintUnsignedHandlingMode;
    }

    public String getBigintUnsignedHandlingMode() {
        return bigintUnsignedHandlingMode;
    }

    /**
     * A numeric ID of this database client, which must be unique across all
     * currently-running database processes in the cluster. This connector joins
     * the MySQL database cluster as another server (with this unique ID) so it
     * can read the binlog.
     */
    public void setDatabaseServerId(long databaseServerId) {
        this.databaseServerId = databaseServerId;
    }

    public long getDatabaseServerId() {
        return databaseServerId;
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
     * Time, date and timestamps can be represented with different kinds of
     * precisions, including: 'adaptive_time_microseconds': the precision of
     * date and timestamp values is based the database column's precision; but
     * time fields always use microseconds precision; 'connect': always
     * represents time, date and timestamp values using Kafka Connect's built-in
     * representations for Time, Date, and Timestamp, which uses millisecond
     * precision regardless of the database columns' precision.
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
     * Specify how failures during deserialization of binlog events (i.e. when
     * encountering a corrupted event) should be handled, including: 'fail' (the
     * default) an exception indicating the problematic event and its binlog
     * position is raised, causing the connector to be stopped; 'warn' the
     * problematic event and its binlog position will be logged and the event
     * will be skipped; 'ignore' the problematic event will be skipped.
     */
    public void setEventDeserializationFailureHandlingMode(
            String eventDeserializationFailureHandlingMode) {
        this.eventDeserializationFailureHandlingMode = eventDeserializationFailureHandlingMode;
    }

    public String getEventDeserializationFailureHandlingMode() {
        return eventDeserializationFailureHandlingMode;
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
     * The location of the trust store file for the server certificate
     * verification.
     */
    public void setDatabaseSslTruststore(String databaseSslTruststore) {
        this.databaseSslTruststore = databaseSslTruststore;
    }

    public String getDatabaseSslTruststore() {
        return databaseSslTruststore;
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
     * Whether to use an encrypted connection to MySQL. Options include:
     * 'disabled' to use an unencrypted connection; 'preferred' (the default) to
     * establish a secure (encrypted) connection if the server supports secure
     * connections, but fall back to an unencrypted connection otherwise;
     * 'required' to use a secure (encrypted) connection, and fail if one cannot
     * be established; 'verify_ca' like 'required' but additionally verify the
     * server TLS certificate against the configured Certificate Authority (CA)
     * certificates, or fail if no valid matching CA certificates are found;
     * or'verify_identity' like 'verify_ca' but additionally verify that the
     * server certificate matches the host to which the connection is attempted.
     */
    public void setDatabaseSslMode(String databaseSslMode) {
        this.databaseSslMode = databaseSslMode;
    }

    public String getDatabaseSslMode() {
        return databaseSslMode;
    }

    /**
     * The password for the key store file. This is optional and only needed if
     * 'database.ssl.keystore' is configured.
     */
    public void setDatabaseSslKeystorePassword(
            String databaseSslKeystorePassword) {
        this.databaseSslKeystorePassword = databaseSslKeystorePassword;
    }

    public String getDatabaseSslKeystorePassword() {
        return databaseSslKeystorePassword;
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
     * Only relevant if parallel snapshotting is configured. During parallel
     * snapshotting, multiple (4) connections open to the database client, and
     * they each need their own unique connection ID. This offset is used to
     * generate those IDs from the base configured cluster ID.
     */
    public void setDatabaseServerIdOffset(long databaseServerIdOffset) {
        this.databaseServerIdOffset = databaseServerIdOffset;
    }

    public long getDatabaseServerIdOffset() {
        return databaseServerIdOffset;
    }

    /**
     * Interval for connection checking if keep alive thread is used, given in
     * milliseconds Defaults to 1 minute (60,000 ms).
     */
    public void setConnectKeepAliveIntervalMs(long connectKeepAliveIntervalMs) {
        this.connectKeepAliveIntervalMs = connectKeepAliveIntervalMs;
    }

    public long getConnectKeepAliveIntervalMs() {
        return connectKeepAliveIntervalMs;
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
     * Whether the connector should include the original SQL query that
     * generated the change event. Note: This option requires MySQL be
     * configured with the binlog_rows_query_log_events option set to ON. Query
     * will not be present for events generated from snapshot. WARNING: Enabling
     * this option may expose tables or fields explicitly excluded or masked by
     * including the original SQL statement in the change event. For this reason
     * the default value is 'false'.
     */
    public void setIncludeQuery(boolean includeQuery) {
        this.includeQuery = includeQuery;
    }

    public boolean isIncludeQuery() {
        return includeQuery;
    }

    /**
     * The databases for which changes are to be captured
     */
    public void setDatabaseIncludeList(String databaseIncludeList) {
        this.databaseIncludeList = databaseIncludeList;
    }

    public String getDatabaseIncludeList() {
        return databaseIncludeList;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "snapshot.locking.mode", snapshotLockingMode);
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "signal.enabled.channels", signalEnabledChannels);
        addPropertyIfNotNull(configBuilder, "include.schema.changes", includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, "gtid.source.includes", gtidSourceIncludes);
        addPropertyIfNotNull(configBuilder, "database.jdbc.driver", databaseJdbcDriver);
        addPropertyIfNotNull(configBuilder, "heartbeat.action.query", heartbeatActionQuery);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "database.initial.statements", databaseInitialStatements);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "binlog.buffer.size", binlogBufferSize);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "datatype.propagate.source.type", datatypePropagateSourceType);
        addPropertyIfNotNull(configBuilder, "snapshot.tables.order.by.row.count", snapshotTablesOrderByRowCount);
        addPropertyIfNotNull(configBuilder, "gtid.source.excludes", gtidSourceExcludes);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "database.ssl.keystore", databaseSslKeystore);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "database.ssl.truststore.password", databaseSslTruststorePassword);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.allow.schema.changes", incrementalSnapshotAllowSchemaChanges);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.skip.unparseable.ddl", schemaHistoryInternalSkipUnparseableDdl);
        addPropertyIfNotNull(configBuilder, "column.include.list", columnIncludeList);
        addPropertyIfNotNull(configBuilder, "enable.time.adjuster", enableTimeAdjuster);
        addPropertyIfNotNull(configBuilder, "column.propagate.source.type", columnPropagateSourceType);
        addPropertyIfNotNull(configBuilder, "inconsistent.schema.handling.mode", inconsistentSchemaHandlingMode);
        addPropertyIfNotNull(configBuilder, "min.row.count.to.stream.results", minRowCountToStreamResults);
        addPropertyIfNotNull(configBuilder, "errors.max.retries", errorsMaxRetries);
        addPropertyIfNotNull(configBuilder, "table.exclude.list", tableExcludeList);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "database.exclude.list", databaseExcludeList);
        addPropertyIfNotNull(configBuilder, "gtid.source.filter.dml.events", gtidSourceFilterDmlEvents);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "topic.naming.strategy", topicNamingStrategy);
        addPropertyIfNotNull(configBuilder, "connect.keep.alive", connectKeepAlive);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "connect.timeout.ms", connectTimeoutMs);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.chunk.size", incrementalSnapshotChunkSize);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.databases.ddl", schemaHistoryInternalStoreOnlyCapturedDatabasesDdl);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.tables.ddl", schemaHistoryInternalStoreOnlyCapturedTablesDdl);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.file.filename", schemaHistoryInternalFileFilename);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "topic.prefix", topicPrefix);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "binary.handling.mode", binaryHandlingMode);
        addPropertyIfNotNull(configBuilder, "include.schema.comments", includeSchemaComments);
        addPropertyIfNotNull(configBuilder, "snapshot.new.tables", snapshotNewTables);
        addPropertyIfNotNull(configBuilder, "sourceinfo.struct.maker", sourceinfoStructMaker);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "bigint.unsigned.handling.mode", bigintUnsignedHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.server.id", databaseServerId);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "signal.poll.interval.ms", signalPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "event.deserialization.failure.handling.mode", eventDeserializationFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "notification.enabled.channels", notificationEnabledChannels);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.max.threads", snapshotMaxThreads);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "database.ssl.truststore", databaseSslTruststore);
        addPropertyIfNotNull(configBuilder, "notification.sink.topic.name", notificationSinkTopicName);
        addPropertyIfNotNull(configBuilder, "database.ssl.mode", databaseSslMode);
        addPropertyIfNotNull(configBuilder, "database.ssl.keystore.password", databaseSslKeystorePassword);
        addPropertyIfNotNull(configBuilder, "schema.history.internal", schemaHistoryInternal);
        addPropertyIfNotNull(configBuilder, "column.exclude.list", columnExcludeList);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "database.server.id.offset", databaseServerIdOffset);
        addPropertyIfNotNull(configBuilder, "connect.keep.alive.interval.ms", connectKeepAliveIntervalMs);
        addPropertyIfNotNull(configBuilder, "table.include.list", tableIncludeList);
        addPropertyIfNotNull(configBuilder, "include.query", includeQuery);
        addPropertyIfNotNull(configBuilder, "database.include.list", databaseIncludeList);
        
        return configBuilder.build();
    }

    @Override
    protected Class configureConnectorClass() {
        return MySqlConnector.class;
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
        return "mysql";
    }
}