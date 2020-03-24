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
    @Metadata(required = true)
    private String mongodbPassword;
    @UriParam(label = LABEL_NAME, defaultValue = "1")
    private int initialSyncMaxThreads = 1;
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private long snapshotDelayMs = 0;
    @UriParam(label = LABEL_NAME)
    private String collectionBlacklist;
    @UriParam(label = LABEL_NAME)
    private String collectionWhitelist;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslEnabled = false;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean tombstonesOnDelete = false;
    @UriParam(label = LABEL_NAME, defaultValue = "true")
    private boolean mongodbMembersAutoDiscover = true;
    @UriParam(label = LABEL_NAME)
    private String fieldRenames;
    @UriParam(label = LABEL_NAME, defaultValue = "500")
    private long pollIntervalMs = 500;
    @UriParam(label = LABEL_NAME, defaultValue = "__debezium-heartbeat")
    private String heartbeatTopicsPrefix = "__debezium-heartbeat";
    @UriParam(label = LABEL_NAME)
    private String databaseWhitelist;
    @UriParam(label = LABEL_NAME)
    private String mongodbHosts;
    @UriParam(label = LABEL_NAME, defaultValue = "1000")
    private long connectBackoffInitialDelayMs = 1000;
    @UriParam(label = LABEL_NAME)
    private int snapshotFetchSize;
    @UriParam(label = LABEL_NAME)
    private String databaseHistoryFileFilename;
    @UriParam(label = LABEL_NAME, defaultValue = "16")
    private int connectMaxAttempts = 16;
    @UriParam(label = LABEL_NAME)
    private String mongodbUser;
    @UriParam(label = LABEL_NAME)
    private String fieldBlacklist;
    @UriParam(label = LABEL_NAME, defaultValue = "v2")
    private String sourceStructVersion = "v2";
    @UriParam(label = LABEL_NAME, defaultValue = "0")
    private int heartbeatIntervalMs = 0;
    @UriParam(label = LABEL_NAME)
    @Metadata(required = true)
    private String mongodbName;
    @UriParam(label = LABEL_NAME, defaultValue = "120000")
    private long connectBackoffMaxDelayMs = 120000;
    @UriParam(label = LABEL_NAME, defaultValue = "false")
    private boolean mongodbSslInvalidHostnameAllowed = false;
    @UriParam(label = LABEL_NAME)
    private String databaseBlacklist;
    @UriParam(label = LABEL_NAME)
    private String skippedOperations;
    @UriParam(label = LABEL_NAME, defaultValue = "2048")
    private int maxBatchSize = 2048;
    @UriParam(label = LABEL_NAME, defaultValue = "initial")
    private String snapshotMode = "initial";

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
     * Password to be used when connecting to MongoDB, if necessary.
     */
    public void setMongodbPassword(String mongodbPassword) {
        this.mongodbPassword = mongodbPassword;
    }

    public String getMongodbPassword() {
        return mongodbPassword;
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
     * Description is not available here, please check Debezium website for
     * corresponding key 'collection.blacklist' description.
     */
    public void setCollectionBlacklist(String collectionBlacklist) {
        this.collectionBlacklist = collectionBlacklist;
    }

    public String getCollectionBlacklist() {
        return collectionBlacklist;
    }

    /**
     * The collections for which changes are to be captured
     */
    public void setCollectionWhitelist(String collectionWhitelist) {
        this.collectionWhitelist = collectionWhitelist;
    }

    public String getCollectionWhitelist() {
        return collectionWhitelist;
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
     * The databases for which changes are to be captured
     */
    public void setDatabaseWhitelist(String databaseWhitelist) {
        this.databaseWhitelist = databaseWhitelist;
    }

    public String getDatabaseWhitelist() {
        return databaseWhitelist;
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
     * Description is not available here, please check Debezium website for
     * corresponding key 'field.blacklist' description.
     */
    public void setFieldBlacklist(String fieldBlacklist) {
        this.fieldBlacklist = fieldBlacklist;
    }

    public String getFieldBlacklist() {
        return fieldBlacklist;
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
     * The databases for which changes are to be excluded
     */
    public void setDatabaseBlacklist(String databaseBlacklist) {
        this.databaseBlacklist = databaseBlacklist;
    }

    public String getDatabaseBlacklist() {
        return databaseBlacklist;
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

    @Override
    protected Configuration createConnectorConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();
        
        addPropertyIfNotNull(configBuilder, "max.queue.size", maxQueueSize);
        addPropertyIfNotNull(configBuilder, "mongodb.password", mongodbPassword);
        addPropertyIfNotNull(configBuilder, "initial.sync.max.threads", initialSyncMaxThreads);
        addPropertyIfNotNull(configBuilder, "snapshot.delay.ms", snapshotDelayMs);
        addPropertyIfNotNull(configBuilder, "collection.blacklist", collectionBlacklist);
        addPropertyIfNotNull(configBuilder, "collection.whitelist", collectionWhitelist);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.enabled", mongodbSslEnabled);
        addPropertyIfNotNull(configBuilder, "tombstones.on.delete", tombstonesOnDelete);
        addPropertyIfNotNull(configBuilder, "mongodb.members.auto.discover", mongodbMembersAutoDiscover);
        addPropertyIfNotNull(configBuilder, "field.renames", fieldRenames);
        addPropertyIfNotNull(configBuilder, "poll.interval.ms", pollIntervalMs);
        addPropertyIfNotNull(configBuilder, "heartbeat.topics.prefix", heartbeatTopicsPrefix);
        addPropertyIfNotNull(configBuilder, "database.whitelist", databaseWhitelist);
        addPropertyIfNotNull(configBuilder, "mongodb.hosts", mongodbHosts);
        addPropertyIfNotNull(configBuilder, "connect.backoff.initial.delay.ms", connectBackoffInitialDelayMs);
        addPropertyIfNotNull(configBuilder, "snapshot.fetch.size", snapshotFetchSize);
        addPropertyIfNotNull(configBuilder, "database.history.file.filename", databaseHistoryFileFilename);
        addPropertyIfNotNull(configBuilder, "connect.max.attempts", connectMaxAttempts);
        addPropertyIfNotNull(configBuilder, "mongodb.user", mongodbUser);
        addPropertyIfNotNull(configBuilder, "field.blacklist", fieldBlacklist);
        addPropertyIfNotNull(configBuilder, "source.struct.version", sourceStructVersion);
        addPropertyIfNotNull(configBuilder, "heartbeat.interval.ms", heartbeatIntervalMs);
        addPropertyIfNotNull(configBuilder, "mongodb.name", mongodbName);
        addPropertyIfNotNull(configBuilder, "connect.backoff.max.delay.ms", connectBackoffMaxDelayMs);
        addPropertyIfNotNull(configBuilder, "mongodb.ssl.invalid.hostname.allowed", mongodbSslInvalidHostnameAllowed);
        addPropertyIfNotNull(configBuilder, "database.blacklist", databaseBlacklist);
        addPropertyIfNotNull(configBuilder, "skipped.operations", skippedOperations);
        addPropertyIfNotNull(configBuilder, "max.batch.size", maxBatchSize);
        addPropertyIfNotNull(configBuilder, "snapshot.mode", snapshotMode);
        
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