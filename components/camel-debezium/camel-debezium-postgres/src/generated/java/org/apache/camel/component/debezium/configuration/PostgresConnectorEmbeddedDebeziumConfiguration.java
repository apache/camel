package org.apache.camel.component.debezium.configuration;

import io.debezium.config.Configuration;
import io.debezium.connector.postgresql.PostgresConnector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class PostgresConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,postgres";
    @UriParam(label = LABEL_NAME)
    private String messageKeyColumns;
    @UriParam(label = LABEL_NAME)
    private String customMetricTags;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int queryFetchSize = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "dbz_publication")
    private String publicationName = "dbz_publication";
    @UriParam(label = LABEL_NAME)
    private String schemaIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "source")
    private String signalEnabledChannels = "source";
    @UriParam(label = LABEL_NAME, defaultValue = "6")
    private int slotMaxRetries = 6;
    @UriParam(label = LABEL_NAME, defaultValue = "columns_diff")
    private String schemaRefreshMode = "columns_diff";
    @UriParam(label = LABEL_NAME, defaultValue = "prefer")
    private String databaseSslmode = "prefer";
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium_unavailable_value")
    private String unavailableValuePlaceholder = "__debezium_unavailable_value";
    @UriParam(label = LABEL_NAME)
    private String heartbeatActionQuery;
    @UriParam(label = LABEL_NAME)
    private String replicaIdentityAutosetValues;
    @UriParam(label = LABEL_NAME)
    private String databaseSslcert;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME)
    private String databaseInitialStatements;
    @UriParam(label = LABEL_NAME, defaultValue = "numeric")
    private String intervalHandlingMode = "numeric";
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME)
    private String databaseSslfactory;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private int statusUpdateIntervalMs = 10000;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String databaseDbname;
    @UriParam(label = LABEL_NAME)
    private String datatypePropagateSourceType;
    @UriParam(label = LABEL_NAME)
    private String databaseSslkey;
    @UriParam(label = LABEL_NAME, defaultValue = "disabled")
    private String snapshotTablesOrderByRowCount = "disabled";
    @UriParam(label = LABEL_NAME, defaultValue = "INSERT_INSERT")
    private String incrementalSnapshotWatermarkingStrategy = "INSERT_INSERT";
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    private String columnIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "decoderbufs")
    private String pluginName = "decoderbufs";
    @UriParam(label = LABEL_NAME)
    private String databaseSslpassword;
    @UriParam(label = LABEL_NAME)
    private String columnPropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "-1")
    private int errorsMaxRetries = -1;
    @UriParam(label = LABEL_NAME)
    private String tableExcludeList;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME)
    private String databaseSslrootcert;
    @UriParam(label = LABEL_NAME, defaultValue = "t")
    private String skippedOperations = "t";
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.schema.SchemaTopicNamingStrategy")
    private String topicNamingStrategy = "io.debezium.schema.SchemaTopicNamingStrategy";
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME)
    private String messagePrefixIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME)
    private String snapshotCustomClass;
    @UriParam(label = LABEL_NAME, defaultValue = "debezium")
    private String slotName = "debezium";
    @UriParam(label = LABEL_NAME, defaultValue = "1024")
    private int incrementalSnapshotChunkSize = 1024;
    @UriParam(label = LABEL_NAME, defaultValue = "json")
    private String hstoreHandlingMode = "json";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME)
    private String schemaHistoryInternalFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String topicPrefix;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long slotRetryDelayMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "bytes")
    private String binaryHandlingMode = "bytes";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeSchemaComments = false;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.connector.postgresql.PostgresSourceInfoStructMaker")
    private String sourceinfoStructMaker = "io.debezium.connector.postgresql.PostgresSourceInfoStructMaker";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean flushLsnSource = true;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean databaseTcpkeepalive = true;
    @UriParam(label = LABEL_NAME)
    private String schemaExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "all_tables")
    private String publicationAutocreateMode = "all_tables";
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean slotDropOnStop = false;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long xminFetchIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive")
    private String timePrecisionMode = "adaptive";
    @UriParam(label = LABEL_NAME)
    private String messagePrefixExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private long signalPollIntervalMs = 5000;
    @UriParam(label = LABEL_NAME)
    private String postProcessors;
    @UriParam(label = LABEL_NAME)
    private String notificationEnabledChannels;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int snapshotMaxThreads = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "5432")
    private int databasePort = 5432;
    @UriParam(label = LABEL_NAME)
    private String notificationSinkTopicName;
    @UriParam(label = LABEL_NAME)
    private String columnExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeUnknownDatatypes = false;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String schemaNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME)
    private String tableIncludeList;
    @UriParam(label = LABEL_NAME)
    private String slotStreamParams;

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
     * The name of the Postgres 10+ publication used for streaming changes from
     * a plugin. Defaults to 'dbz_publication'
     */
    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public String getPublicationName() {
        return publicationName;
    }

    /**
     * The schemas for which events should be captured
     */
    public void setSchemaIncludeList(String schemaIncludeList) {
        this.schemaIncludeList = schemaIncludeList;
    }

    public String getSchemaIncludeList() {
        return schemaIncludeList;
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
     * How many times to retry connecting to a replication slot when an attempt
     * fails.
     */
    public void setSlotMaxRetries(int slotMaxRetries) {
        this.slotMaxRetries = slotMaxRetries;
    }

    public int getSlotMaxRetries() {
        return slotMaxRetries;
    }

    /**
     * Specify the conditions that trigger a refresh of the in-memory schema for
     * a table. 'columns_diff' (the default) is the safest mode, ensuring the
     * in-memory schema stays in-sync with the database table's schema at all
     * times. 'columns_diff_exclude_unchanged_toast' instructs the connector to
     * refresh the in-memory schema cache if there is a discrepancy between it
     * and the schema derived from the incoming message, unless unchanged
     * TOASTable data fully accounts for the discrepancy. This setting can
     * improve connector performance significantly if there are
     * frequently-updated tables that have TOASTed data that are rarely part of
     * these updates. However, it is possible for the in-memory schema to become
     * outdated if TOASTable columns are dropped from the table.
     */
    public void setSchemaRefreshMode(String schemaRefreshMode) {
        this.schemaRefreshMode = schemaRefreshMode;
    }

    public String getSchemaRefreshMode() {
        return schemaRefreshMode;
    }

    /**
     * Whether to use an encrypted connection to Postgres. Options include:
     * 'disable' (the default) to use an unencrypted connection; 'allow' to try
     * and use an unencrypted connection first and, failing that, a secure
     * (encrypted) connection; 'prefer' (the default) to try and use a secure
     * (encrypted) connection first and, failing that, an unencrypted
     * connection; 'require' to use a secure (encrypted) connection, and fail if
     * one cannot be established; 'verify-ca' like 'required' but additionally
     * verify the server TLS certificate against the configured Certificate
     * Authority (CA) certificates, or fail if no valid matching CA certificates
     * are found; or 'verify-full' like 'verify-ca' but additionally verify that
     * the server certificate matches the host to which the connection is
     * attempted.
     */
    public void setDatabaseSslmode(String databaseSslmode) {
        this.databaseSslmode = databaseSslmode;
    }

    public String getDatabaseSslmode() {
        return databaseSslmode;
    }

    /**
     * Specify the constant that will be provided by Debezium to indicate that
     * the original value is a toasted value not provided by the database. If
     * starts with 'hex:' prefix it is expected that the rest of the string
     * represents hexadecimal encoded octets.
     */
    public void setUnavailableValuePlaceholder(
            String unavailableValuePlaceholder) {
        this.unavailableValuePlaceholder = unavailableValuePlaceholder;
    }

    public String getUnavailableValuePlaceholder() {
        return unavailableValuePlaceholder;
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
     * Applies only when streaming changes using pgoutput.Determines the value
     * for Replica Identity at table level. This option will overwrite the
     * existing value in databaseA comma-separated list of regular expressions
     * that match fully-qualified tables and Replica Identity value to be used
     * in the table. Each expression must match the pattern '<fully-qualified
     * table name>:<replica identity>', where the table names could be defined
     * as (SCHEMA_NAME.TABLE_NAME), and the replica identity values are: DEFAULT
     * - Records the old values of the columns of the primary key, if any. This
     * is the default for non-system tables.INDEX index_name - Records the old
     * values of the columns covered by the named index, that must be unique,
     * not partial, not deferrable, and include only columns marked NOT NULL. If
     * this index is dropped, the behavior is the same as NOTHING.FULL - Records
     * the old values of all columns in the row.NOTHING - Records no information
     * about the old row. This is the default for system tables.
     */
    public void setReplicaIdentityAutosetValues(
            String replicaIdentityAutosetValues) {
        this.replicaIdentityAutosetValues = replicaIdentityAutosetValues;
    }

    public String getReplicaIdentityAutosetValues() {
        return replicaIdentityAutosetValues;
    }

    /**
     * File containing the SSL Certificate for the client. See the Postgres SSL
     * docs for further information
     */
    public void setDatabaseSslcert(String databaseSslcert) {
        this.databaseSslcert = databaseSslcert;
    }

    public String getDatabaseSslcert() {
        return databaseSslcert;
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
     * connection to the database is established. Note that the connector may
     * establish JDBC connections at its own discretion, so this should
     * typically be used for configuration of session parameters only, but not
     * for executing DML statements. Use doubled semicolon (';;') to use a
     * semicolon as a character and not as a delimiter.
     */
    public void setDatabaseInitialStatements(String databaseInitialStatements) {
        this.databaseInitialStatements = databaseInitialStatements;
    }

    public String getDatabaseInitialStatements() {
        return databaseInitialStatements;
    }

    /**
     * Specify how INTERVAL columns should be represented in change events,
     * including: 'string' represents values as an exact ISO formatted string;
     * 'numeric' (default) represents values using the inexact conversion into
     * microseconds
     */
    public void setIntervalHandlingMode(String intervalHandlingMode) {
        this.intervalHandlingMode = intervalHandlingMode;
    }

    public String getIntervalHandlingMode() {
        return intervalHandlingMode;
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
     * A name of class to that creates SSL Sockets. Use
     * org.postgresql.ssl.NonValidatingFactory to disable SSL validation in
     * development environments
     */
    public void setDatabaseSslfactory(String databaseSslfactory) {
        this.databaseSslfactory = databaseSslfactory;
    }

    public String getDatabaseSslfactory() {
        return databaseSslfactory;
    }

    /**
     * Frequency for sending replication connection status updates to the
     * server, given in milliseconds. Defaults to 10 seconds (10,000 ms).
     */
    public void setStatusUpdateIntervalMs(int statusUpdateIntervalMs) {
        this.statusUpdateIntervalMs = statusUpdateIntervalMs;
    }

    public int getStatusUpdateIntervalMs() {
        return statusUpdateIntervalMs;
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
     * File containing the SSL private key for the client. See the Postgres SSL
     * docs for further information
     */
    public void setDatabaseSslkey(String databaseSslkey) {
        this.databaseSslkey = databaseSslkey;
    }

    public String getDatabaseSslkey() {
        return databaseSslkey;
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
     * Regular expressions matching columns to include in change events
     */
    public void setColumnIncludeList(String columnIncludeList) {
        this.columnIncludeList = columnIncludeList;
    }

    public String getColumnIncludeList() {
        return columnIncludeList;
    }

    /**
     * The name of the Postgres logical decoding plugin installed on the server.
     * Supported values are 'decoderbufs' and 'pgoutput'. Defaults to
     * 'decoderbufs'.
     */
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getPluginName() {
        return pluginName;
    }

    /**
     * Password to access the client private key from the file specified by
     * 'database.sslkey'. See the Postgres SSL docs for further information
     */
    public void setDatabaseSslpassword(String databaseSslpassword) {
        this.databaseSslpassword = databaseSslpassword;
    }

    public String getDatabaseSslpassword() {
        return databaseSslpassword;
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
     * File containing the root certificate(s) against which the server is
     * validated. See the Postgres JDBC SSL docs for further information
     */
    public void setDatabaseSslrootcert(String databaseSslrootcert) {
        this.databaseSslrootcert = databaseSslrootcert;
    }

    public String getDatabaseSslrootcert() {
        return databaseSslrootcert;
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
     * Maximum size of each batch of source records. Defaults to 2048.
     */
    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
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
     * one of the following snapshot options: 'always': The connector runs a
     * snapshot every time that it starts. After the snapshot completes, the
     * connector begins to stream changes from the transaction log.; 'initial'
     * (default): If the connector does not detect any offsets for the logical
     * server name, it runs a snapshot that captures the current full state of
     * the configured tables. After the snapshot completes, the connector begins
     * to stream changes from the transaction log. 'initial_only': The connector
     * performs a snapshot as it does for the 'initial' option, but after the
     * connector completes the snapshot, it stops, and does not stream changes
     * from the transaction log.; 'never': The connector does not run a
     * snapshot. Upon first startup, the connector immediately begins reading
     * from the beginning of the transaction log. 'exported': This option is
     * deprecated; use 'initial' instead.; 'custom': The connector loads a
     * custom class  to specify how the connector performs snapshots. For more
     * information, see Custom snapshotter SPI in the PostgreSQL connector
     * documentation.
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * A comma-separated list of regular expressions that match the logical
     * decoding message prefixes to be monitored. All prefixes are monitored by
     * default.
     */
    public void setMessagePrefixIncludeList(String messagePrefixIncludeList) {
        this.messagePrefixIncludeList = messagePrefixIncludeList;
    }

    public String getMessagePrefixIncludeList() {
        return messagePrefixIncludeList;
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
     * When 'snapshot.mode' is set as custom, this setting must be set to
     * specify a fully qualified class name to load (via the default class
     * loader). This class must implement the 'Snapshotter' interface and is
     * called on each app boot to determine whether to do a snapshot and how to
     * build queries.
     */
    public void setSnapshotCustomClass(String snapshotCustomClass) {
        this.snapshotCustomClass = snapshotCustomClass;
    }

    public String getSnapshotCustomClass() {
        return snapshotCustomClass;
    }

    /**
     * The name of the Postgres logical decoding slot created for streaming
     * changes from a plugin. Defaults to 'debezium
     */
    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public String getSlotName() {
        return slotName;
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
     * Specify how HSTORE columns should be represented in change events,
     * including: 'json' represents values as string-ified JSON (default); 'map'
     * represents values as a key/value map
     */
    public void setHstoreHandlingMode(String hstoreHandlingMode) {
        this.hstoreHandlingMode = hstoreHandlingMode;
    }

    public String getHstoreHandlingMode() {
        return hstoreHandlingMode;
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
     * Time to wait between retry attempts when the connector fails to connect
     * to a replication slot, given in milliseconds. Defaults to 10 seconds
     * (10,000 ms).
     */
    public void setSlotRetryDelayMs(long slotRetryDelayMs) {
        this.slotRetryDelayMs = slotRetryDelayMs;
    }

    public long getSlotRetryDelayMs() {
        return slotRetryDelayMs;
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
     * Boolean to determine if Debezium should flush LSN in the source postgres
     * database. If set to false, user will have to flush the LSN manually
     * outside Debezium.
     */
    public void setFlushLsnSource(boolean flushLsnSource) {
        this.flushLsnSource = flushLsnSource;
    }

    public boolean isFlushLsnSource() {
        return flushLsnSource;
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
     * Enable or disable TCP keep-alive probe to avoid dropping TCP connection
     */
    public void setDatabaseTcpkeepalive(boolean databaseTcpkeepalive) {
        this.databaseTcpkeepalive = databaseTcpkeepalive;
    }

    public boolean isDatabaseTcpkeepalive() {
        return databaseTcpkeepalive;
    }

    /**
     * The schemas for which events must not be captured
     */
    public void setSchemaExcludeList(String schemaExcludeList) {
        this.schemaExcludeList = schemaExcludeList;
    }

    public String getSchemaExcludeList() {
        return schemaExcludeList;
    }

    /**
     * Applies only when streaming changes using pgoutput.Determine how creation
     * of a publication should work, the default is all_tables.DISABLED - The
     * connector will not attempt to create a publication at all. The
     * expectation is that the user has created the publication up-front. If the
     * publication isn't found to exist upon startup, the connector will throw
     * an exception and stop.ALL_TABLES - If no publication exists, the
     * connector will create a new publication for all tables. Note this
     * requires that the configured user has access. If the publication already
     * exists, it will be used. i.e CREATE PUBLICATION <publication_name> FOR
     * ALL TABLES;FILTERED - If no publication exists, the connector will create
     * a new publication for all those tables matchingthe current filter
     * configuration (see table/database include/exclude list properties). If
     * the publication already exists, it will be used. i.e CREATE PUBLICATION
     * <publication_name> FOR TABLE <tbl1, tbl2, etc>
     */
    public void setPublicationAutocreateMode(String publicationAutocreateMode) {
        this.publicationAutocreateMode = publicationAutocreateMode;
    }

    public String getPublicationAutocreateMode() {
        return publicationAutocreateMode;
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
     * Whether or not to drop the logical replication slot when the connector
     * finishes orderly. By default the replication is kept so that on restart
     * progress can resume from the last recorded location
     */
    public void setSlotDropOnStop(boolean slotDropOnStop) {
        this.slotDropOnStop = slotDropOnStop;
    }

    public boolean isSlotDropOnStop() {
        return slotDropOnStop;
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
     * Specify how often (in ms) the xmin will be fetched from the replication
     * slot. This xmin value is exposed by the slot which gives a lower bound of
     * where a new replication slot could start from. The lower the value, the
     * more likely this value is to be the current 'true' value, but the bigger
     * the performance cost. The bigger the value, the less likely this value is
     * to be the current 'true' value, but the lower the performance penalty.
     * The default is set to 0 ms, which disables tracking xmin.
     */
    public void setXminFetchIntervalMs(long xminFetchIntervalMs) {
        this.xminFetchIntervalMs = xminFetchIntervalMs;
    }

    public long getXminFetchIntervalMs() {
        return xminFetchIntervalMs;
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
     * A comma-separated list of regular expressions that match the logical
     * decoding message prefixes to be excluded from monitoring.
     */
    public void setMessagePrefixExcludeList(String messagePrefixExcludeList) {
        this.messagePrefixExcludeList = messagePrefixExcludeList;
    }

    public String getMessagePrefixExcludeList() {
        return messagePrefixExcludeList;
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
     * Regular expressions matching columns to exclude from change events
     */
    public void setColumnExcludeList(String columnExcludeList) {
        this.columnExcludeList = columnExcludeList;
    }

    public String getColumnExcludeList() {
        return columnExcludeList;
    }

    /**
     * Specify whether the fields of data type not supported by Debezium should
     * be processed: 'false' (the default) omits the fields; 'true' converts the
     * field into an implementation dependent binary representation.
     */
    public void setIncludeUnknownDatatypes(boolean includeUnknownDatatypes) {
        this.includeUnknownDatatypes = includeUnknownDatatypes;
    }

    public boolean isIncludeUnknownDatatypes() {
        return includeUnknownDatatypes;
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
     * Any optional parameters used by logical decoding plugin. Semi-colon
     * separated. E.g. 'add-tables=public.table,public.table2;include-lsn=true'
     */
    public void setSlotStreamParams(String slotStreamParams) {
        this.slotStreamParams = slotStreamParams;
    }

    public String getSlotStreamParams() {
        return slotStreamParams;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "custom.metric.tags", customMetricTags);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "publication.name", publicationName);
        addPropertyIfNotNull(configBuilder, "schema.include.list", schemaIncludeList);
        addPropertyIfNotNull(configBuilder, "signal.enabled.channels", signalEnabledChannels);
        addPropertyIfNotNull(configBuilder, "slot.max.retries", slotMaxRetries);
        addPropertyIfNotNull(configBuilder, "schema.refresh.mode", schemaRefreshMode);
        addPropertyIfNotNull(configBuilder, "database.sslmode", databaseSslmode);
        addPropertyIfNotNull(configBuilder, "unavailable.value.placeholder", unavailableValuePlaceholder);
        addPropertyIfNotNull(configBuilder, "heartbeat.action.query", heartbeatActionQuery);
        addPropertyIfNotNull(configBuilder, "replica.identity.autoset.values", replicaIdentityAutosetValues);
        addPropertyIfNotNull(configBuilder, "database.sslcert", databaseSslcert);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "database.initial.statements", databaseInitialStatements);
        addPropertyIfNotNull(configBuilder, "interval.handling.mode", intervalHandlingMode);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "database.sslfactory", databaseSslfactory);
        addPropertyIfNotNull(configBuilder, "status.update.interval.ms", statusUpdateIntervalMs);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "database.dbname", databaseDbname);
        addPropertyIfNotNull(configBuilder, "datatype.propagate.source.type", datatypePropagateSourceType);
        addPropertyIfNotNull(configBuilder, "database.sslkey", databaseSslkey);
        addPropertyIfNotNull(configBuilder, "snapshot.tables.order.by.row.count", snapshotTablesOrderByRowCount);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.watermarking.strategy", incrementalSnapshotWatermarkingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "column.include.list", columnIncludeList);
        addPropertyIfNotNull(configBuilder, "plugin.name", pluginName);
        addPropertyIfNotNull(configBuilder, "database.sslpassword", databaseSslpassword);
        addPropertyIfNotNull(configBuilder, "column.propagate.source.type", columnPropagateSourceType);
        addPropertyIfNotNull(configBuilder, "errors.max.retries", errorsMaxRetries);
        addPropertyIfNotNull(configBuilder, "table.exclude.list", tableExcludeList);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "database.sslrootcert", databaseSslrootcert);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "topic.naming.strategy", topicNamingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "message.prefix.include.list", messagePrefixIncludeList);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "snapshot.custom.class", snapshotCustomClass);
        addPropertyIfNotNull(configBuilder, "slot.name", slotName);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.chunk.size", incrementalSnapshotChunkSize);
        addPropertyIfNotNull(configBuilder, "hstore.handling.mode", hstoreHandlingMode);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.file.filename", schemaHistoryInternalFileFilename);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "topic.prefix", topicPrefix);
        addPropertyIfNotNull(configBuilder, "slot.retry.delay.ms", slotRetryDelayMs);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "binary.handling.mode", binaryHandlingMode);
        addPropertyIfNotNull(configBuilder, "include.schema.comments", includeSchemaComments);
        addPropertyIfNotNull(configBuilder, "sourceinfo.struct.maker", sourceinfoStructMaker);
        addPropertyIfNotNull(configBuilder, "flush.lsn.source", flushLsnSource);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "database.tcpKeepAlive", databaseTcpkeepalive);
        addPropertyIfNotNull(configBuilder, "schema.exclude.list", schemaExcludeList);
        addPropertyIfNotNull(configBuilder, "publication.autocreate.mode", publicationAutocreateMode);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "slot.drop.on.stop", slotDropOnStop);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "xmin.fetch.interval.ms", xminFetchIntervalMs);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "message.prefix.exclude.list", messagePrefixExcludeList);
        addPropertyIfNotNull(configBuilder, "signal.poll.interval.ms", signalPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "post.processors", postProcessors);
        addPropertyIfNotNull(configBuilder, "notification.enabled.channels", notificationEnabledChannels);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.max.threads", snapshotMaxThreads);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "notification.sink.topic.name", notificationSinkTopicName);
        addPropertyIfNotNull(configBuilder, "column.exclude.list", columnExcludeList);
        addPropertyIfNotNull(configBuilder, "include.unknown.datatypes", includeUnknownDatatypes);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "table.include.list", tableIncludeList);
        addPropertyIfNotNull(configBuilder, "slot.stream.params", slotStreamParams);
        
        return configBuilder.build();
    }

    @Override
    protected Class configureConnectorClass() {
        return PostgresConnector.class;
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
        return "postgres";
    }
}