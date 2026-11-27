/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.debezium.configuration;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.embedded.async.AsyncEmbeddedEngine;
import io.debezium.engine.spi.OffsetCommitPolicy;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.connect.json.JsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriParams
public abstract class EmbeddedDebeziumConfiguration implements Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedDebeziumConfiguration.class);

    // the properties of the file schema history of Debezium (MySQL, SQL Server, Oracle, Db2)
    private static final String SCHEMA_HISTORY_INTERNAL = "schema.history.internal";
    private static final String SCHEMA_HISTORY_INTERNAL_FILE_FILENAME = "schema.history.internal.file.filename";

    private static final String LABEL_NAME = "consumer";

    private Class<?> connectorClass;
    // name
    @UriPath(label = LABEL_NAME, description = "The name of the connector, which is also the name of the Debezium engine."
                                               + " The offsets are stored by this name: a connector with a new name starts"
                                               + " from the beginning again, as set by its snapshot mode.")
    @Metadata(required = true)
    private String name;
    // offset.storage
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.storage.FileOffsetBackingStore",
              description = "The Java class of the Kafka Connect OffsetBackingStore that stores the offsets (the position"
                            + " of the connector in the database log), so that a restarted consumer continues where it"
                            + " stopped. The default FileOffsetBackingStore writes them to the offsetStorageFileName file,"
                            + " KafkaOffsetBackingStore to the offsetStorageTopic topic, and MemoryOffsetBackingStore keeps"
                            + " them in memory only.")
    private String offsetStorage = DebeziumConstants.DEFAULT_OFFSET_STORAGE;
    // offset.storage.file.filename
    @UriParam(label = LABEL_NAME, description = "The file that the FileOffsetBackingStore writes the offsets to. Required"
                                                + " with the default offsetStorage: creating the endpoint fails when it is"
                                                + " not set, or when its folder does not exist or cannot be written.")
    private String offsetStorageFileName;
    // offset.storage.topic
    @UriParam(label = LABEL_NAME, description = "The Kafka topic that the KafkaOffsetBackingStore writes the offsets to."
                                                + " Required with that offsetStorage, together with offsetStoragePartitions,"
                                                + " offsetStorageReplicationFactor and the Kafka brokers as"
                                                + " additionalProperties.bootstrap.servers.")
    private String offsetStorageTopic;
    // offset.storage.partitions
    @UriParam(label = LABEL_NAME, description = "The number of partitions of the offset topic, when the"
                                                + " KafkaOffsetBackingStore creates it. Required when offsetStorage is the"
                                                + " KafkaOffsetBackingStore.")
    private int offsetStoragePartitions;
    // offset.storage.replication.factor
    @UriParam(label = LABEL_NAME, description = "The replication factor of the offset topic, when the"
                                                + " KafkaOffsetBackingStore creates it. Required when offsetStorage is the"
                                                + " KafkaOffsetBackingStore.")
    private int offsetStorageReplicationFactor;
    // offset.commit.policy
    @UriParam(label = LABEL_NAME,
              description = "The Java class that decides when the offsets are committed. It must implement"
                            + " io.debezium.engine.spi.OffsetCommitPolicy. The default PeriodicCommitOffsetPolicy commits"
                            + " every offsetFlushIntervalMs, and AlwaysCommitOffsetPolicy commits after each batch of"
                            + " events.")
    private String offsetCommitPolicy = OffsetCommitPolicy.PeriodicCommitOffsetPolicy.class.getName();
    // offset.flush.interval.ms
    @UriParam(label = LABEL_NAME, defaultValue = "60000",
              description = "How often the default periodic commit policy commits the offsets. The events processed since"
                            + " the last commit are sent again after a crash. The default is 1 minute.",
              javaType = "java.time.Duration")
    private long offsetFlushIntervalMs = 60000;
    // offset.commit.timeout.ms
    @UriParam(label = LABEL_NAME, defaultValue = "5000",
              description = "How long to wait for the offsets to be written to the offset store before the commit is"
                            + " cancelled; the offsets are then committed in a later attempt. Camel passes it to Debezium as"
                            + " offset.flush.timeout.ms. The default is 5 seconds.",
              javaType = "java.time.Duration")
    private long offsetCommitTimeoutMs = 5000;
    // internal.key.converter
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.json.JsonConverter",
              description = "The converter class that Camel passes to Debezium as internal.key.converter. Debezium does"
                            + " not read this property: it always stores the keys of the offsets as JSON, so this option has"
                            + " no effect.")
    private String internalKeyConverter = JsonConverter.class.getName();
    // internal.value.converter
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.json.JsonConverter",
              description = "The converter class that Camel passes to Debezium as internal.value.converter. Debezium does"
                            + " not read this property: it always stores the values of the offsets as JSON, so this option"
                            + " has no effect.")
    private String internalValueConverter = JsonConverter.class.getName();
    // Additional properties
    @UriParam(label = "common", prefix = "additionalProperties.", multiValue = true,
              description = "Properties for the Debezium engine and connector that have no option of their own, such as"
                            + " the Kafka brokers of the KafkaOffsetBackingStore or single message transformations, for"
                            + " example additionalProperties.bootstrap.servers=localhost:9092.")
    private Map<String, Object> additionalProperties = new HashMap<>();

    protected EmbeddedDebeziumConfiguration() {
        ObjectHelper.notNull(configureConnectorClass(), "connectorClass");
        this.connectorClass = configureConnectorClass();
    }

    public Object copy() {
        try {
            EmbeddedDebeziumConfiguration answer = (EmbeddedDebeziumConfiguration) clone();
            // make sure the map is copied in its own instance
            Map<String, Object> additionalPropertiesCopy = new HashMap<>(additionalProperties);
            answer.setAdditionalProperties(additionalPropertiesCopy);
            return answer;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Configure the Debezium connector class supported by Debezium
     *
     * @return {@link Class}
     */
    protected abstract Class<?> configureConnectorClass();

    /**
     * Create a specific {@link Configuration} for a concrete configuration
     *
     * @return {@link Configuration}
     */
    protected abstract Configuration createConnectorConfiguration();

    /**
     * Validate a concrete configuration
     *
     * @return {@link ConfigurationValidation}
     */
    protected abstract ConfigurationValidation validateConnectorConfiguration();

    /**
     * The Debezium connector type that is supported by Camel Debezium component.
     *
     * @return {@link String}
     */
    public abstract String getConnectorDatabaseType();

    /**
     * Creates a Debezium configuration of type {@link Configuration} in order to be used in the engine.
     *
     * @return {@link Configuration}
     */
    public Configuration createDebeziumConfiguration() {
        final Configuration connectorConfiguration = createConnectorConfiguration();

        ObjectHelper.notNull(connectorConfiguration, "createConnectorConfiguration");

        return Configuration.create().with(createDebeziumEmbeddedEngineConfiguration())
                .with(createConnectorConfiguration()).build();
    }

    private Configuration createDebeziumEmbeddedEngineConfiguration() {
        final Configuration.Builder configBuilder = Configuration.create();

        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.ENGINE_NAME, name);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.CONNECTOR_CLASS, connectorClass.getName());
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_STORAGE, offsetStorage);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_STORAGE_FILE_FILENAME,
                offsetStorageFileName);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_STORAGE_KAFKA_TOPIC, offsetStorageTopic);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_STORAGE_KAFKA_PARTITIONS,
                offsetStoragePartitions);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_STORAGE_KAFKA_REPLICATION_FACTOR,
                offsetStorageReplicationFactor);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_COMMIT_POLICY, offsetCommitPolicy);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_FLUSH_INTERVAL_MS, offsetFlushIntervalMs);
        addPropertyIfNotNull(configBuilder, AsyncEmbeddedEngine.OFFSET_COMMIT_TIMEOUT_MS, offsetCommitTimeoutMs);

        if (internalKeyConverter != null && internalValueConverter != null) {
            configBuilder.with("internal.key.converter", internalKeyConverter);
            configBuilder.with("internal.value.converter", internalValueConverter);
        }

        // additional properties
        applyAdditionalProperties(configBuilder, getAdditionalProperties());

        return configBuilder.build();
    }

    protected static <T> void addPropertyIfNotNull(
            final Configuration.Builder configBuilder,
            final Field field, final T value) {
        if (value != null) {
            configBuilder.with(field, value);
        }
    }

    protected static <T> void addPropertyIfNotNull(
            final Configuration.Builder configBuilder,
            final String key, final T value) {
        if (value != null) {
            configBuilder.with(key, value);
        }
    }

    private void applyAdditionalProperties(
            final Configuration.Builder configBuilder, final Map<String, Object> additionalProperties) {
        if (!ObjectHelper.isEmpty(getAdditionalProperties())) {
            additionalProperties.forEach((property, value) -> addPropertyIfNotNull(configBuilder, property, value));
        }
    }

    /**
     * Validate all configurations defined and return {@link ConfigurationValidation} instance which contains the
     * validation results
     *
     * @return {@link ConfigurationValidation}
     */
    public ConfigurationValidation validateConfiguration() {
        final ConfigurationValidation embeddedEngineValidation = validateDebeziumEmbeddedEngineConfiguration();
        // only if embeddedEngineValidation is true, we check the connector validation
        if (embeddedEngineValidation.isValid()) {
            final ConfigurationValidation connectorValidation = validateConnectorConfiguration();

            ObjectHelper.notNull(connectorValidation, "validateConnectorConfiguration");

            return connectorValidation;
        }
        return embeddedEngineValidation;
    }

    private ConfigurationValidation validateDebeziumEmbeddedEngineConfiguration() {
        if (isFieldValueNotSet(name)) {
            return ConfigurationValidation.notValid("Required field 'name' must be set.");
        }
        // check for offsetStorageFileName
        if (offsetStorage.equals(DebeziumConstants.DEFAULT_OFFSET_STORAGE)
                && isFieldValueNotSet(offsetStorageFileName)) {
            return ConfigurationValidation.notValid(String
                    .format("Required field 'offsetStorageFileName' must be set since 'offsetStorage' is set to '%s'",
                            DebeziumConstants.DEFAULT_OFFSET_STORAGE));
        }
        return validateStorageFiles();
    }

    /**
     * Checks the files that Debezium writes its position and its schema history to, as the engine gets them (the
     * additional properties included), so a consumer whose position cannot be saved fails when it starts instead of
     * logging a WARN on every save and sending everything again, the snapshot included, after a restart.
     * <p>
     * The file offset store of Kafka writes the file in its folder, which must exist. The file schema history of
     * Debezium creates its folders, so only a folder that cannot be created is an error there.
     */
    private ConfigurationValidation validateStorageFiles() {
        final Configuration config;
        try {
            config = createDebeziumConfiguration();
        } catch (RuntimeException e) {
            // an incomplete configuration: the connector validation reports what is missing
            LOG.debug("Cannot check the storage files, as the Debezium configuration cannot be created: {}", e.getMessage(),
                    e);
            return ConfigurationValidation.valid();
        }
        if (DebeziumConstants.DEFAULT_OFFSET_STORAGE.equals(config.getString(AsyncEmbeddedEngine.OFFSET_STORAGE))) {
            String problem = storageFileProblem(config.getString(AsyncEmbeddedEngine.OFFSET_STORAGE_FILE_FILENAME), false);
            if (problem != null) {
                return ConfigurationValidation.notValid(
                        "The file of offsetStorageFileName " + problem + ". Debezium saves its position to this file:"
                                                        + " without it, a restart sends all the changes again.");
            }
        }
        String history = config.getString(SCHEMA_HISTORY_INTERNAL);
        if (history != null && history.endsWith(".FileSchemaHistory")) {
            String file = config.getString(SCHEMA_HISTORY_INTERNAL_FILE_FILENAME);
            if (isFieldValueNotSet(file)) {
                return ConfigurationValidation.notValid(
                        "Required field 'schemaHistoryInternalFileFilename' must be set since 'schemaHistoryInternal'"
                                                        + " is set to '" + history + "'");
            }
            String problem = storageFileProblem(file, true);
            if (problem != null) {
                return ConfigurationValidation.notValid("The file of schemaHistoryInternalFileFilename " + problem);
            }
        }
        return ConfigurationValidation.valid();
    }

    /**
     * Why the file cannot be written, or null when it can.
     *
     * @param file           the file
     * @param createsFolders whether the store creates the folders of the file that do not exist
     */
    static String storageFileProblem(String file, boolean createsFolders) {
        if (isFieldValueNotSet(file)) {
            return null;
        }
        Path path;
        try {
            path = Path.of(file).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return "(" + file + ") is not a valid path";
        }
        if (Files.isDirectory(path)) {
            return "(" + path + ") is a folder, not a file";
        }
        if (Files.exists(path)) {
            return Files.isWritable(path) ? null : "(" + path + ") cannot be written";
        }
        Path folder = path.getParent();
        if (createsFolders) {
            Path existing = folder;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing == null || !Files.isDirectory(existing) || !Files.isWritable(existing)) {
                return "(" + path + ") is in a folder that cannot be created, as "
                       + (existing != null ? existing : folder) + " cannot be written";
            }
            return null;
        }
        if (folder == null || !Files.isDirectory(folder)) {
            return "(" + path + ") is in the folder " + folder + ", which does not exist: create it, or use a file in"
                   + " a folder that exists";
        }
        if (!Files.isWritable(folder)) {
            return "(" + path + ") is in the folder " + folder + ", which cannot be written";
        }
        return null;
    }

    protected static boolean isFieldValueNotSet(final Object field) {
        return ObjectHelper.isEmpty(field);
    }

    /**
     * The name of the Java class for the connector
     */
    public Class<?> getConnectorClass() {
        return connectorClass;
    }

    public void setConnectorClass(Class<?> connectorClass) {
        this.connectorClass = connectorClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOffsetStorage() {
        return offsetStorage;
    }

    public void setOffsetStorage(String offsetStorage) {
        this.offsetStorage = offsetStorage;
    }

    public String getOffsetStorageFileName() {
        return offsetStorageFileName;
    }

    public void setOffsetStorageFileName(String offsetStorageFileName) {
        this.offsetStorageFileName = offsetStorageFileName;
    }

    public String getOffsetStorageTopic() {
        return offsetStorageTopic;
    }

    public void setOffsetStorageTopic(String offsetStorageTopic) {
        this.offsetStorageTopic = offsetStorageTopic;
    }

    public int getOffsetStorageReplicationFactor() {
        return offsetStorageReplicationFactor;
    }

    public void setOffsetStorageReplicationFactor(int offsetStorageReplicationFactor) {
        this.offsetStorageReplicationFactor = offsetStorageReplicationFactor;
    }

    public String getOffsetCommitPolicy() {
        return offsetCommitPolicy;
    }

    public void setOffsetCommitPolicy(String offsetCommitPolicy) {
        this.offsetCommitPolicy = offsetCommitPolicy;
    }

    public long getOffsetFlushIntervalMs() {
        return offsetFlushIntervalMs;
    }

    public void setOffsetFlushIntervalMs(long offsetFlushIntervalMs) {
        this.offsetFlushIntervalMs = offsetFlushIntervalMs;
    }

    public long getOffsetCommitTimeoutMs() {
        return offsetCommitTimeoutMs;
    }

    public void setOffsetCommitTimeoutMs(long offsetCommitTimeoutMs) {
        this.offsetCommitTimeoutMs = offsetCommitTimeoutMs;
    }

    public int getOffsetStoragePartitions() {
        return offsetStoragePartitions;
    }

    public void setOffsetStoragePartitions(int offsetStoragePartitions) {
        this.offsetStoragePartitions = offsetStoragePartitions;
    }

    public String getInternalKeyConverter() {
        return internalKeyConverter;
    }

    public void setInternalKeyConverter(String internalKeyConverter) {
        this.internalKeyConverter = internalKeyConverter;
    }

    public String getInternalValueConverter() {
        return internalValueConverter;
    }

    public void setInternalValueConverter(String internalValueConverter) {
        this.internalValueConverter = internalValueConverter;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
