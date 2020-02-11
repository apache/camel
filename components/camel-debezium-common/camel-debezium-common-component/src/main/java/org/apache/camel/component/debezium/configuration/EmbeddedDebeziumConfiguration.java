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

import java.util.HashMap;
import java.util.Map;

import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.embedded.EmbeddedEngine;
import io.debezium.embedded.spi.OffsetCommitPolicy;
import org.apache.camel.component.debezium.DebeziumConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.connect.json.JsonConverter;

@UriParams
public abstract class EmbeddedDebeziumConfiguration {

    private static final String LABEL_NAME = "consumer";

    private Class<?> connectorClass;
    // name
    @UriPath(label = LABEL_NAME, description = "Unique name for the connector. "
            + "Attempting to register again with the same name will fail.")
    @Metadata(required = true)
    private String name;
    // offset.storage
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.storage.FileOffsetBackingStore",
            description = "The name of the Java class that is responsible for persistence of connector offsets.")
    private String offsetStorage = DebeziumConstants.DEFAULT_OFFSET_STORAGE;
    // offset.storage.file.filename
    @UriParam(label = LABEL_NAME, description = "Path to file where offsets are to be stored. "
            + "Required when offset.storage is set to the FileOffsetBackingStore.")
    private String offsetStorageFileName;
    // offset.storage.topic
    @UriParam(label = LABEL_NAME, description = "The name of the Kafka topic where offsets are "
            + "to be stored. Required when offset.storage is set to the KafkaOffsetBackingStore.")
    private String offsetStorageTopic;
    // offset.storage.partitions
    @UriParam(label = LABEL_NAME, description = "The number of partitions used when creating the "
            + "offset storage topic. Required when offset.storage is set to the 'KafkaOffsetBackingStore'.")
    private int offsetStoragePartitions;
    // offset.storage.replication.factor
    @UriParam(label = LABEL_NAME, description = "Replication factor used when creating the offset "
            + "storage topic. Required when offset.storage is set to the KafkaOffsetBackingStore")
    private int offsetStorageReplicationFactor;
    // offset.commit.policy
    @UriParam(label = LABEL_NAME, defaultValue = "io.debezium.embedded.spi.OffsetCommitPolicy.PeriodicCommitOffsetPolicy",
            description = "The name of the Java class of the commit policy. It defines when offsets "
                    + "commit has to be triggered based on the number of events processed and the "
                    + "time elapsed since the last commit. This class must implement the interface "
                    + "'OffsetCommitPolicy'. The default is a periodic commit policy based upon "
                    + "time intervals.")
    private String offsetCommitPolicy = OffsetCommitPolicy.PeriodicCommitOffsetPolicy.class.getName();
    // offset.flush.interval.ms
    @UriParam(label = LABEL_NAME, defaultValue = "60000", description = "Interval at which to try committing "
            + "offsets. The default is 1 minute.")
    private long offsetFlushIntervalMs = 60000;
    // offset.commit.timeout.ms
    @UriParam(label = LABEL_NAME, defaultValue = "5000", description = "Maximum number of milliseconds "
            + "to wait for records to flush and partition offset data to be committed to offset storage "
            + "before cancelling the process and restoring the offset data to be committed in a future "
            + "attempt. The default is 5 seconds.")
    private long offsetCommitTimeoutMs = 5000;
    // internal.key.converter
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.json.JsonConverter",
            description = "The Converter class that should be used to serialize and deserialize key data "
                    + "for offsets. The default is JSON converter.")
    private String internalKeyConverter = JsonConverter.class.getName();
    // internal.value.converter
    @UriParam(label = LABEL_NAME, defaultValue = "org.apache.kafka.connect.json.JsonConverter",
            description = "The Converter class that should be used to serialize and deserialize value "
                    + "data for offsets. The default is JSON converter.")
    private String internalValueConverter = JsonConverter.class.getName();
    // Additional properties
    @UriParam(label = "common", prefix = "additionalProperties.", multiValue = true,
            description = "Additional properties for debezium components in case they can't be set directly "
                    + "on the camel configurations (e.g: setting Kafka Connect properties needed by Debezium engine, "
                    + "for example setting KafkaOffsetBackingStore), the properties have to be prefixed with "
                    + "`additionalProperties.`. E.g: `additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro`")
    private Map<String, Object> additionalProperties = new HashMap<>();

    public EmbeddedDebeziumConfiguration() {
        ObjectHelper.notNull(configureConnectorClass(), "connectorClass");
        this.connectorClass = configureConnectorClass();
    }

    /**
     * Configure the Debezium connector class that is supported by Debezium
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
     * Creates a Debezium configuration of type {@link Configuration} in order to be
     * used in the engine.
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

        addPropertyIfNotNull(configBuilder, EmbeddedEngine.ENGINE_NAME, name);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.CONNECTOR_CLASS, connectorClass.getName());
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_STORAGE, offsetStorage);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_STORAGE_FILE_FILENAME,
                             offsetStorageFileName);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_STORAGE_KAFKA_TOPIC, offsetStorageTopic);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_STORAGE_KAFKA_PARTITIONS,
                             offsetStoragePartitions);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_STORAGE_KAFKA_REPLICATION_FACTOR,
                             offsetStorageReplicationFactor);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_COMMIT_POLICY, offsetCommitPolicy);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_FLUSH_INTERVAL_MS, offsetFlushIntervalMs);
        addPropertyIfNotNull(configBuilder, EmbeddedEngine.OFFSET_COMMIT_TIMEOUT_MS, offsetCommitTimeoutMs);

        if (internalKeyConverter != null && internalValueConverter != null) {
            configBuilder.with("internal.key.converter", internalKeyConverter);
            configBuilder.with("internal.value.converter", internalValueConverter);
        }

        // additional properties
        applyAdditionalProperties(configBuilder, getAdditionalProperties());

        return configBuilder.build();
    }

    protected static <T> void addPropertyIfNotNull(final Configuration.Builder configBuilder,
                                                   final Field field, final T value) {
        if (value != null) {
            configBuilder.with(field, value);
        }
    }

    protected static <T> void addPropertyIfNotNull(final Configuration.Builder configBuilder,
                                                   final String key, final T value) {
        if (value != null) {
            configBuilder.with(key, value);
        }
    }

    private void applyAdditionalProperties(final Configuration.Builder configBuilder, final Map<String, Object> additionalProperties) {
        if (!ObjectHelper.isEmpty(getAdditionalProperties())) {
            additionalProperties.forEach((property, value) -> addPropertyIfNotNull(configBuilder, property, value));
        }
    }

    /**
     * Validate all configurations defined and return
     * {@link ConfigurationValidation} instance which contains the validation
     * results
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
        return ConfigurationValidation.valid();
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

    /**
     * Unique name for the connector. Attempting to register again with the same
     * name will fail.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The name of the Java class that is responsible for persistence of connector
     * offsets.
     */
    public String getOffsetStorage() {
        return offsetStorage;
    }

    public void setOffsetStorage(String offsetStorage) {
        this.offsetStorage = offsetStorage;
    }

    /**
     * Path to file where offsets are to be stored. Required when offset.storage is
     * set to the FileOffsetBackingStore
     */
    public String getOffsetStorageFileName() {
        return offsetStorageFileName;
    }

    public void setOffsetStorageFileName(String offsetStorageFileName) {
        this.offsetStorageFileName = offsetStorageFileName;
    }

    /**
     * The name of the Kafka topic where offsets are to be stored. Required when
     * offset.storage is set to the KafkaOffsetBackingStore.
     */
    public String getOffsetStorageTopic() {
        return offsetStorageTopic;
    }

    public void setOffsetStorageTopic(String offsetStorageTopic) {
        this.offsetStorageTopic = offsetStorageTopic;
    }

    /**
     * Replication factor used when creating the offset storage topic. Required when
     * offset.storage is set to the KafkaOffsetBackingStore
     */
    public int getOffsetStorageReplicationFactor() {
        return offsetStorageReplicationFactor;
    }

    public void setOffsetStorageReplicationFactor(int offsetStorageReplicationFactor) {
        this.offsetStorageReplicationFactor = offsetStorageReplicationFactor;
    }

    /**
     * The name of the Java class of the commit policy. It defines when offsets
     * commit has to be triggered based on the number of events processed and the
     * time elapsed since the last commit. This class must implement the interface
     * 'OffsetCommitPolicy'. The default is a periodic commit policy based upon
     * time intervals.
     */
    public String getOffsetCommitPolicy() {
        return offsetCommitPolicy;
    }

    public void setOffsetCommitPolicy(String offsetCommitPolicy) {
        this.offsetCommitPolicy = offsetCommitPolicy;
    }

    /**
     * Interval at which to try committing offsets. The default is 1 minute.
     */
    public long getOffsetFlushIntervalMs() {
        return offsetFlushIntervalMs;
    }

    public void setOffsetFlushIntervalMs(long offsetFlushIntervalMs) {
        this.offsetFlushIntervalMs = offsetFlushIntervalMs;
    }

    /**
     * Maximum number of milliseconds to wait for records to flush and partition
     * offset data to be committed to offset storage before cancelling the process
     * and restoring the offset data to be committed in a future attempt. The
     * default is 5 seconds.
     */
    public long getOffsetCommitTimeoutMs() {
        return offsetCommitTimeoutMs;
    }

    public void setOffsetCommitTimeoutMs(long offsetCommitTimeoutMs) {
        this.offsetCommitTimeoutMs = offsetCommitTimeoutMs;
    }

    /**
     * The number of partitions used when creating the offset storage topic.
     * Required when offset.storage is set to the 'KafkaOffsetBackingStore'.
     */
    public int getOffsetStoragePartitions() {
        return offsetStoragePartitions;
    }

    public void setOffsetStoragePartitions(int offsetStoragePartitions) {
        this.offsetStoragePartitions = offsetStoragePartitions;
    }

    /**
     * The Converter class that should be used to serialize and deserialize key data
     * for offsets. The default is JSON converter.
     */
    public String getInternalKeyConverter() {
        return internalKeyConverter;
    }

    public void setInternalKeyConverter(String internalKeyConverter) {
        this.internalKeyConverter = internalKeyConverter;
    }

    /**
     * The Converter class that should be used to serialize and deserialize value
     * data for offsets. The default is JSON converter.
     */
    public String getInternalValueConverter() {
        return internalValueConverter;
    }

    public void setInternalValueConverter(String internalValueConverter) {
        this.internalValueConverter = internalValueConverter;
    }

    /**
     * Sets additional properties for debezium components in case they can't be set directly on the camel configurations
     * (e.g: setting Kafka Connect properties needed by Debezium engine, for example setting KafkaOffsetBackingStore), the properties have to be prefixed with
     * `additionalProperties.`. E.g: `additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro`
     */
    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
