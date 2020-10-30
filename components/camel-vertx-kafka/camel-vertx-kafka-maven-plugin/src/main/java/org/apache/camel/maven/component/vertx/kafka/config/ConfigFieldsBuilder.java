package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.config.ConfigDef;

public class ConfigFieldsBuilder {

    private static final String[] ILLEGAL_CHARS = { "%", "+", "[", "]", "*", "(", ")", "ˆ", "@", "%", "~" };

    private Map<String, ConfigDef.ConfigKey> configs;
    private Set<String> requiredFields = Collections.emptySet();
    private Set<String> deprecatedFields = Collections.emptySet();
    private Set<String> skippedFields = Collections.emptySet();
    private Map<String, Object> overriddenDefaultValues = Collections.emptyMap();
    private Map<String, String> overriddenVariableNames = Collections.emptyMap();

    public ConfigFieldsBuilder setConfigs(Map<String, ConfigDef.ConfigKey> configs) {
        this.configs = configs;
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

    public ConfigFieldsBuilder setOverriddenDefaultValues(Map<String, Object> overriddenDefaultValues) {
        this.overriddenDefaultValues = overriddenDefaultValues;
        return this;
    }

    public ConfigFieldsBuilder setOverriddenVariableNames(Map<String, String> overriddenVariableNames) {
        this.overriddenVariableNames = overriddenVariableNames;
        return this;
    }

    public Map<String, ConfigField> build() {
        ObjectHelper.notNull(configs, "configs");
        ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
        ObjectHelper.notNull(requiredFields, "requiredFields");
        ObjectHelper.notNull(skippedFields, "skippedFields");
        ObjectHelper.notNull(overriddenDefaultValues, "overriddenDefaultValues");
        ObjectHelper.notNull(overriddenVariableNames, "overriddenVariableNames");

        final Map<String, ConfigField> results = new LinkedHashMap<>();

        configs.forEach((name, configKey) -> {
            // check if name is clean and is not in the skipped list
            if (!StringUtils.containsAny(name, ILLEGAL_CHARS) && !skippedFields.contains(name)) {
                final ConfigField configField = ConfigField.builder()
                        .withFieldDef(configKey)
                        .withOverrideDefaultValue(overriddenDefaultValues.getOrDefault(name, null))
                        .withOverrideVariableName(overriddenVariableNames.getOrDefault(name, null))
                        .isDeprecated(deprecatedFields.contains(name))
                        .isRequired(requiredFields.contains(name))
                        .build();

                results.put(name, configField);
            }
        });

        return results;
    }
}
