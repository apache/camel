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
    @UriParam(label = LABEL_NAME, defaultValue = "dbz_publication")
    private String publicationName = "dbz_publication";
    @UriParam(label = LABEL_NAME)
    private String columnBlacklist;
    @UriParam(label = LABEL_NAME)
    private String schemaBlacklist;
    @UriParam(label = LABEL_NAME)
    private String tableBlacklist;
    @UriParam(label = LABEL_NAME, defaultValue = "6")
    private int slotMaxRetries = 6;
    @UriParam(label = LABEL_NAME, defaultValue = "columns_diff")
    private String schemaRefreshMode = "columns_diff";
    @UriParam(label = LABEL_NAME, defaultValue = "disable")
    private String databaseSslmode = "disable";
    @UriParam(label = LABEL_NAME)
    private String heartbeatActionQuery;
    @UriParam(label = LABEL_NAME)
    private String databaseSslcert;
    @UriParam(label = LABEL_NAME, defaultValue = "500")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String databaseInitialStatements;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME, defaultValue = "numeric")
    private String intervalHandlingMode = "numeric";
    @UriParam(label = LABEL_NAME)
    private String databaseSslfactory;
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private int statusUpdateIntervalMs = 10000;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String databaseDbname;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String databaseSslkey;
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "v2")
    private String sourceStructVersion = "v2";
    @UriParam(label = LABEL_NAME, defaultValue = "decoderbufs")
    private String pluginName = "decoderbufs";
    @UriParam(label = LABEL_NAME)
    private String databaseSslpassword;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium_unavailable_value")
    private String toastedValuePlaceholder = "__debezium_unavailable_value";
    @UriParam(label = LABEL_NAME)
    private String schemaWhitelist;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME)
    private String databaseSslrootcert;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME)
    private String snapshotCustomClass;
    @UriParam(label = LABEL_NAME, defaultValue = "debezium")
    private String slotName = "debezium";
    @UriParam(label = LABEL_NAME, defaultValue = "json")
    private String hstoreHandlingMode = "json";
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME)
    private String tableWhitelist;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private long slotRetryDelayMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean databaseTcpkeepalive = true;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean slotDropOnStop = false;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long xminFetchIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive")
    private String timePrecisionMode = "adaptive";
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String databaseServerName;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "5432")
    private int databasePort = 5432;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean includeUnknownDatatypes = false;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME)
    private String slotStreamParams;

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
     * The name of the Postgres 10+ publication used for streaming changes from
     * a plugin.Defaults to 'dbz_publication'
     */
    public void setPublicationName(String publicationName) {
        this.publicationName = publicationName;
    }

    public String getPublicationName() {
        return publicationName;
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
     * The schemas for which events must not be captured
     */
    public void setSchemaBlacklist(String schemaBlacklist) {
        this.schemaBlacklist = schemaBlacklist;
    }

    public String getSchemaBlacklist() {
        return schemaBlacklist;
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
     * Whether to use an encrypted connection to Postgres. Options
     * include'disable' (the default) to use an unencrypted connection;
     * 'require' to use a secure (encrypted) connection, and fail if one cannot
     * be established; 'verify-ca' like 'required' but additionally verify the
     * server TLS certificate against the configured Certificate Authority (CA)
     * certificates, or fail if no valid matching CA certificates are found;
     * or'verify-full' like 'verify-ca' but additionally verify that the server
     * certificate matches the host to which the connection is attempted.
     */
    public void setDatabaseSslmode(String databaseSslmode) {
        this.databaseSslmode = databaseSslmode;
    }

    public String getDatabaseSslmode() {
        return databaseSslmode;
    }

    /**
     * The query executed with every heartbeat. Defaults to an empty string.
     */
    public void setHeartbeatActionQuery(String heartbeatActionQuery) {
        this.heartbeatActionQuery = heartbeatActionQuery;
    }

    public String getHeartbeatActionQuery() {
        return heartbeatActionQuery;
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
     * connection to the database is established. Note that the connector may
     * establish JDBC connections at its own discretion, so this should
     * typically be used for configurationof session parameters only, but not
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
     * Specify how INTERVAL columns should be represented in change events,
     * including:'string' represents values as an exact ISO formatted
     * string'numeric' (default) represents values using the inexact conversion
     * into microseconds
     */
    public void setIntervalHandlingMode(String intervalHandlingMode) {
        this.intervalHandlingMode = intervalHandlingMode;
    }

    public String getIntervalHandlingMode() {
        return intervalHandlingMode;
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
     * Frequency in milliseconds for sending replication connection status
     * updates to the server. Defaults to 10 seconds (10000 ms).
     */
    public void setStatusUpdateIntervalMs(int statusUpdateIntervalMs) {
        this.statusUpdateIntervalMs = statusUpdateIntervalMs;
    }

    public int getStatusUpdateIntervalMs() {
        return statusUpdateIntervalMs;
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
     * The name of the database the connector should be monitoring
     */
    public void setDatabaseDbname(String databaseDbname) {
        this.databaseDbname = databaseDbname;
    }

    public String getDatabaseDbname() {
        return databaseDbname;
    }

    /**
     * Name of the Postgres database user to be used when connecting to the
     * database.
     */
    public void setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
    }

    public String getDatabaseUser() {
        return databaseUser;
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
     * The name of the Postgres logical decoding plugin installed on the server.
     * Supported values are 'decoderbufs' and 'wal2json'. Defaults to
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
     * Specify the constant that will be provided by Debezium to indicate that
     * the original value is a toasted value not provided by the database.If
     * starts with 'hex:' prefix it is expected that the rest of the string
     * repesents hexadecimally encoded octets.
     */
    public void setToastedValuePlaceholder(String toastedValuePlaceholder) {
        this.toastedValuePlaceholder = toastedValuePlaceholder;
    }

    public String getToastedValuePlaceholder() {
        return toastedValuePlaceholder;
    }

    /**
     * The schemas for which events should be captured
     */
    public void setSchemaWhitelist(String schemaWhitelist) {
        this.schemaWhitelist = schemaWhitelist;
    }

    public String getSchemaWhitelist() {
        return schemaWhitelist;
    }

    /**
     * Password of the Postgres database user to be used when connecting to the
     * database.
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
     * Maximum size of each batch of source records. Defaults to 2048.
     */
    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * The criteria for running a snapshot upon startup of the connector.
     * Options include: 'always' to specify that the connector run a snapshot
     * each time it starts up; 'initial' (the default) to specify the connector
     * can run a snapshot only when no offsets are available for the logical
     * server name; 'initial_only' same as 'initial' except the connector should
     * stop after completing the snapshot and before it would normally start
     * emitting changes;'never' to specify the connector should never run a
     * snapshot and that upon first startup the connector should read from the
     * last position (LSN) recorded by the server; and'exported' to specify the
     * connector should run a snapshot based on the position when the
     * replication slot was created; 'custom' to specify a custom class with
     * 'snapshot.custom_class' which will be loaded and used to determine the
     * snapshot, see docs for more details.
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
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
     * loader).This class must implement the 'Snapshotter' interface and is
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
     * changes from a plugin.Defaults to 'debezium
     */
    public void setSlotName(String slotName) {
        this.slotName = slotName;
    }

    public String getSlotName() {
        return slotName;
    }

    /**
     * Specify how HSTORE columns should be represented in change events,
     * including:'json' represents values as string-ified JSON (default)'map'
     * represents values as a key/value map
     */
    public void setHstoreHandlingMode(String hstoreHandlingMode) {
        this.hstoreHandlingMode = hstoreHandlingMode;
    }

    public String getHstoreHandlingMode() {
        return hstoreHandlingMode;
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
     * Enables transaction metadata extraction together with event counting
     */
    public void setProvideTransactionMetadata(boolean provideTransactionMetadata) {
        this.provideTransactionMetadata = provideTransactionMetadata;
    }

    public boolean isProvideTransactionMetadata() {
        return provideTransactionMetadata;
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
     * The number of milli-seconds to wait between retry attempts when the
     * connector fails to connect to a replication slot.
     */
    public void setSlotRetryDelayMs(long slotRetryDelayMs) {
        this.slotRetryDelayMs = slotRetryDelayMs;
    }

    public long getSlotRetryDelayMs() {
        return slotRetryDelayMs;
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
     * Enable or disable TCP keep-alive probe to avoid dropping TCP connection
     */
    public void setDatabaseTcpkeepalive(boolean databaseTcpkeepalive) {
        this.databaseTcpkeepalive = databaseTcpkeepalive;
    }

    public boolean isDatabaseTcpkeepalive() {
        return databaseTcpkeepalive;
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
     * Whether or not to drop the logical replication slot when the connector
     * finishes orderlyBy default the replication is kept so that on restart
     * progress can resume from the last recorded location
     */
    public void setSlotDropOnStop(boolean slotDropOnStop) {
        this.slotDropOnStop = slotDropOnStop;
    }

    public boolean isSlotDropOnStop() {
        return slotDropOnStop;
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
     * Port of the Postgres database server.
     */
    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    /**
     * Specify whether the fields of data type not supported by Debezium should
     * be processed:'false' (the default) omits the fields; 'true' converts the
     * field into an implementation dependent binary representation.
     */
    public void setIncludeUnknownDatatypes(boolean includeUnknownDatatypes) {
        this.includeUnknownDatatypes = includeUnknownDatatypes;
    }

    public boolean isIncludeUnknownDatatypes() {
        return includeUnknownDatatypes;
    }

    /**
     * Resolvable hostname or IP address of the Postgres database server.
     */
    public void setDatabaseHostname(String databaseHostname) {
        this.databaseHostname = databaseHostname;
    }

    public String getDatabaseHostname() {
        return databaseHostname;
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
        addPropertyIfNotNull(configBuilder, "publication.name", publicationName);
        addPropertyIfNotNull(configBuilder, "column.blacklist", columnBlacklist);
        addPropertyIfNotNull(configBuilder, "schema.blacklist", schemaBlacklist);
        addPropertyIfNotNull(configBuilder, "table.blacklist", tableBlacklist);
        addPropertyIfNotNull(configBuilder, "slot.max.retries", slotMaxRetries);
        addPropertyIfNotNull(configBuilder, "schema.refresh.mode", schemaRefreshMode);
        addPropertyIfNotNull(configBuilder, "database.sslmode", databaseSslmode);
        addPropertyIfNotNull(configBuilder, "heartbeat.action.query", heartbeatActionQuery);
        addPropertyIfNotNull(configBuilder, "database.sslcert", databaseSslcert);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "database.initial.statements", databaseInitialStatements);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "interval.handling.mode", intervalHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.sslfactory", databaseSslfactory);
        addPropertyIfNotNull(configBuilder, "status.update.interval.ms", statusUpdateIntervalMs);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "database.dbname", databaseDbname);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "database.sslkey", databaseSslkey);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "source.struct.version", sourceStructVersion);
        addPropertyIfNotNull(configBuilder, "plugin.name", pluginName);
        addPropertyIfNotNull(configBuilder, "database.sslpassword", databaseSslpassword);
        addPropertyIfNotNull(configBuilder, "toasted.value.placeholder", toastedValuePlaceholder);
        addPropertyIfNotNull(configBuilder, "schema.whitelist", schemaWhitelist);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "database.sslrootcert", databaseSslrootcert);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "snapshot.custom.class", snapshotCustomClass);
        addPropertyIfNotNull(configBuilder, "slot.name", slotName);
        addPropertyIfNotNull(configBuilder, "hstore.handling.mode", hstoreHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "table.whitelist", tableWhitelist);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "slot.retry.delay.ms", slotRetryDelayMs);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.tcpKeepAlive", databaseTcpkeepalive);
        addPropertyIfNotNull(configBuilder, "database.history.file.filename", databaseHistoryFileFilename);
        addPropertyIfNotNull(configBuilder, "slot.drop.on.stop", slotDropOnStop);
        addPropertyIfNotNull(configBuilder, "xmin.fetch.interval.ms", xminFetchIntervalMs);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "database.server.name", databaseServerName);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "include.unknown.datatypes", includeUnknownDatatypes);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
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
        if (isFieldValueNotSet(databaseServerName)) {
        	return ConfigurationValidation.notValid("Required field 'databaseServerName' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "postgres";
    }
}