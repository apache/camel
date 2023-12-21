package org.apache.camel.component.debezium.configuration;

import io.debezium.config.Configuration;
import io.debezium.connector.mongodb.MongoDbConnector;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class MongoDbConnectorEmbeddedDebeziumConfiguration
        extends
            EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer,mongodb";
    @UriParam(label = LABEL_NAME)
    private String customMetricTags;
    @UriParam(label = LABEL_NAME)
    private String mongodbConnectionString;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String mongodbPassword;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int queryFetchSize = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "source")
    private String signalEnabledChannels = "source";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslEnabled = false;
    @UriParam(label = LABEL_NAME, javaType = "java.time.Duration")
    private int cursorMaxAwaitTimeMs;
    @UriParam(label = LABEL_NAME)
    private String fieldRenames;
    @UriParam(label = LABEL_NAME, defaultValue = "30s", javaType = "java.time.Duration")
    private int mongodbServerSelectionTimeoutMs = 30000;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME)
    private String signalDataCollection;
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private int mongodbConnectTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME, defaultValue = "30s", javaType = "java.time.Duration")
    private long mongodbPollIntervalMs = 30000;
    @UriParam(label = LABEL_NAME, defaultValue = "INSERT_INSERT")
    private String incrementalSnapshotWatermarkingStrategy = "INSERT_INSERT";
    @UriParam(label = LABEL_NAME)
    private String mongodbUser;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    private String snapshotCollectionFilterOverrides;
    @UriParam(label = LABEL_NAME)
    private String fieldExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "-1")
    private int errorsMaxRetries = -1;
    @UriParam(label = LABEL_NAME)
    private String databaseExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "t")
    private String skippedOperations = "t";
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.schema.SchemaTopicNamingStrategy")
    private String topicNamingStrategy = "io.debezium.schema.SchemaTopicNamingStrategy";
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME)
    private String collectionIncludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "sharded")
    private String mongodbConnectionMode = "sharded";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "change_streams_update_full")
    private String captureMode = "change_streams_update_full";
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
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.connector.mongodb.MongoDbSourceInfoStructMaker")
    private String sourceinfoStructMaker = "io.debezium.connector.mongodb.MongoDbSourceInfoStructMaker";
    @UriParam(label = LABEL_NAME, defaultValue = "admin")
    private String mongodbAuthsource = "admin";
    @UriParam(label = LABEL_NAME)
    private String collectionExcludeList;
    @UriParam(label = LABEL_NAME)
    private String snapshotIncludeCollectionList;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long maxQueueSizeInBytes = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "5s", javaType = "java.time.Duration")
    private long signalPollIntervalMs = 5000;
    @UriParam(label = LABEL_NAME)
    private String postProcessors;
    @UriParam(label = LABEL_NAME)
    private String notificationEnabledChannels;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int mongodbSocketTimeoutMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int snapshotMaxThreads = 1;
    @UriParam(label = LABEL_NAME)
    private String notificationSinkTopicName;
    @UriParam(label = LABEL_NAME, defaultValue = "none")
    private String schemaNameAdjustmentMode = "none";
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslInvalidHostnameAllowed = false;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private int mongodbHeartbeatFrequencyMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String databaseIncludeList;

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
     * Database connection string.
     */
    public void setMongodbConnectionString(String mongodbConnectionString) {
        this.mongodbConnectionString = mongodbConnectionString;
    }

    public String getMongodbConnectionString() {
        return mongodbConnectionString;
    }

    /**
     * Password to be used when connecting to MongoDB, if necessary.
     */
    public void setMongodbPassword(String mongodbPassword) {
        this.mongodbPassword = mongodbPassword;
    }

    public String getMongodbPassword() {
        return mongodbPassword;
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
     * Should connector use SSL to connect to MongoDB instances
     */
    public void setMongodbSslEnabled(boolean mongodbSslEnabled) {
        this.mongodbSslEnabled = mongodbSslEnabled;
    }

    public boolean isMongodbSslEnabled() {
        return mongodbSslEnabled;
    }

    /**
     * The maximum processing time in milliseconds to wait for the oplog cursor
     * to process a single poll request
     */
    public void setCursorMaxAwaitTimeMs(int cursorMaxAwaitTimeMs) {
        this.cursorMaxAwaitTimeMs = cursorMaxAwaitTimeMs;
    }

    public int getCursorMaxAwaitTimeMs() {
        return cursorMaxAwaitTimeMs;
    }

    /**
     * A comma-separated list of the fully-qualified replacements of fields that
     * should be used to rename fields in change event message values.
     * Fully-qualified replacements for fields are of the form
     * databaseName.collectionName.fieldName.nestedFieldName:newNestedFieldName,
     * where databaseName and collectionName may contain the wildcard (*) which
     * matches any characters, the colon character (:) is used to determine
     * rename mapping of field.
     */
    public void setFieldRenames(String fieldRenames) {
        this.fieldRenames = fieldRenames;
    }

    public String getFieldRenames() {
        return fieldRenames;
    }

    /**
     * The server selection timeout, given in milliseconds. Defaults to 10
     * seconds (10,000 ms).
     */
    public void setMongodbServerSelectionTimeoutMs(
            int mongodbServerSelectionTimeoutMs) {
        this.mongodbServerSelectionTimeoutMs = mongodbServerSelectionTimeoutMs;
    }

    public int getMongodbServerSelectionTimeoutMs() {
        return mongodbServerSelectionTimeoutMs;
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
     * The connection timeout, given in milliseconds. Defaults to 10 seconds
     * (10,000 ms).
     */
    public void setMongodbConnectTimeoutMs(int mongodbConnectTimeoutMs) {
        this.mongodbConnectTimeoutMs = mongodbConnectTimeoutMs;
    }

    public int getMongodbConnectTimeoutMs() {
        return mongodbConnectTimeoutMs;
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
     * Interval for looking for new, removed, or changed replica sets, given in
     * milliseconds. Defaults to 30 seconds (30,000 ms).
     */
    public void setMongodbPollIntervalMs(long mongodbPollIntervalMs) {
        this.mongodbPollIntervalMs = mongodbPollIntervalMs;
    }

    public long getMongodbPollIntervalMs() {
        return mongodbPollIntervalMs;
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
     * Database user for connecting to MongoDB, if necessary.
     */
    public void setMongodbUser(String mongodbUser) {
        this.mongodbUser = mongodbUser;
    }

    public String getMongodbUser() {
        return mongodbUser;
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
     * This property contains a comma-separated list of
     * <dbName>.<collectionName>, for which  the initial snapshot may be a
     * subset of data present in the data source. The subset would be defined by
     * mongodb filter query specified as value for property
     * snapshot.collection.filter.override.<dbname>.<collectionName>
     */
    public void setSnapshotCollectionFilterOverrides(
            String snapshotCollectionFilterOverrides) {
        this.snapshotCollectionFilterOverrides = snapshotCollectionFilterOverrides;
    }

    public String getSnapshotCollectionFilterOverrides() {
        return snapshotCollectionFilterOverrides;
    }

    /**
     * A comma-separated list of the fully-qualified names of fields that should
     * be excluded from change event message values
     */
    public void setFieldExcludeList(String fieldExcludeList) {
        this.fieldExcludeList = fieldExcludeList;
    }

    public String getFieldExcludeList() {
        return fieldExcludeList;
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
     * A comma-separated list of regular expressions or literals that match the
     * database names for which changes are to be excluded
     */
    public void setDatabaseExcludeList(String databaseExcludeList) {
        this.databaseExcludeList = databaseExcludeList;
    }

    public String getDatabaseExcludeList() {
        return databaseExcludeList;
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
     * The criteria for running a snapshot upon startup of the connector. Select
     * one of the following snapshot options: 'initial' (default):  If the
     * connector does not detect any offsets for the logical server name, it
     * runs a snapshot that captures the current full state of the configured
     * tables. After the snapshot completes, the connector begins to stream
     * changes from the oplog. 'never': The connector does not run a snapshot.
     * Upon first startup, the connector immediately begins reading from the
     * beginning of the oplog.
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
     * A comma-separated list of regular expressions or literals that match the
     * collection names for which changes are to be captured
     */
    public void setCollectionIncludeList(String collectionIncludeList) {
        this.collectionIncludeList = collectionIncludeList;
    }

    public String getCollectionIncludeList() {
        return collectionIncludeList;
    }

    /**
     * The method used to connect to MongoDB cluster. Options include:
     * 'replica_set' to individually connect to each replica set / shard
     * 'sharded' (the default) to connect via single connection obtained from
     * connection string
     */
    public void setMongodbConnectionMode(String mongodbConnectionMode) {
        this.mongodbConnectionMode = mongodbConnectionMode;
    }

    public String getMongodbConnectionMode() {
        return mongodbConnectionMode;
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
     * The method used to capture changes from MongoDB server. Options include:
     * 'change_streams' to capture changes via MongoDB Change Streams, update
     * events do not contain full documents; 'change_streams_update_full' (the
     * default) to capture changes via MongoDB Change Streams, update events
     * contain full documents
     */
    public void setCaptureMode(String captureMode) {
        this.captureMode = captureMode;
    }

    public String getCaptureMode() {
        return captureMode;
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
     * Database containing user credentials.
     */
    public void setMongodbAuthsource(String mongodbAuthsource) {
        this.mongodbAuthsource = mongodbAuthsource;
    }

    public String getMongodbAuthsource() {
        return mongodbAuthsource;
    }

    /**
     * A comma-separated list of regular expressions or literals that match the
     * collection names for which changes are to be excluded
     */
    public void setCollectionExcludeList(String collectionExcludeList) {
        this.collectionExcludeList = collectionExcludeList;
    }

    public String getCollectionExcludeList() {
        return collectionExcludeList;
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
     * The socket timeout, given in milliseconds. Defaults to 0 ms.
     */
    public void setMongodbSocketTimeoutMs(int mongodbSocketTimeoutMs) {
        this.mongodbSocketTimeoutMs = mongodbSocketTimeoutMs;
    }

    public int getMongodbSocketTimeoutMs() {
        return mongodbSocketTimeoutMs;
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
     * Whether invalid host names are allowed when using SSL. If true the
     * connection will not prevent man-in-the-middle attacks
     */
    public void setMongodbSslInvalidHostnameAllowed(
            boolean mongodbSslInvalidHostnameAllowed) {
        this.mongodbSslInvalidHostnameAllowed = mongodbSslInvalidHostnameAllowed;
    }

    public boolean isMongodbSslInvalidHostnameAllowed() {
        return mongodbSslInvalidHostnameAllowed;
    }

    /**
     * The frequency that the cluster monitor attempts to reach each server.
     * Defaults to 10 seconds (10,000 ms).
     */
    public void setMongodbHeartbeatFrequencyMs(int mongodbHeartbeatFrequencyMs) {
        this.mongodbHeartbeatFrequencyMs = mongodbHeartbeatFrequencyMs;
    }

    public int getMongodbHeartbeatFrequencyMs() {
        return mongodbHeartbeatFrequencyMs;
    }

    /**
     * A comma-separated list of regular expressions or literals that match the
     * database names for which changes are to be captured
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
        
        addPropertyIfNotNull(configBuilder, "custom.metric.tags", customMetricTags);
        addPropertyIfNotNull(configBuilder, "mongodb.connection.string", mongodbConnectionString);
        addPropertyIfNotNull(configBuilder, "mongodb.password", mongodbPassword);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "signal.enabled.channels", signalEnabledChannels);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.enabled", mongodbSslEnabled);
        addPropertyIfNotNull(configBuilder, "cursor.max.await.time.ms", cursorMaxAwaitTimeMs);
        addPropertyIfNotNull(configBuilder, "field.renames", fieldRenames);
        addPropertyIfNotNull(configBuilder, "mongodb.server.selection.timeout.ms", mongodbServerSelectionTimeoutMs);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "signal.data.collection", signalDataCollection);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "mongodb.connect.timeout.ms", mongodbConnectTimeoutMs);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "mongodb.poll.interval.ms", mongodbPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "incremental.snapshot.watermarking.strategy", incrementalSnapshotWatermarkingStrategy);
        addPropertyIfNotNull(configBuilder, "mongodb.user", mongodbUser);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "snapshot.collection.filter.overrides", snapshotCollectionFilterOverrides);
        addPropertyIfNotNull(configBuilder, "field.exclude.list", fieldExcludeList);
        addPropertyIfNotNull(configBuilder, "errors.max.retries", errorsMaxRetries);
        addPropertyIfNotNull(configBuilder, "database.exclude.list", databaseExcludeList);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "topic.naming.strategy", topicNamingStrategy);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "collection.include.list", collectionIncludeList);
        addPropertyIfNotNull(configBuilder, "mongodb.connection.mode", mongodbConnectionMode);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "capture.mode", captureMode);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "schema.history.internal.file.filename", schemaHistoryInternalFileFilename);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "topic.prefix", topicPrefix);
        addPropertyIfNotNull(configBuilder, "sourceinfo.struct.maker", sourceinfoStructMaker);
        addPropertyIfNotNull(configBuilder, "mongodb.authsource", mongodbAuthsource);
        addPropertyIfNotNull(configBuilder, "collection.exclude.list", collectionExcludeList);
        addPropertyIfNotNull(configBuilder, "snapshot.include.collection.list", snapshotIncludeCollectionList);
        addPropertyIfNotNull(configBuilder, "max.queue.size.in.bytes", maxQueueSizeInBytes);
        addPropertyIfNotNull(configBuilder, "signal.poll.interval.ms", signalPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "post.processors", postProcessors);
        addPropertyIfNotNull(configBuilder, "notification.enabled.channels", notificationEnabledChannels);
        addPropertyIfNotNull(configBuilder, "mongodb.socket.timeout.ms", mongodbSocketTimeoutMs);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "snapshot.max.threads", snapshotMaxThreads);
        addPropertyIfNotNull(configBuilder, "notification.sink.topic.name", notificationSinkTopicName);
        addPropertyIfNotNull(configBuilder, "schema.name.adjustment.mode", schemaNameAdjustmentMode);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.invalid.hostname.allowed", mongodbSslInvalidHostnameAllowed);
        addPropertyIfNotNull(configBuilder, "mongodb.heartbeat.frequency.ms", mongodbHeartbeatFrequencyMs);
        addPropertyIfNotNull(configBuilder, "database.include.list", databaseIncludeList);
        
        return configBuilder.build();
    }

    @Override
    protected Class configureConnectorClass() {
        return MongoDbConnector.class;
    }

    @Override
    protected ConfigurationValidation validateConnectorConfiguration() {
        if (isFieldValueNotSet(mongodbPassword)) {
        	return ConfigurationValidation.notValid("Required field 'mongodbPassword' must be set.");
        }
        if (isFieldValueNotSet(topicPrefix)) {
        	return ConfigurationValidation.notValid("Required field 'topicPrefix' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "mongodb";
    }
}