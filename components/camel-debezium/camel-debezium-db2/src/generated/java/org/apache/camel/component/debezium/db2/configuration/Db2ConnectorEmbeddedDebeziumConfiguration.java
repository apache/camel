package org.apache.camel.component.debezium.db2.configuration;

import javax.annotation.processing.Generated;
import io.debezium.config.Configuration;
import io.debezium.connector.db2.Db2Connector;
import org.apache.camel.component.debezium.configuration.ConfigurationValidation;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Generated("org.apache.camel.maven.GenerateConnectorConfigMojo")
@UriParams
public class Db2ConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,db2";
    @UriParam(label = LABEL_NAME, defaultValue = "exclusive")
    private String snapshotLockingMode = "exclusive";
    @UriParam(label = LABEL_NAME)
    private String messageKeyColumns;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.pipeline.txmetadata.DefaultTransactionMetadataFactory")
    private String transactionMetadataFactory = "io.debezium.pipeline.txmetadata.DefaultTransactionMetadataFactory";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean notificationHttpAllowPrivateNetworks = false;
    @UriParam(label = LABEL_NAME)
    private String customMetricTags;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean updateCaptureTablePruneLsnDecrement = true;
    @UriParam(label = LABEL_NAME)
    private String updateCaptureTablePruneSetName;
    @UriParam(label = LABEL_NAME)
    private String updateCaptureTablePruneTargetServer;
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private int notificationHttpTimeoutMs = 5000;
    @UriParam(label = LABEL_NAME, defaultValue = "source")
    private String signalEnabledChannels = "source";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long signalEmitFailureBackoffMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean includeSchemaChanges = true;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int snapshotMaxThreadsMultiplier = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long snapshotLockTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationJobTags;
    @UriParam(label = LABEL_NAME, defaultValue = "ASNCDC")
    private String cdcChangeTablesSchema = "ASNCDC";
    @UriParam(label = LABEL_NAME)
    private String databaseDbname;
    @UriParam(label = LABEL_NAME, defaultValue = "disabled")
    private String snapshotTablesOrderByRowCount = "disabled";
    @UriParam(label = LABEL_NAME)
    private String snapshotSelectStatementOverrides;
    @UriParam(label = LABEL_NAME)
    private String tableExcludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseExcludeList;
    @UriParam(label = LABEL_NAME)
    private String notificationHttpUrl;
    @UriParam(label = LABEL_NAME, defaultValue = "warn")
    private String eventConvertingFailureHandlingMode = "warn";
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.schema.SchemaTopicNamingStrategy")
    private String topicNamingStrategy = "io.debezium.schema.SchemaTopicNamingStrategy";
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotData = false;
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
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.relational.ConcurrentMapTableMappingStorage")
    private String memoryManagementTablesClass = "io.debezium.relational.ConcurrentMapTableMappingStorage";
    @UriParam(label = LABEL_NAME, defaultValue = "4s", javaType = "java.time.Duration")
    private long executorShutdownTimeoutMs = 4000;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotOnDataError = false;
    @UriParam(label = LABEL_NAME)
    private String schemaHistoryInternalFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "precise")
    private String decimalHandlingMode = "precise";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean updateCaptureTablePruneInd = false;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationDatasetKafkaBootstrapServers;
    @UriParam(label = LABEL_NAME, defaultValue = "ASNCDC")
    private String cdcControlSchema = "ASNCDC";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean tableIgnoreBuiltin = true;
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedStartStream = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean statisticsMetricsEnabled = true;
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private long signalPollIntervalMs = 5000;
    @UriParam(label = LABEL_NAME)
    private String notificationEnabledChannels;
    @UriParam(label = LABEL_NAME, defaultValue = "repeatable_read")
    private String snapshotIsolationMode = "repeatable_read";
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean zStopLsnIgnore = false;
    @UriParam(label = LABEL_NAME)
    private String notificationSinkTopicName;
    @UriParam(label = LABEL_NAME)
    private String snapshotModeCustomName;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String schemaNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.relational.ConcurrentMapTableMappingStorage")
    private String memoryManagementSchemasClass = "io.debezium.relational.ConcurrentMapTableMappingStorage";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean skipMessagesWithoutChange = false;
    @UriParam(label = LABEL_NAME)
    private String tableIncludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String fieldNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long streamingDelayMs = 0;
    @UriParam(label = LABEL_NAME)
    private String openlineageIntegrationJobNamespace;
    @UriParam(label = LABEL_NAME, defaultValue = "10m", javaType = "java.time.Duration")
    private int databaseQueryTimeoutMs = 600000;
    @UriParam(label = LABEL_NAME, defaultValue = "10000")
    private int queryFetchSize = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean legacySnapshotMaxThreads = false;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int streamingQueryTimespanSeconds = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private int updateCaptureTablePruneMinIntervalMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int guardrailCollectionsMax = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME)
    private String updateCaptureTablePruneApplyQual;
    @UriParam(label = LABEL_NAME)
    private String databaseUser;
    @UriParam(label = LABEL_NAME)
    private String datatypePropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "INSERT_INSERT")
    private String incrementalSnapshotWatermarkingStrategy = "INSERT_INSERT";
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotOnSchemaError = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalSkipUnparseableDdl = false;
    @UriParam(label = LABEL_NAME)
    private String columnIncludeList;
    @UriParam(label = LABEL_NAME)
    private String columnPropagateSourceType;
    @UriParam(label = LABEL_NAME, defaultValue = "-1")
    private int errorsMaxRetries = -1;
    @UriParam(label = LABEL_NAME, secret = true)
    @Metadata(required = true)
    private String databasePassword;
    @UriParam(label = LABEL_NAME, defaultValue = "t")
    private String skippedOperations = "t";
    @UriParam(label = LABEL_NAME, defaultValue = "Debezium change data capture job")
    private String openlineageIntegrationJobDescription = "Debezium change data capture job";
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean extendedHeadersEnabled = true;
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME, defaultValue = "warn")
    private String guardrailCollectionsLimitAction = "warn";
    @UriParam(label = LABEL_NAME, defaultValue = ".*secret$|.*password$|.*sasl\\.jaas\\.config$|.*basic\\.auth\\.user\\.info|.*registry\\.auth\\.client-secret|.*credentials\\.json$|.*connectionstring$|.*connection\\.string$")
    private String customSanitizePattern = ".*secret$|.*password$|.*sasl\\.jaas\\.config$|.*basic\\.auth\\.user\\.info|.*registry\\.auth\\.client-secret|.*credentials\\.json$|.*connectionstring$|.*connection\\.string$";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalStoreOnlyCapturedTablesDdl = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean schemaHistoryInternalStoreOnlyCapturedDatabasesDdl = false;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String topicPrefix;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.connector.db2.Db2SourceInfoStructMaker")
    private String sourceinfoStructMaker = "io.debezium.connector.db2.Db2SourceInfoStructMaker";
    @UriParam(label = LABEL_NAME, defaultValue = "off")
    private String schemaHistoryInternalMemoryOptimization = "off";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean openlineageIntegrationEnabled = false;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int signalEmitFailureMaxRetries = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "adaptive")
    private String timePrecisionMode = "adaptive";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean snapshotModeConfigurationBasedSnapshotSchema = false;
    @UriParam(label = LABEL_NAME)
    private String postProcessors;
    @UriParam(label = LABEL_NAME, defaultValue = "50000")
    private int databasePort = 50000;
    @UriParam(label = LABEL_NAME)
    private String updateCaptureTablePruneProcedureOverrideName;
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.storage.kafka.history.KafkaSchemaHistory")
    private String schemaHistoryInternal = "io.debezium.storage.kafka.history.KafkaSchemaHistory";
    @UriParam(label = LABEL_NAME)
    private String columnExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "2")
    private int notificationHttpRetries = 2;
    @UriParam(label = LABEL_NAME)
    private String databaseHostname;
    @UriParam(label = LABEL_NAME, defaultValue = "1m", javaType = "java.time.Duration")
    private long connectionValidationTimeoutMs = 60000;
    @UriParam(label = LABEL_NAME, defaultValue = "LUW")
    private String db2Platform = "LUW";

    /**
     * Controls how the connector holds locks on tables while performing the
     * schema snapshot when `snapshot.isolation.mode` is `REPEATABLE_READ` or
     * `EXCLUSIVE`. The 'exclusive' which means the connector will hold a table
     * lock for exclusive table access for just the initial portion of the
     * snapshot while the database schemas and other metadata are being read.
     * The remaining work in a snapshot involves selecting all rows from each
     * table, and this is done using a flashback query that requires no locks.
     * However, in some cases it may be desirable to avoid locks entirely which
     * can be done by specifying 'none'. This mode is only safe to use if no
     * schema changes are happening while the snapshot is taken.
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
     * Class to make transaction context & transaction struct/schemas
     */
    public void setTransactionMetadataFactory(String transactionMetadataFactory) {
        this.transactionMetadataFactory = transactionMetadataFactory;
    }

    public String getTransactionMetadataFactory() {
        return transactionMetadataFactory;
    }

    /**
     * When true, the SSRF guard permits notification URLs that resolve to
     * loopback, site-local or link-local addresses (e.g. an in-cluster
     * Service). Defaults to false so only public addresses are allowed.
     */
    public void setNotificationHttpAllowPrivateNetworks(
            boolean notificationHttpAllowPrivateNetworks) {
        this.notificationHttpAllowPrivateNetworks = notificationHttpAllowPrivateNetworks;
    }

    public boolean isNotificationHttpAllowPrivateNetworks() {
        return notificationHttpAllowPrivateNetworks;
    }

    /**
     * A comma-separated list of key-value pairs that are appended to the MBean
     * object name. Keys must be valid JMX ObjectName property keys. For
     * example: k1=v1,k2=v2
     */
    public void setCustomMetricTags(String customMetricTags) {
        this.customMetricTags = customMetricTags;
    }

    public String getCustomMetricTags() {
        return customMetricTags;
    }

    /**
     * An switch to decrement the prune lsn/syncpoint by one to avoid allowing
     * the last lsn processed to get pruned to avoid the risk of an unnecessary
     * snapshot due to the last processed lsn not being present in the change
     * table.
     */
    public void setUpdateCaptureTablePruneLsnDecrement(
            boolean updateCaptureTablePruneLsnDecrement) {
        this.updateCaptureTablePruneLsnDecrement = updateCaptureTablePruneLsnDecrement;
    }

    public boolean isUpdateCaptureTablePruneLsnDecrement() {
        return updateCaptureTablePruneLsnDecrement;
    }

    /**
     * If set to update the prune table, this will identify the prune set name
     * that should be used when updating the table for this instance.
     */
    public void setUpdateCaptureTablePruneSetName(
            String updateCaptureTablePruneSetName) {
        this.updateCaptureTablePruneSetName = updateCaptureTablePruneSetName;
    }

    public String getUpdateCaptureTablePruneSetName() {
        return updateCaptureTablePruneSetName;
    }

    /**
     * The target_server name that represents the apply agent's target (where
     * the data is going) for the subscription set.
     */
    public void setUpdateCaptureTablePruneTargetServer(
            String updateCaptureTablePruneTargetServer) {
        this.updateCaptureTablePruneTargetServer = updateCaptureTablePruneTargetServer;
    }

    public String getUpdateCaptureTablePruneTargetServer() {
        return updateCaptureTablePruneTargetServer;
    }

    /**
     * Connect and read timeout, in milliseconds, for HTTP notification
     * delivery.
     */
    public void setNotificationHttpTimeoutMs(int notificationHttpTimeoutMs) {
        this.notificationHttpTimeoutMs = notificationHttpTimeoutMs;
    }

    public int getNotificationHttpTimeoutMs() {
        return notificationHttpTimeoutMs;
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
     * Duration to sleep for after encountering an error emitting a window
     * event, given in milliseconds. Defaults to 10 seconds.
     */
    public void setSignalEmitFailureBackoffMs(long signalEmitFailureBackoffMs) {
        this.signalEmitFailureBackoffMs = signalEmitFailureBackoffMs;
    }

    public long getSignalEmitFailureBackoffMs() {
        return signalEmitFailureBackoffMs;
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
     * The name of the data collection that is used to send signals/commands to
     * Debezium. For multi-partition mode connectors, multiple signal data
     * collections can be specified as a comma-separated list. Signaling is
     * disabled when not set.
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
     * The factor used to scale the number of snapshot chunks per table. The
     * default behavior is to take 'row_count/snapshot.max.threads' to compute
     * the number of rows per chunks. This may not be ideal for larger tables,
     * and using the multiplier, the formula is adjusted to increase the number
     * of chunks by using 'row_count/(snapshot.max.threads *
     * snapshot.max.threads.multiplier).
     */
    public void setSnapshotMaxThreadsMultiplier(int snapshotMaxThreadsMultiplier) {
        this.snapshotMaxThreadsMultiplier = snapshotMaxThreadsMultiplier;
    }

    public int getSnapshotMaxThreadsMultiplier() {
        return snapshotMaxThreadsMultiplier;
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
     * The name of the schema where CDC change tables are located; defaults to
     * 'ASNCDC'
     */
    public void setCdcChangeTablesSchema(String cdcChangeTablesSchema) {
        this.cdcChangeTablesSchema = cdcChangeTablesSchema;
    }

    public String getCdcChangeTablesSchema() {
        return cdcChangeTablesSchema;
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
     * The URL to which notifications are POSTed as JSON. Required when 'http'
     * is in the list of enabled channels.
     */
    public void setNotificationHttpUrl(String notificationHttpUrl) {
        this.notificationHttpUrl = notificationHttpUrl;
    }

    public String getNotificationHttpUrl() {
        return notificationHttpUrl;
    }

    /**
     * Specify how failures during converting of event should be handled,
     * including: 'fail' throw an exception that the column of event conversion
     * is failed with unmatched schema type, causing the connector to be
     * stopped. it could need schema recovery to covert successfully; 'warn'
     * (the default) the value of column of event that conversion failed will be
     * null and be logged with warn level; 'skip' the value of column of event
     * that conversion failed will be null and be logged with debug level.
     */
    public void setEventConvertingFailureHandlingMode(
            String eventConvertingFailureHandlingMode) {
        this.eventConvertingFailureHandlingMode = eventConvertingFailureHandlingMode;
    }

    public String getEventConvertingFailureHandlingMode() {
        return eventConvertingFailureHandlingMode;
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
     * The fully-qualified class name of the storage implementation for table
     * metadata. The class must implement
     * io.debezium.relational.TableMappingStorage<io.debezium.relational.Table>.
     * Defaults to io.debezium.relational.ConcurrentMapTableMappingStorage for
     * in-memory storage.
     */
    public void setMemoryManagementTablesClass(
            String memoryManagementTablesClass) {
        this.memoryManagementTablesClass = memoryManagementTablesClass;
    }

    public String getMemoryManagementTablesClass() {
        return memoryManagementTablesClass;
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
     * A switch to control if the connector instance is responsible for updating
     * the prune table (usually done by IBM Apply agent) which will cause the
     * IBM Capture agent to prune read data from the change tables. default
     * false.
     */
    public void setUpdateCaptureTablePruneInd(boolean updateCaptureTablePruneInd) {
        this.updateCaptureTablePruneInd = updateCaptureTablePruneInd;
    }

    public boolean isUpdateCaptureTablePruneInd() {
        return updateCaptureTablePruneInd;
    }

    /**
     * The Kafka bootstrap server address used as input/output namespace/
     */
    public void setOpenlineageIntegrationDatasetKafkaBootstrapServers(
            String openlineageIntegrationDatasetKafkaBootstrapServers) {
        this.openlineageIntegrationDatasetKafkaBootstrapServers = openlineageIntegrationDatasetKafkaBootstrapServers;
    }

    public String getOpenlineageIntegrationDatasetKafkaBootstrapServers() {
        return openlineageIntegrationDatasetKafkaBootstrapServers;
    }

    /**
     * The name of the schema where CDC control structures are located; defaults
     * to 'ASNCDC'
     */
    public void setCdcControlSchema(String cdcControlSchema) {
        this.cdcControlSchema = cdcControlSchema;
    }

    public String getCdcControlSchema() {
        return cdcControlSchema;
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
     * Enable to collect various kind of statistics, like latencies in record
     * processing, and derived data like quantiles. By default collecting
     * statistics is enabled.
     */
    public void setStatisticsMetricsEnabled(boolean statisticsMetricsEnabled) {
        this.statisticsMetricsEnabled = statisticsMetricsEnabled;
    }

    public boolean isStatisticsMetricsEnabled() {
        return statisticsMetricsEnabled;
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
     * Controls which transaction isolation level is used and how long the
     * connector locks the monitored tables. The default is 'repeatable_read',
     * which means that repeatable read isolation level is used. In addition,
     * exclusive locks are taken only during schema snapshot. Using a value of
     * 'exclusive' ensures that the connector holds the exclusive lock (and thus
     * prevents any reads and updates) for all monitored tables during the
     * entire snapshot duration. In 'read_committed' mode no table locks or any
     * *long-lasting* row-level locks are acquired, but connector does not
     * guarantee snapshot consistency.In 'read_uncommitted' mode neither table
     * nor row-level locks are acquired, but connector does not guarantee
     * snapshot consistency.
     */
    public void setSnapshotIsolationMode(String snapshotIsolationMode) {
        this.snapshotIsolationMode = snapshotIsolationMode;
    }

    public String getSnapshotIsolationMode() {
        return snapshotIsolationMode;
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
     * If true, causes the connector to ignore the stop LSN value from the
     * IBMSNAP_REGISTER.CD_OLD_SYNCHPOINT column when polling. Apply this if
     * events are getting dropped due to the stop LSN being smaller than the
     * current range of LSNs. Only applies to z/OS platform.
     */
    public void setZStopLsnIgnore(boolean zStopLsnIgnore) {
        this.zStopLsnIgnore = zStopLsnIgnore;
    }

    public boolean isZStopLsnIgnore() {
        return zStopLsnIgnore;
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
     * The fully-qualified class name of the storage implementation for schema
     * metadata. The class must implement
     * io.debezium.relational.TableMappingStorage<io.debezium.relational.TableSchema>. Defaults to io.debezium.relational.ConcurrentMapTableMappingStorage for in-memory storage.
     */
    public void setMemoryManagementSchemasClass(
            String memoryManagementSchemasClass) {
        this.memoryManagementSchemasClass = memoryManagementSchemasClass;
    }

    public String getMemoryManagementSchemasClass() {
        return memoryManagementSchemasClass;
    }

    /**
     * Enable to skip publishing messages when there is no change in included
     * columns.This would essentially filter messages to be sent when there is
     * no change in columns included as per
     * column.include.list/column.exclude.list.For Postgres - this would require
     * REPLICA IDENTITY of table to be FULL.
     */
    public void setSkipMessagesWithoutChange(boolean skipMessagesWithoutChange) {
        this.skipMessagesWithoutChange = skipMessagesWithoutChange;
    }

    public boolean isSkipMessagesWithoutChange() {
        return skipMessagesWithoutChange;
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
     * The databases for which changes are to be captured
     */
    public void setDatabaseIncludeList(String databaseIncludeList) {
        this.databaseIncludeList = databaseIncludeList;
    }

    public String getDatabaseIncludeList() {
        return databaseIncludeList;
    }

    /**
     * Specify how field names should be adjusted for compatibility with the
     * message converter used by the connector, including: 'avro' replaces the
     * characters that cannot be used in the Avro type name with underscore;
     * 'avro_unicode' replaces the underscore or characters that cannot be used
     * in the Avro type name with corresponding unicode like _uxxxx. Note: _ is
     * an escape sequence like backslash in Java;'none' does not apply any
     * adjustment (default)
     */
    public void setFieldNameAdjustmentMode(String fieldNameAdjustmentMode) {
        this.fieldNameAdjustmentMode = fieldNameAdjustmentMode;
    }

    public String getFieldNameAdjustmentMode() {
        return fieldNameAdjustmentMode;
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
     * The maximum number of records that should be loaded into memory while
     * streaming. A value of '0' uses the default JDBC fetch size. The default
     * value is '10000'.
     */
    public void setQueryFetchSize(int queryFetchSize) {
        this.queryFetchSize = queryFetchSize;
    }

    public int getQueryFetchSize() {
        return queryFetchSize;
    }

    /**
     * When enabled, uses the legacy table-per-thread parallel snapshot
     * algorithm. When set to false (the default), tables are split into chunks
     * and processed across all snapshot threads, allowing for higher
     * concurrency for snapshots.
     */
    public void setLegacySnapshotMaxThreads(boolean legacySnapshotMaxThreads) {
        this.legacySnapshotMaxThreads = legacySnapshotMaxThreads;
    }

    public boolean isLegacySnapshotMaxThreads() {
        return legacySnapshotMaxThreads;
    }

    /**
     * The maximum number of seconds for a streaming query to include that
     * query's result set, starting from the earliest row in this query.  Used
     * to limit the size of queries when the change table is large to avoid
     * excessive resource usage. If 0, no timespan limit will apply.
     */
    public void setStreamingQueryTimespanSeconds(
            int streamingQueryTimespanSeconds) {
        this.streamingQueryTimespanSeconds = streamingQueryTimespanSeconds;
    }

    public int getStreamingQueryTimespanSeconds() {
        return streamingQueryTimespanSeconds;
    }

    /**
     * The minimum number of milliseconds between the connector instance's
     * update of the prune point for the subscription set.  Default is 10000 (10
     * seconds).
     */
    public void setUpdateCaptureTablePruneMinIntervalMs(
            int updateCaptureTablePruneMinIntervalMs) {
        this.updateCaptureTablePruneMinIntervalMs = updateCaptureTablePruneMinIntervalMs;
    }

    public int getUpdateCaptureTablePruneMinIntervalMs() {
        return updateCaptureTablePruneMinIntervalMs;
    }

    /**
     * Time to wait for new change events to appear after receiving no events,
     * given in milliseconds. Defaults to 500 ms. Values will be capped at 5000
     * ms.
     */
    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * The maximum number of collections or tables that can be captured by the
     * connector. When this limit is exceeded, the action specified by
     * 'guardrail.collections.limit.action' will be taken. Set to 0 to disable
     * this guardrail.
     */
    public void setGuardrailCollectionsMax(int guardrailCollectionsMax) {
        this.guardrailCollectionsMax = guardrailCollectionsMax;
    }

    public int getGuardrailCollectionsMax() {
        return guardrailCollectionsMax;
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
     * The apply_qual name that represents the apply agent (this instance) to
     * the subscription set.
     */
    public void setUpdateCaptureTablePruneApplyQual(
            String updateCaptureTablePruneApplyQual) {
        this.updateCaptureTablePruneApplyQual = updateCaptureTablePruneApplyQual;
    }

    public String getUpdateCaptureTablePruneApplyQual() {
        return updateCaptureTablePruneApplyQual;
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
     * Password of the database user to be used when connecting to the database.
     */
    public void setDatabasePassword(String databasePassword) {
        this.databasePassword = databasePassword;
    }

    public String getDatabasePassword() {
        return databasePassword;
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
     * Enable/Disable Debezium context headers that provides essential metadata
     * for tracking and identifying the source of CDC events in downstream
     * processing systems.
     */
    public void setExtendedHeadersEnabled(boolean extendedHeadersEnabled) {
        this.extendedHeadersEnabled = extendedHeadersEnabled;
    }

    public boolean isExtendedHeadersEnabled() {
        return extendedHeadersEnabled;
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
     * Specify the action to take when a guardrail collections limit is
     * exceeded: 'warn' (the default) logs a warning message and continues
     * processing; 'fail' stops the connector with an error.
     */
    public void setGuardrailCollectionsLimitAction(
            String guardrailCollectionsLimitAction) {
        this.guardrailCollectionsLimitAction = guardrailCollectionsLimitAction;
    }

    public String getGuardrailCollectionsLimitAction() {
        return guardrailCollectionsLimitAction;
    }

    /**
     * Regular expression identifying configuration keys whose values should be
     * masked. When set, this custom pattern replaces Debeziums default password
     * masking pattern.
     */
    public void setCustomSanitizePattern(String customSanitizePattern) {
        this.customSanitizePattern = customSanitizePattern;
    }

    public String getCustomSanitizePattern() {
        return customSanitizePattern;
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
     * Controls how Debezium deduplicates identical schema objects (tables,
     * columns, attributes) in memory using an interner.
     */
    public void setSchemaHistoryInternalMemoryOptimization(
            String schemaHistoryInternalMemoryOptimization) {
        this.schemaHistoryInternalMemoryOptimization = schemaHistoryInternalMemoryOptimization;
    }

    public String getSchemaHistoryInternalMemoryOptimization() {
        return schemaHistoryInternalMemoryOptimization;
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
     * The maximum number of retries after encountering an error emitting a
     * window event (-1 = no limit, 0 = disabled, > 0 = num of retries).
     * Defaults to 0.
     */
    public void setSignalEmitFailureMaxRetries(int signalEmitFailureMaxRetries) {
        this.signalEmitFailureMaxRetries = signalEmitFailureMaxRetries;
    }

    public int getSignalEmitFailureMaxRetries() {
        return signalEmitFailureMaxRetries;
    }

    /**
     * Time, date, and timestamps can be represented with different kinds of
     * precisions, including: 'adaptive' (the default) bases the precision of
     * time, date, and timestamp values on the database column's precision;
     * 'adaptive_time_microseconds' like 'adaptive' mode, but TIME fields always
     * use microseconds precision; 'connect' always represents time, date, and
     * timestamp values using Kafka Connect's built-in representations for Time,
     * Date, and Timestamp, which uses millisecond precision regardless of the
     * database columns' precision; 'isostring' represents time, date, and
     * timestamp values as ISO-8601 formatted strings at the UTC time zone;
     * 'structured' represents time, date, timestamp, and datetime values as
     * Kafka Connect structs with separate calendar and clock components;
     * 'microseconds' always represents time, date, and timestamp values using
     * microsecond precision; 'nanoseconds' always represents time, date, and
     * timestamp values using nanosecond precision.
     */
    public void setTimePrecisionMode(String timePrecisionMode) {
        this.timePrecisionMode = timePrecisionMode;
    }

    public String getTimePrecisionMode() {
        return timePrecisionMode;
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
     * Port of the database server.
     */
    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    /**
     * The name of a prepared statement in the database that will be used
     * instead of direct sql to allow for DBA control of the update code where
     * needed.
     */
    public void setUpdateCaptureTablePruneProcedureOverrideName(
            String updateCaptureTablePruneProcedureOverrideName) {
        this.updateCaptureTablePruneProcedureOverrideName = updateCaptureTablePruneProcedureOverrideName;
    }

    public String getUpdateCaptureTablePruneProcedureOverrideName() {
        return updateCaptureTablePruneProcedureOverrideName;
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
     * Number of retries after the first failed HTTP notification delivery
     * attempt. Terminal notifications (COMPLETED/ABORTED/SKIPPED) are always
     * retried at least 3 times.
     */
    public void setNotificationHttpRetries(int notificationHttpRetries) {
        this.notificationHttpRetries = notificationHttpRetries;
    }

    public int getNotificationHttpRetries() {
        return notificationHttpRetries;
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

    /**
     * Informs connector which Db2 implementation platform it is connected to.
     * The default is 'LUW', which means Windows, UNIX, Linux. Using a value of
     * 'Z' ensures that the Db2 for z/OS specific SQL statements are used.
     */
    public void setDb2Platform(String db2Platform) {
        this.db2Platform = db2Platform;
    }

    public String getDb2Platform() {
        return db2Platform;
    }

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "snapshot.locking.mode", snapshotLockingMode);
        addPropertyIfNotNull(configBuilder, "message.key.columns", messageKeyColumns);
        addPropertyIfNotNull(configBuilder, "transaction.metadata.factory", transactionMetadataFactory);
        addPropertyIfNotNull(configBuilder, "notification.http.allow.private.networks", notificationHttpAllowPrivateNetworks);
        addPropertyIfNotNull(configBuilder, "custom.metric.tags", customMetricTags);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.lsn.decrement", updateCaptureTablePruneLsnDecrement);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.set.name", updateCaptureTablePruneSetName);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.target.server", updateCaptureTablePruneTargetServer);
        addPropertyIfNotNull(configBuilder, "notification.http.timeout.ms", notificationHttpTimeoutMs);
        addPropertyIfNotNull(configBuilder, "signal.enabled.channels", signalEnabledChannels);
        addPropertyIfNotNull(configBuilder, "signal.emit.failure.backoff.ms", signalEmitFailureBackoffMs);
        addPropertyIfNotNull(configBuilder, "include.schema.changes", includeSchemaChanges);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.max.threads.multiplier", snapshotMaxThreadsMultiplier);
        addPropertyIfNotNull(configBuilder, "snapshot.lock.timeout.ms", snapshotLockTimeoutMs);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.tags", openlineageIntegrationJobTags);
        addPropertyIfNotNull(configBuilder, "cdc.change.tables.schema", cdcChangeTablesSchema);
        addPropertyIfNotNull(configBuilder, "database.dbname", databaseDbname);
        addPropertyIfNotNull(configBuilder, "snapshot.tables.order.by.row.count", snapshotTablesOrderByRowCount);
        addPropertyIfNotNull(configBuilder, "snapshot.select.statement.overrides", snapshotSelectStatementOverrides);
        addPropertyIfNotNull(configBuilder, "table.exclude.list", tableExcludeList);
        addPropertyIfNotNull(configBuilder, "database.exclude.list", databaseExcludeList);
        addPropertyIfNotNull(configBuilder, "notification.http.url", notificationHttpUrl);
        addPropertyIfNotNull(configBuilder, "event.converting.failure.handling.mode", eventConvertingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "topic.naming.strategy", topicNamingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.data", snapshotModeConfigurationBasedSnapshotData);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.chunk.size", incrementalSnapshotChunkSize);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.owners", openlineageIntegrationJobOwners);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.config.file.path", openlineageIntegrationConfigFilePath);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "memory.management.tables.class", memoryManagementTablesClass);
        addPropertyIfNotNull(configBuilder, "executor.shutdown.timeout.ms", executorShutdownTimeoutMs);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.on.data.error", snapshotModeConfigurationBasedSnapshotOnDataError);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.file.filename", schemaHistoryInternalFileFilename);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "decimal.handling.mode", decimalHandlingMode);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.ind", updateCaptureTablePruneInd);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.dataset.kafka.bootstrap.servers", openlineageIntegrationDatasetKafkaBootstrapServers);
        addPropertyIfNotNull(configBuilder, "cdc.control.schema", cdcControlSchema);
        addPropertyIfNotNull(configBuilder, "table.ignore.builtin", tableIgnoreBuiltin);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.start.stream", snapshotModeConfigurationBasedStartStream);
        addPropertyIfNotNull(configBuilder, "statistics.metrics.enabled", statisticsMetricsEnabled);
        addPropertyIfNotNull(configBuilder, "signal.poll.interval.ms", signalPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "notification.enabled.channels", notificationEnabledChannels);
        addPropertyIfNotNull(configBuilder, "snapshot.isolation.mode", snapshotIsolationMode);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "z.stop.lsn.ignore", zStopLsnIgnore);
        addPropertyIfNotNull(configBuilder, "notification.sink.topic.name", notificationSinkTopicName);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.custom.name", snapshotModeCustomName);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "memory.management.schemas.class", memoryManagementSchemasClass);
        addPropertyIfNotNull(configBuilder, "skip.messages.without.change", skipMessagesWithoutChange);
        addPropertyIfNotNull(configBuilder, "table.include.list", tableIncludeList);
        addPropertyIfNotNull(configBuilder, "database.include.list", databaseIncludeList);
        addPropertyIfNotNull(configBuilder, "field.name.adjustment.mode", fieldNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "streaming.delay.ms", streamingDelayMs);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.namespace", openlineageIntegrationJobNamespace);
        addPropertyIfNotNull(configBuilder, "database.query.timeout.ms", databaseQueryTimeoutMs);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "legacy.snapshot.max.threads", legacySnapshotMaxThreads);
        addPropertyIfNotNull(configBuilder, "streaming.query.timespan.seconds", streamingQueryTimespanSeconds);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.min.interval.ms", updateCaptureTablePruneMinIntervalMs);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "guardrail.collections.max", guardrailCollectionsMax);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.apply.qual", updateCaptureTablePruneApplyQual);
        addPropertyIfNotNull(configBuilder, "database.user", databaseUser);
        addPropertyIfNotNull(configBuilder, "datatype.propagate.source.type", datatypePropagateSourceType);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.watermarking.strategy", incrementalSnapshotWatermarkingStrategy);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.on.schema.error", snapshotModeConfigurationBasedSnapshotOnSchemaError);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.skip.unparseable.ddl", schemaHistoryInternalSkipUnparseableDdl);
        addPropertyIfNotNull(configBuilder, "column.include.list", columnIncludeList);
        addPropertyIfNotNull(configBuilder, "column.propagate.source.type", columnPropagateSourceType);
        addPropertyIfNotNull(configBuilder, "errors.max.retries", errorsMaxRetries);
        addPropertyIfNotNull(configBuilder, "database.password", databasePassword);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.job.description", openlineageIntegrationJobDescription);
        addPropertyIfNotNull(configBuilder, "extended.headers.enabled", extendedHeadersEnabled);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "guardrail.collections.limit.action", guardrailCollectionsLimitAction);
        addPropertyIfNotNull(configBuilder, "custom.sanitize.pattern", customSanitizePattern);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.tables.ddl", schemaHistoryInternalStoreOnlyCapturedTablesDdl);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.store.only.captured.databases.ddl", schemaHistoryInternalStoreOnlyCapturedDatabasesDdl);
        addPropertyIfNotNull(configBuilder, "topic.prefix", topicPrefix);
        addPropertyIfNotNull(configBuilder, "sourceinfo.struct.maker", sourceinfoStructMaker);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.memory.optimization", schemaHistoryInternalMemoryOptimization);
        addPropertyIfNotNull(configBuilder, "openlineage.integration.enabled", openlineageIntegrationEnabled);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "signal.emit.failure.max.retries", signalEmitFailureMaxRetries);
        addPropertyIfNotNull(configBuilder, "time.precision.mode", timePrecisionMode);
        addPropertyIfNotNull(configBuilder, "snapshot.mode.configuration.based.snapshot.schema", snapshotModeConfigurationBasedSnapshotSchema);
        addPropertyIfNotNull(configBuilder, "post.processors", postProcessors);
        addPropertyIfNotNull(configBuilder, "database.port", databasePort);
        addPropertyIfNotNull(configBuilder, "update.capture.table.prune.procedure.override.name", updateCaptureTablePruneProcedureOverrideName);
        addPropertyIfNotNull(configBuilder, "schema.history.internal", schemaHistoryInternal);
        addPropertyIfNotNull(configBuilder, "column.exclude.list", columnExcludeList);
        addPropertyIfNotNull(configBuilder, "notification.http.retries", notificationHttpRetries);
        addPropertyIfNotNull(configBuilder, "database.hostname", databaseHostname);
        addPropertyIfNotNull(configBuilder, "connection.validation.timeout.ms", connectionValidationTimeoutMs);
        addPropertyIfNotNull(configBuilder, "db2.platform", db2Platform);
        
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
        if (isFieldValueNotSet(topicPrefix)) {
        	return ConfigurationValidation.notValid("Required field 'topicPrefix' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "db2";
    }
}