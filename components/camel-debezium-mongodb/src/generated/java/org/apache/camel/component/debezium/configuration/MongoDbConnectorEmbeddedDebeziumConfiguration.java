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
    @UriParam(label = LABEL_NAME, defaultValue = "8192")
    private int maxQueueSize = 8192;
    @UriParam(label = LABEL_NAME)
    private String collectionIncludeList;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String mongodbPassword;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int queryFetchSize = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private long retriableRestartConnectorWaitMs = 10000;
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int initialSyncMaxThreads = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean provideTransactionMetadata = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslEnabled = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean mongodbMembersAutoDiscover = true;
    @UriParam(label = LABEL_NAME)
    private String fieldRenames;
    @UriParam(label = LABEL_NAME, defaultValue = "30s", javaType = "java.time.Duration")
    private int mongodbServerSelectionTimeoutMs = 30000;
    @UriParam(label = LABEL_NAME, defaultValue = "500ms", javaType = "java.time.Duration")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME, defaultValue = "admin")
    private String mongodbAuthsource = "admin";
    @UriParam(label = LABEL_NAME)
    private String converters;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME, defaultValue = "10s", javaType = "java.time.Duration")
    private int mongodbConnectTimeoutMs = 10000;
    @UriParam(label = LABEL_NAME)
    private String mongodbHosts;
    @UriParam(label = LABEL_NAME, defaultValue = "1s", javaType = "java.time.Duration")
    private long connectBackoffInitialDelayMs = 1000;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME)
    private String collectionExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "30s", javaType = "java.time.Duration")
    private long mongodbPollIntervalMs = 30000;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean sanitizeFieldNames = false;
    @UriParam(label = LABEL_NAME, defaultValue = "16")
    private int connectMaxAttempts = 16;
    @UriParam(label = LABEL_NAME)
    private String mongodbUser;
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int mongodbSocketTimeoutMs = 0;
    @UriParam(label = LABEL_NAME, defaultValue = "fail")
    private String eventProcessingFailureHandlingMode = "fail";
    @UriParam(label = LABEL_NAME, defaultValue = "v2")
    private String sourceStructVersion = "v2";
    @UriParam(label = LABEL_NAME, defaultValue = "0ms", javaType = "java.time.Duration")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String mongodbName;
    @UriParam(label = LABEL_NAME, defaultValue = "2m", javaType = "java.time.Duration")
    private long connectBackoffMaxDelayMs = 120000;
    @UriParam(label = LABEL_NAME)
    private String fieldExcludeList;
    @UriParam(label = LABEL_NAME)
    private String databaseExcludeList;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslInvalidHostnameAllowed = false;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME)
    private String skippedOperations;
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";
    @UriParam(label = LABEL_NAME)
    private String databaseIncludeList;

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
     * A comma-separated list of regular expressions that match the collection
     * names for which changes are to be captured
     */
    public void setCollectionIncludeList(String collectionIncludeList) {
        this.collectionIncludeList = collectionIncludeList;
    }

    public String getCollectionIncludeList() {
        return collectionIncludeList;
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
     * streaming.  A value of `0` uses the default JDBC fetch size.
     */
    public void setQueryFetchSize(int queryFetchSize) {
        this.queryFetchSize = queryFetchSize;
    }

    public int getQueryFetchSize() {
        return queryFetchSize;
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
     * Maximum number of threads used to perform an initial sync of the
     * collections in a replica set. Defaults to 1.
     */
    public void setInitialSyncMaxThreads(int initialSyncMaxThreads) {
        this.initialSyncMaxThreads = initialSyncMaxThreads;
    }

    public int getInitialSyncMaxThreads() {
        return initialSyncMaxThreads;
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
     * Should connector use SSL to connect to MongoDB instances
     */
    public void setMongodbSslEnabled(boolean mongodbSslEnabled) {
        this.mongodbSslEnabled = mongodbSslEnabled;
    }

    public boolean isMongodbSslEnabled() {
        return mongodbSslEnabled;
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
     * Specifies whether the addresses in 'hosts' are seeds that should be used
     * to discover all members of the cluster or replica set ('true'), or
     * whether the address(es) in 'hosts' should be used as is ('false'). The
     * default is 'true'.
     */
    public void setMongodbMembersAutoDiscover(boolean mongodbMembersAutoDiscover) {
        this.mongodbMembersAutoDiscover = mongodbMembersAutoDiscover;
    }

    public boolean isMongodbMembersAutoDiscover() {
        return mongodbMembersAutoDiscover;
    }

    /**
     * Description is not available here, please check Debezium website for
     * corresponding key 'field.renames' description.
     */
    public void setFieldRenames(String fieldRenames) {
        this.fieldRenames = fieldRenames;
    }

    public String getFieldRenames() {
        return fieldRenames;
    }

    /**
     * The server selection timeout in milliseconds
     */
    public void setMongodbServerSelectionTimeoutMs(
            int mongodbServerSelectionTimeoutMs) {
        this.mongodbServerSelectionTimeoutMs = mongodbServerSelectionTimeoutMs;
    }

    public int getMongodbServerSelectionTimeoutMs() {
        return mongodbServerSelectionTimeoutMs;
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
     * Database containing user credentials.
     */
    public void setMongodbAuthsource(String mongodbAuthsource) {
        this.mongodbAuthsource = mongodbAuthsource;
    }

    public String getMongodbAuthsource() {
        return mongodbAuthsource;
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
     * The connection timeout in milliseconds
     */
    public void setMongodbConnectTimeoutMs(int mongodbConnectTimeoutMs) {
        this.mongodbConnectTimeoutMs = mongodbConnectTimeoutMs;
    }

    public int getMongodbConnectTimeoutMs() {
        return mongodbConnectTimeoutMs;
    }

    /**
     * The hostname and port pairs (in the form 'host' or 'host:port') of the
     * MongoDB server(s) in the replica set.
     */
    public void setMongodbHosts(String mongodbHosts) {
        this.mongodbHosts = mongodbHosts;
    }

    public String getMongodbHosts() {
        return mongodbHosts;
    }

    /**
     * The initial delay when trying to reconnect to a primary after a
     * connection cannot be made or when no primary is available. Defaults to 1
     * second (1000 ms).
     */
    public void setConnectBackoffInitialDelayMs(
            long connectBackoffInitialDelayMs) {
        this.connectBackoffInitialDelayMs = connectBackoffInitialDelayMs;
    }

    public long getConnectBackoffInitialDelayMs() {
        return connectBackoffInitialDelayMs;
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
     * A comma-separated list of regular expressions that match the collection
     * names for which changes are to be excluded
     */
    public void setCollectionExcludeList(String collectionExcludeList) {
        this.collectionExcludeList = collectionExcludeList;
    }

    public String getCollectionExcludeList() {
        return collectionExcludeList;
    }

    /**
     * Frequency in milliseconds to look for new, removed, or changed replica
     * sets.  Defaults to 30000 milliseconds.
     */
    public void setMongodbPollIntervalMs(long mongodbPollIntervalMs) {
        this.mongodbPollIntervalMs = mongodbPollIntervalMs;
    }

    public long getMongodbPollIntervalMs() {
        return mongodbPollIntervalMs;
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
     * Whether field names will be sanitized to Avro naming conventions
     */
    public void setSanitizeFieldNames(boolean sanitizeFieldNames) {
        this.sanitizeFieldNames = sanitizeFieldNames;
    }

    public boolean isSanitizeFieldNames() {
        return sanitizeFieldNames;
    }

    /**
     * Maximum number of failed connection attempts to a replica set primary
     * before an exception occurs and task is aborted. Defaults to 16, which
     * with the defaults for 'connect.backoff.initial.delay.ms' and
     * 'connect.backoff.max.delay.ms' results in just over 20 minutes of
     * attempts before failing.
     */
    public void setConnectMaxAttempts(int connectMaxAttempts) {
        this.connectMaxAttempts = connectMaxAttempts;
    }

    public int getConnectMaxAttempts() {
        return connectMaxAttempts;
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
     * The socket timeout in milliseconds
     */
    public void setMongodbSocketTimeoutMs(int mongodbSocketTimeoutMs) {
        this.mongodbSocketTimeoutMs = mongodbSocketTimeoutMs;
    }

    public int getMongodbSocketTimeoutMs() {
        return mongodbSocketTimeoutMs;
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
     * Unique name that identifies the MongoDB replica set or cluster and all
     * recorded offsets, andthat is used as a prefix for all schemas and topics.
     * Each distinct MongoDB installation should have a separate namespace and
     * monitored by at most one Debezium connector.
     */
    public void setMongodbName(String mongodbName) {
        this.mongodbName = mongodbName;
    }

    public String getMongodbName() {
        return mongodbName;
    }

    /**
     * The maximum delay when trying to reconnect to a primary after a
     * connection cannot be made or when no primary is available. Defaults to
     * 120 second (120,000 ms).
     */
    public void setConnectBackoffMaxDelayMs(long connectBackoffMaxDelayMs) {
        this.connectBackoffMaxDelayMs = connectBackoffMaxDelayMs;
    }

    public long getConnectBackoffMaxDelayMs() {
        return connectBackoffMaxDelayMs;
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
     * A comma-separated list of regular expressions that match the database
     * names for which changes are to be excluded
     */
    public void setDatabaseExcludeList(String databaseExcludeList) {
        this.databaseExcludeList = databaseExcludeList;
    }

    public String getDatabaseExcludeList() {
        return databaseExcludeList;
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
     * as: 'i' for inserts; 'u' for updates; 'd' for deletes. By default, no
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
     * always perform an initial sync when required; 'never' to specify the
     * connector should never perform an initial sync 
     */
    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    /**
     * A comma-separated list of regular expressions that match the database
     * names for which changes are to be captured
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
        
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "collection.include.list", collectionIncludeList);
        addPropertyIfNotNull(configBuilder, "mongodb.password", mongodbPassword);
        addPropertyIfNotNull(configBuilder, "query.fetch.size", queryFetchSize);
        addPropertyIfNotNull(configBuilder, "retriable.restart.connector.wait.ms", retriableRestartConnectorWaitMs);
        addPropertyIfNotNull(configBuilder, "initial.sync.max.threads", initialSyncMaxThreads);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "provide.transaction.metadata", provideTransactionMetadata);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.enabled", mongodbSslEnabled);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "mongodb.members.auto.discover", mongodbMembersAutoDiscover);
        addPropertyIfNotNull(configBuilder, "field.renames", fieldRenames);
        addPropertyIfNotNull(configBuilder, "mongodb.server.selection.timeout.ms", mongodbServerSelectionTimeoutMs);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "mongodb.authsource", mongodbAuthsource);
        addPropertyIfNotNull(configBuilder, "converters", converters);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "mongodb.connect.timeout.ms", mongodbConnectTimeoutMs);
        addPropertyIfNotNull(configBuilder, "mongodb.hosts", mongodbHosts);
        addPropertyIfNotNull(configBuilder, "connect.backoff.initial.delay.ms", connectBackoffInitialDelayMs);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "collection.exclude.list", collectionExcludeList);
        addPropertyIfNotNull(configBuilder, "mongodb.poll.interval.ms", mongodbPollIntervalMs);
        addPropertyIfNotNull(configBuilder, "database.history.file.filename", databaseHistoryFileFilename);
        addPropertyIfNotNull(configBuilder, "sanitize.field.names", sanitizeFieldNames);
        addPropertyIfNotNull(configBuilder, "connect.max.attempts", connectMaxAttempts);
        addPropertyIfNotNull(configBuilder, "mongodb.user", mongodbUser);
        addPropertyIfNotNull(configBuilder, "mongodb.socket.timeout.ms", mongodbSocketTimeoutMs);
        addPropertyIfNotNull(configBuilder, "event.processing.failure.handling.mode", eventProcessingFailureHandlingMode);
        addPropertyIfNotNull(configBuilder, "source.struct.version", sourceStructVersion);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "mongodb.name", mongodbName);
        addPropertyIfNotNull(configBuilder, "connect.backoff.max.delay.ms", connectBackoffMaxDelayMs);
        addPropertyIfNotNull(configBuilder, "field.exclude.list", fieldExcludeList);
        addPropertyIfNotNull(configBuilder, "database.exclude.list", databaseExcludeList);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.invalid.hostname.allowed", mongodbSslInvalidHostnameAllowed);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
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
        if (isFieldValueNotSet(mongodbName)) {
        	return ConfigurationValidation.notValid("Required field 'mongodbName' must be set.");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "mongodb";
    }
}