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
package org.apache.camel.component.torchserve.client;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Configuration {

    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public static final String TSC4J_PROPERTIES = "tsc4j.properties";
    public static final String TSC4J_PREFIX = "tsc4j.";

    public static final String INFERENCE_KEY = "inference.key";
    public static final String INFERENCE_ADDRESS = "inference.address";
    public static final String INFERENCE_PORT = "inference.port";

    public static final String MANAGEMENT_KEY = "management.key";
    public static final String MANAGEMENT_ADDRESS = "management.address";
    public static final String MANAGEMENT_PORT = "management.port";

    public static final String METRICS_ADDRESS = "metrics.address";
    public static final String METRICS_PORT = "metrics.port";

    private final Optional<String> inferenceKey;
    private final Optional<String> inferenceAddress;
    private final Optional<Integer> inferencePort;

    private final Optional<String> managementKey;
    private final Optional<String> managementAddress;
    private final Optional<Integer> managementPort;

    private final Optional<String> metricsAddress;
    private final Optional<Integer> metricsPort;

    private Configuration() {
        Properties props = loadProperties();

        this.inferenceKey = loadProperty(INFERENCE_KEY, props);
        this.inferenceAddress = loadProperty(INFERENCE_ADDRESS, props);
        this.inferencePort = loadProperty(INFERENCE_PORT, props).map(Integer::parseInt);

        this.managementKey = loadProperty(MANAGEMENT_KEY, props);
        this.managementAddress = loadProperty(MANAGEMENT_ADDRESS, props);
        this.managementPort = loadProperty(MANAGEMENT_PORT, props).map(Integer::parseInt);

        this.metricsAddress = loadProperty(METRICS_ADDRESS, props);
        this.metricsPort = loadProperty(METRICS_PORT, props).map(Integer::parseInt);
    }

    static Properties loadProperties() {
        Properties properties = new Properties();
        try {
            InputStream is = Configuration.class.getClassLoader().getResourceAsStream(TSC4J_PROPERTIES);
            properties.load(is);
        } catch (Exception e) {
            // Ignore
            LOG.debug("Failed to load properties file: {}", e.getMessage());
        }
        return properties;
    }

    /**
     * Order of precedence: System properties > environment variables > properties file
     */
    static Optional<String> loadProperty(String key, Properties properties) {
        String tsc4jKey = TSC4J_PREFIX + key;
        Optional<String> value = Optional.ofNullable(System.getProperty(tsc4jKey))
                .or(() -> Optional.ofNullable(System.getenv(tsc4jKey.toUpperCase().replace(".", "_"))))
                .or(() -> Optional.ofNullable(properties.getProperty(key)));
        LOG.debug("Loaded property {}: {}", key, value.orElse(null));
        return value;
    }

    public static Configuration load() {
        return new Configuration();
    }

    public Optional<String> getInferenceKey() {
        return inferenceKey;
    }

    public Optional<String> getInferenceAddress() {
        return inferenceAddress;
    }

    public Optional<Integer> getInferencePort() {
        return inferencePort;
    }

    public Optional<String> getManagementKey() {
        return managementKey;
    }

    public Optional<String> getManagementAddress() {
        return managementAddress;
    }

    public Optional<Integer> getManagementPort() {
        return managementPort;
    }

    public Optional<String> getMetricsAddress() {
        return metricsAddress;
    }

    public Optional<Integer> getMetricsPort() {
        return metricsPort;
    }
}
