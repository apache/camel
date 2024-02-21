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
package org.apache.camel.component.microprofile.config;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.function.Predicate;

import io.smallrye.config.SmallRyeConfig;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.annotations.JdkService;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The microprofile-config component is used for bridging the Eclipse MicroProfile Config with Camels properties
 * component. This allows to use configuration management from MicroProfile with Camel.
 */
@JdkService("properties-source-factory")
public class CamelMicroProfilePropertiesSource implements LoadablePropertiesSource {

    private static final Logger LOG = LoggerFactory.getLogger(CamelMicroProfilePropertiesSource.class);
    private List<String> profiles = Collections.emptyList();

    public CamelMicroProfilePropertiesSource() {
        try {
            this.profiles = ConfigProvider.getConfig()
                    .unwrap(SmallRyeConfig.class)
                    .getProfiles();
        } catch (IllegalArgumentException e) {
            // Handle unlikely event that the config could not be unwrapped
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to discover active configuration profiles", e);
            }
        }
    }

    @Override
    public String getName() {
        return "CamelMicroProfilePropertiesSource";
    }

    @Override
    public String getProperty(String name) {
        return ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(null);
    }

    @Override
    public Properties loadProperties() {
        final Properties answer = new Properties();
        final Config config = ConfigProvider.getConfig();
        for (String name : config.getPropertyNames()) {
            try {
                if (isValidForActiveProfiles(name)) {
                    answer.put(name, config.getValue(name, String.class));
                }
            } catch (NoSuchElementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to resolve property {} due to {}", name, e.getMessage());
                }
            }
        }

        return answer;
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        final Properties answer = new Properties();
        final Config config = ConfigProvider.getConfig();

        for (String name : config.getPropertyNames()) {
            if (isValidForActiveProfiles(name) && filter.test(name)) {
                try {
                    config.getOptionalValue(name, String.class).ifPresent(value -> answer.put(name, value));
                } catch (NoSuchElementException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed to resolve property {} due to {}", name, e.getMessage());
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public void reloadProperties(String location) {
        // noop
    }

    @Override
    public String toString() {
        return "camel-microprofile-config";
    }

    private boolean isValidForActiveProfiles(String name) {
        if (!profiles.isEmpty() && name.startsWith("%")) {
            for (String profile : profiles) {
                if (name.startsWith(profile + ".", 1)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
