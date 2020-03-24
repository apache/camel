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
    @UriParam(label = LABEL_NAME)
    private String columnBlacklist;
    @UriParam(label = LABEL_NAME)
    private String tableBlacklist;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    @UriParam(label = LABEL_NAME)
    private String gtidSourceIncludes;
    @UriParam(label = LABEL_NAME, defaultValue = "class com.mysql.cj.jdbc.Driver")
    private String databaseJdbcDriver = "com.mysql.cj.jdbc.Driver";
    @UriParam(label = LABEL_NAME, defaultValue = "100")
    private int databaseHistoryKafkaRecoveryPollIntervalMs = 100;
    @UriParam(label = LABEL_NAME, defaultValue = "500")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String databaseInitialStatements;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int binlogBufferSize = 0;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String gtidSourceExcludes;
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaBootstrapServers;
    @UriParam(label = LABEL_NAME)
    private String databaseSslKeystore;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "v2")
    private String sourceStructVersion = "v2";
    @UriParam(label = LABEL_NAME)
    private String databaseSslTruststorePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String inconsistentSchemaHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean enableTimeAdjuster = true;
    @UriParam(label = LABEL_NAME, defaultValue = "earliest")
    private String gtidNewChannelPosition = "earliest";
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean databaseHistoryStoreOnlyMonitoredTablesDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean gtidSourceFilterDmlEvents = true;
    @UriParam(label = LABEL_NAME)
    private String databaseBlacklist;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean connectKeepAlive = true;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.relational.history.FileDatabaseHistory")
    private String databaseHistory = "io.debezium.relational.history.FileDatabaseHistory";
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "30000")
    private int connectTimeoutMs = 30000;
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryKafkaTopic;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "100")
    private int databaseHistoryKafkaRecoveryAttempts = 100;
    @UriParam(label = LABEL_NAME)
    private String tableWhitelist;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "off")
    private String snapshotNewTables = "off";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean databaseHistorySkipUnparseableDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME)
    private String databaseWhitelist;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "long")
    private String bigintUnsignedHandlingMode = "long";
    @UriParam(label = LABEL_NAME)
    private long databaseServerId;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventDeserializationFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive_time_microseconds")
    private String timePrecisionMode = "adaptive_time_microseconds";
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseServerName;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "3306")
    private int databasePort = 3306;
    @UriParam(label = LABEL_NAME)
    private String databaseSslTruststore;
    @UriParam(label = LABEL_NAME, defaultValue = "disabled")
    private String databaseSslMode = "disabled";
    @UriParam(label = LABEL_NAME)
    private String databaseSslKeystorePassword;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private long databaseServerIdOffset = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "60000")
    private long connectKeepAliveIntervalMs = 60000;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeQuery = false;

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
     * Description is not available here, please check Debezium website for
     * corresponding key 'column.blacklist' description.
     */
    public void setColumnBlacklist(String columnBlacklist) {
        this.columnBlacklist = columnBlacklist;
    }

    public String getColumnBlacklist() {
        return columnBlacklist;
    }

    /**
     * Description is not available here, please check Debezium website for
     * corresponding key 'table.blacklist' description.
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
     * whose value includes the DDL statement(s).The default is 'true'. This is
     * independent of how the connector internally records database history.
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
     * Frequency in milliseconds to wait for new change events to appear after
     * receiving no events. Defaults to 500ms.
     */
    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * A semicolon separated list of SQL statements to be executed when a JDBC
     * connection (not binlog reading connection) to the database is
     * established. Note that the connector may establish JDBC connections at
     * its own discretion, so this should typically be used for configuration of
     * session parameters only,but not for executing DML statements. Use doubled
     * semicolon (';;') to use a semicolon as a character and not as a
     * delimiter.
     */
    public void setDatabaseInitialStatements(String databaseInitialStatements) {
        this.databaseInitialStatements = databaseInitialStatements;
    }

    public String getDatabaseInitialStatements() {
        return databaseInitialStatements;
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
     * performing a snapshot
     */
    public void setSnapshotFetchSize(int snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public int getSnapshotFetchSize() {
        return snapshotFetchSize;
    }

    /**
     * Name of the MySQL database user to be used when connecting to the
     * database.
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public String getDatabaseUser() {
        return databaseUser;
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
     * (DB_NAME.TABLE_NAME) or (SCHEMA_NAME.TABLE_NAME), depending on
     * thespecific connectors . Select statements for the individual tables are
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
     * Location of the Java keystore file containing an application process's
     * own certificate and private key.
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
     * Password to unlock the keystore file (store password) specified by
     * 'ssl.trustore' configuration property or the 'javax.net.ssl.trustStore'
     * system or JVM property.
     */
    public void setDatabaseSslTruststorePassword(
            String databaseSslTruststorePassword) {
        this.databaseSslTruststorePassword = databaseSslTruststorePassword;
    }

    public String getDatabaseSslTruststorePassword() {
        return databaseSslTruststorePassword;
    }

    /**
     * Specify how binlog events that belong to a table missing from internal
     * schema representation (i.e. internal representation is not consistent
     * with database) should be handled, including:'fail' (the default) an
     * exception indicating the problematic event and its binlog position is
     * raised, causing the connector to be stopped; 'warn' the problematic event
     * and its binlog position will be logged and the event will be
     * skipped;'skip' the problematic event will be skipped.
     */
    public void setInconsistentSchemaHandlingMode(
            String inconsistentSchemaHandlingMode) {
        this.inconsistentSchemaHandlingMode = inconsistentSchemaHandlingMode;
    }

    public String getInconsistentSchemaHandlingMode() {
        return inconsistentSchemaHandlingMode;
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
     * If set to 'latest', when connector sees new GTID, it will start consuming
     * gtid channel from the server latest executed gtid position. If 'earliest'
     * (the default) connector starts reading channel from first available (not
     * purged) gtid position on the server.
     */
    public void setGtidNewChannelPosition(String gtidNewChannelPosition) {
        this.gtidNewChannelPosition = gtidNewChannelPosition;
    }

    public String getGtidNewChannelPosition() {
        return gtidNewChannelPosition;
    }

    /**
     * Password of the MySQL database user to be used when connecting to the
     * database.
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    /**
     * Controls what DDL will Debezium store in database history.By default
     * (false) Debezium will store all incoming DDL statements. If set to
     * truethen only DDL that manipulates a monitored table will be stored.
     */
    public void setDatabaseHistoryStoreOnlyMonitoredTablesDdl(
            boolean databaseHistoryStoreOnlyMonitoredTablesDdl) {
        this.databaseHistoryStoreOnlyMonitoredTablesDdl = databaseHistoryStoreOnlyMonitoredTablesDdl;
    }

    public boolean isDatabaseHistoryStoreOnlyMonitoredTablesDdl() {
        return databaseHistoryStoreOnlyMonitoredTablesDdl;
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
     * Description is not available here, please check Debezium website for
     * corresponding key 'database.blacklist' description.
     */
    public void setDatabaseBlacklist(String databaseBlacklist) {
        this.databaseBlacklist = databaseBlacklist;
    }

    public String getDatabaseBlacklist() {
        return databaseBlacklist;
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
     * The criteria for running a snapshot upon startup of the connector.
     * Options include: 'when_needed' to specify that the connector run a
     * snapshot upon startup whenever it deems it necessary; 'initial' (the
     * default) to specify the connector can run a snapshot only when no offsets
     * are available for the logical server name; 'initial_only' same as
     * 'initial' except the connector should stop after completing the snapshot
     * and before it would normally read the binlog; and'never' to specify the
     * connector should never run a snapshot and that upon first startup the
     * connector should read from the beginning of the binlog. The 'never' mode
     * should be used with care, and only when the binlog is known to contain
     * all history.
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * Maximum time in milliseconds to wait after trying to connect to the
     * database before timing out.
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
     * The name of the topic for the database schema history
     */
    public void setDatabaseHistoryKafkaTopic(String databaseHistoryKafkaTopic) {
        this.databaseHistoryKafkaTopic = databaseHistoryKafkaTopic;
    }

    public String getDatabaseHistoryKafkaTopic() {
        return databaseHistoryKafkaTopic;
    }

    /**
     * The number of milliseconds to delay before a snapshot will begin.
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
     * The tables for which changes are to be captured
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
     * BETA FEATURE: On connector restart, the connector will check if there
     * have been any new tables added to the configuration, and snapshot them.
     * There is presently only two options:'off': Default behavior. Do not
     * snapshot new tables.'parallel': The snapshot of the new tables will occur
     * in parallel to the continued binlog reading of the old tables. When the
     * snapshot completes, an independent binlog reader will begin reading the
     * events for the new tables until it catches up to present time. At this
     * point, both old and new binlog readers will be momentarily halted and new
     * binlog reader will start that will read the binlog for all configured
     * tables. The parallel binlog reader will have a configured server id of
     * 10000 + the primary binlog reader's server id.
     */
    public void setSnapshotNewTables(String snapshotNewTables) {
        this.snapshotNewTables = snapshotNewTables;
    }

    public String getSnapshotNewTables() {
        return snapshotNewTables;
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
     * The databases for which changes are to be captured
     */
    public void setDatabaseWhitelist(String databaseWhitelist) {
        this.databaseWhitelist = databaseWhitelist;
    }

    public String getDatabaseWhitelist() {
        return databaseWhitelist;
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
     * Specify how BIGINT UNSIGNED columns should be represented in change
     * events, including:'precise' uses java.math.BigDecimal to represent
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
     * can read the binlog. By default, a random number is generated between
     * 5400 and 6400.
     */
    public void setDatabaseServerId(long databaseServerId) {
        this.databaseServerId = databaseServerId;
    }

    public long getDatabaseServerId() {
        return databaseServerId;
    }

    /**
     * Specify how failures during deserialization of binlog events (i.e. when
     * encountering a corrupted event) should be handled, including:'fail' (the
     * default) an exception indicating the problematic event and its binlog
     * position is raised, causing the connector to be stopped; 'warn' the
     * problematic event and its binlog position will be logged and the event
     * will be skipped;'ignore' the problematic event will be skipped.
     */
    public void setEventDeserializationFailureHandlingMode(
            String eventDeserializationFailureHandlingMode) {
        this.eventDeserializationFailureHandlingMode = eventDeserializationFailureHandlingMode;
    }

    public String getEventDeserializationFailureHandlingMode() {
        return eventDeserializationFailureHandlingMode;
    }

    /**
     * Time, date and timestamps can be represented with different kinds of
     * precisions, including:'adaptive_time_microseconds': the precision of date
     * and timestamp values is based the database column's precision; but time
     * fields always use microseconds precision;'connect': always represents
     * time, date and timestamp values using Kafka Connect's built-in
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
     * Port of the MySQL database server.
     */
    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    /**
     * Location of the Java truststore file containing the collection of CA
     * certificates trusted by this application process (trust store).
     */
    public void setDatabaseSslTruststore(String databaseSslTruststore) {
        this.databaseSslTruststore = databaseSslTruststore;
    }

    public String getDatabaseSslTruststore() {
        return databaseSslTruststore;
    }

    /**
     * Whether to use an encrypted connection to MySQL. Options
     * include'disabled' (the default) to use an unencrypted connection;
     * 'preferred' to establish a secure (encrypted) connection if the server
     * supports secure connections, but fall back to an unencrypted connection
     * otherwise; 'required' to use a secure (encrypted) connection, and fail if
     * one cannot be established; 'verify_ca' like 'required' but additionally
     * verify the server TLS certificate against the configured Certificate
     * Authority (CA) certificates, or fail if no valid matching CA certificates
     * are found; or'verify_identity' like 'verify_ca' but additionally verify
     * that the server certificate matches the host to which the connection is
     * attempted.
     */
    public void setDatabaseSslMode(String databaseSslMode) {
        this.databaseSslMode = databaseSslMode;
    }

    public String getDatabaseSslMode() {
        return databaseSslMode;
    }

    /**
     * Password to access the private key from the keystore file specified by
     * 'ssl.keystore' configuration property or the 'javax.net.ssl.keyStore'
     * system or JVM property. This password is used to unlock the keystore file
     * (store password), and to decrypt the private key stored in the keystore
     * (key password).
     */
    public void setDatabaseSslKeystorePassword(
            String databaseSslKeystorePassword) {
        this.databaseSslKeystorePassword = databaseSslKeystorePassword;
    }

    public String getDatabaseSslKeystorePassword() {
        return databaseSslKeystorePassword;
    }

    /**
     * Resolvable hostname or IP address of the MySQL database server.
     */
    public void setDatabaseHostname(String databaseHostname) {
        this.databaseHostname = databaseHostname;
    }

    public String getDatabaseHostname() {
        return databaseHostname;
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
     * Interval in milliseconds to wait for connection checking if keep alive
     * thread is used.
     */
    public void setConnectKeepAliveIntervalMs(long connectKeepAliveIntervalMs) {
        this.connectKeepAliveIntervalMs = connectKeepAliveIntervalMs;
    }

    public long getConnectKeepAliveIntervalMs() {
        return connectKeepAliveIntervalMs;
    }

    /**
     * Whether the connector should include the original SQL query that
     * generated the change event. Note: This option requires MySQL be
     * configured with the binlog_rows_query_log_events option set to ON. Query
     * will not be present for events generated from snapshot. WARNING: Enabling
     * this option may expose tables or fields explicitly blacklisted or masked
     * by including the original SQL statement in the change event. For this
     * reason the default value is 'false'.
     */
    public void setIncludeQuery(boolean includeQuery) {
        this.includeQuery = includeQuery;
    }

    public boolean isIncludeQuery() {
        return includeQuery;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "snapshot.locking.mode", snapshotLockingMode);
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "column.blacklist", columnBlacklist);
        addPropertyIfNotNull(configBuilder, "table.blacklist", tableBlacklist);
        addPropertyIfNotNull(configBuilder, "include.schema.changes", includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, "gtid.source.includes", gtidSourceIncludes);
        addPropertyIfNotNull(configBuilder, "database.jdbc.driver", databaseJdbcDriver);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.recovery.poll.interval.ms", databaseHistoryKafkaRecoveryPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "database.initial.statements", databaseInitialStatements);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "binlog.buffer.size", binlogBufferSize);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "gtid.source.excludes", gtidSourceExcludes);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.bootstrap.servers", databaseHistoryKafkaBootstrapServers);
        addPropertyIfNotNull(configBuilder, "database.ssl.keystore", databaseSslKeystore);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "source.struct.version", sourceStructVersion);
        addPropertyIfNotNull(configBuilder, "database.ssl.truststore.password", databaseSslTruststorePassword);
        addPropertyIfNotNull(configBuilder, "inconsistent.schema.handling.mode", inconsistentSchemaHandlingMode);
        addPropertyIfNotNull(configBuilder, "enable.time.adjuster", enableTimeAdjuster);
        addPropertyIfNotNull(configBuilder, "gtid.new.channel.position", gtidNewChannelPosition);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "database.history.store.only.monitored.tables.ddl", databaseHistoryStoreOnlyMonitoredTablesDdl);
        addPropertyIfNotNull(configBuilder, "gtid.source.filter.dml.events", gtidSourceFilterDmlEvents);
        addPropertyIfNotNull(configBuilder, "database.blacklist", databaseBlacklist);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "connect.keep.alive", connectKeepAlive);
        addPropertyIfNotNull(configBuilder, "database.history", databaseHistory);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "connect.timeout.ms", connectTimeoutMs);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.topic", databaseHistoryKafkaTopic);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "database.history.kafka.recovery.attempts", databaseHistoryKafkaRecoveryAttempts);
        addPropertyIfNotNull(configBuilder, "table.whitelist", tableWhitelist);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.new.tables", snapshotNewTables);
        addPropertyIfNotNull(configBuilder, "database.history.skip.unparseable.ddl", databaseHistorySkipUnparseableDdl);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "database.whitelist", databaseWhitelist);
        addPropertyIfNotNull(configBuilder, "database.history.file.filename", databaseHistoryFileFilename);
        addPropertyIfNotNull(configBuilder, "bigint.unsigned.handling.mode", bigintUnsignedHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.server.id", databaseServerId);
        addPropertyIfNotNull(configBuilder, "event.deserialization.failure.handling.mode", eventDeserializationFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "database.server.name", databaseServerName);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "database.ssl.truststore", databaseSslTruststore);
        addPropertyIfNotNull(configBuilder, "database.ssl.mode", databaseSslMode);
        addPropertyIfNotNull(configBuilder, "database.ssl.keystore.password", databaseSslKeystorePassword);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "database.server.id.offset", databaseServerIdOffset);
        addPropertyIfNotNull(configBuilder, "connect.keep.alive.interval.ms", connectKeepAliveIntervalMs);
        addPropertyIfNotNull(configBuilder, "include.query", includeQuery);
        
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
        if (isFieldValueNotSet(databaseServerName)) {
        	return ConfigurationValidation.notValid("Required field 'databaseServerName' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "mysql";
    }
}