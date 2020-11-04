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
package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.config.ConfigDef;

public final class ConfigFieldsBuilder {

    private static final String[] ILLEGAL_CHARS = { "%", "+", "[", "]", "*", "(", ")", "ˆ", "@", "%", "~" };

    private final Map<String, ConfigField> configs = new LinkedHashMap<>();
    private Map<String, ConfigDef.ConfigKey> configKeys = Collections.emptyMap();
    private Set<String> requiredFields = Collections.emptySet();
    private Set<String> deprecatedFields = Collections.emptySet();
    private Set<String> skippedFields = Collections.emptySet();
    private Set<String> uriPathFields = Collections.emptySet();
    private Map<String, Object> overriddenDefaultValues = Collections.emptyMap();
    private Map<String, String> overriddenVariableNames = Collections.emptyMap();

    public ConfigFieldsBuilder fromConfigKeys(Map<String, ConfigDef.ConfigKey> configKeys) {
        this.configKeys = configKeys;
        return this;
    }

    public ConfigFieldsBuilder setRequiredFields(Set<String> requiredFields) {
        this.requiredFields = requiredFields;
        return this;
    }

    public ConfigFieldsBuilder setDeprecatedFields(Set<String> deprecatedFields) {
        this.deprecatedFields = deprecatedFields;
        return this;
    }

    public ConfigFieldsBuilder setSkippedFields(Set<String> skippedFields) {
        this.skippedFields = skippedFields;
        return this;
    }

    public ConfigFieldsBuilder setUriPathFields(Set<String> uriPathFields) {
        this.uriPathFields = uriPathFields;
        return this;
    }

    public ConfigFieldsBuilder setOverriddenDefaultValues(Map<String, Object> overriddenDefaultValues) {
        this.overriddenDefaultValues = overriddenDefaultValues;
        return this;
    }

    public ConfigFieldsBuilder setOverriddenVariableNames(Map<String, String> overriddenVariableNames) {
        this.overriddenVariableNames = overriddenVariableNames;
        return this;
    }

    public ConfigFieldsBuilder addConfig(ConfigField config) {
        configs.put(config.getName(), config);
        return this;
    }

    public ConfigFieldsBuilder addConfig(Map<String, ConfigField> configs) {
        this.configs.putAll(configs);
        return this;
    }

    public Map<String, ConfigField> build() {
        ObjectHelper.notNull(configKeys, "configs");
        ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
        ObjectHelper.notNull(requiredFields, "requiredFields");
        ObjectHelper.notNull(skippedFields, "skippedFields");
        ObjectHelper.notNull(uriPathFields, "uriPathFields");
        ObjectHelper.notNull(overriddenDefaultValues, "overriddenDefaultValues");
        ObjectHelper.notNull(overriddenVariableNames, "overriddenVariableNames");

        configKeys.forEach((name, configKey) -> {
            // check if name is clean and is not in the skipped list
            if (!StringUtils.containsAny(name, ILLEGAL_CHARS) && !skippedFields.contains(name)) {
                final ConfigField configField = ConfigField
                        .fromConfigKey(configKey)
                        .withDefaultValue(overriddenDefaultValues.getOrDefault(name, configKey.defaultValue))
                        .withVariableName(overriddenVariableNames.getOrDefault(name, null))
                        .isDeprecated(deprecatedFields.contains(name))
                        .isRequired(requiredFields.contains(name))
                        .isUriPathOption(uriPathFields.contains(name))
                        .build();

                configs.put(name, configField);
            }
        });

        return configs;
    }
}
