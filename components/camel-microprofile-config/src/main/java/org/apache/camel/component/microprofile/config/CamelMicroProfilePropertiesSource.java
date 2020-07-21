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

import java.util.Properties;
import java.util.function.Predicate;

import org.apache.camel.spi.LoadablePropertiesSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * The microprofile-config component is used for bridging the Eclipse MicroProfile Config with Camels
 * properties component. This allows to use configuration management from MicroProfile with Camel.
 */
public class CamelMicroProfilePropertiesSource implements LoadablePropertiesSource {

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

        for (String key: config.getPropertyNames()) {
            answer.put(key, config.getValue(key, String.class));
        }

        return answer;
    }

    @Override
    public Properties loadProperties(Predicate<String> filter) {
        final Properties answer = new Properties();
        final Config config = ConfigProvider.getConfig();

        for (String name: config.getPropertyNames()) {
            if (filter.test(name)) {
                config.getOptionalValue(name, String.class).ifPresent(v -> answer.put(name, v));
            }
        }

        return answer;
    }
}
