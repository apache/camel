package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.config.ConfigDef;

public class ConfigJavaClass {

    private final String packageName;
    private final String className;
    private final String parentClass;
    private final Map<String, ConfigField> commonConfigs;
    private final Map<String, ConfigField> consumerConfigs;
    private final Map<String, ConfigField> producerConfigs;

    private final JavaClass javaClass = new JavaClass(getClass().getClassLoader());

    private ConfigJavaClass(String packageName, String className, String parentClass,
                            Map<String, ConfigField> commonConfigs,
                            Map<String, ConfigField> consumerConfigs,
                            Map<String, ConfigField> producerConfigs) {
        this.packageName = packageName;
        this.className = className;
        this.parentClass = parentClass;
        this.commonConfigs = commonConfigs;
        this.consumerConfigs = consumerConfigs;
        this.producerConfigs = producerConfigs;
    }

    public static ConfigJavaClassGeneratorBuilder builder() {
        return new ConfigJavaClassGeneratorBuilder();
    }

    public static final class ConfigJavaClassGeneratorBuilder {
        private String packageName = null;
        private String className = null;
        private String parentClassName = null;
        private Map<String, ConfigDef.ConfigKey> commonConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> consumerConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> producerConfigs = Collections.emptyMap();
        private Set<String> requiredFields = Collections.emptySet();
        private Set<String> deprecatedFields = Collections.emptySet();
        private Set<String> skippedFields = Collections.emptySet();
        private Map<String, Object> overriddenDefaultValues = Collections.emptyMap();
        private Map<String, String> overriddenVariableNames = Collections.emptyMap();

        public ConfigJavaClassGeneratorBuilder withClassName(final String className) {
            this.className = className;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withPackageName(final String packageName) {
            this.packageName = packageName;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withParentClassName(final String parentClassName) {
            this.parentClassName = parentClassName;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withCommonConfigs(final Map<String, ConfigDef.ConfigKey> commonConfigs) {
            this.commonConfigs = commonConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withConsumerConfigs(final Map<String, ConfigDef.ConfigKey> consumerConfigs) {
            this.consumerConfigs = consumerConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withProducerConfigs(final Map<String, ConfigDef.ConfigKey> producerConfigs) {
            this.producerConfigs = producerConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withRequiredFields(final Set<String> requiredFields) {
            this.requiredFields = requiredFields;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withDeprecatedFields(final Set<String> deprecatedFields) {
            this.deprecatedFields = deprecatedFields;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withSkippedFields(final Set<String> skippedFields) {
            this.skippedFields = skippedFields;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withOverriddenDefaultValues(final Map<String, Object> overriddenDefaultValues) {
            this.overriddenDefaultValues = overriddenDefaultValues;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withOverriddenVariableNames(final Map<String, String> overriddenVariableNames) {
            this.overriddenVariableNames = overriddenVariableNames;
            return this;
        }

        public ConfigJavaClass build() {
            ObjectHelper.notNull(className, "className");
            ObjectHelper.notNull(packageName, "packageName");
            ObjectHelper.notNull(requiredFields, "requiredFields");
            ObjectHelper.notNull(commonConfigs, "commonConfigs");
            ObjectHelper.notNull(consumerConfigs, "consumerConfigs");
            ObjectHelper.notNull(producerConfigs, "producerConfigs");
            ObjectHelper.notNull(skippedFields, "skippedFields");
            ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
            ObjectHelper.notNull(overriddenDefaultValues, "overriddenDefaultValues");
            ObjectHelper.notNull(overriddenVariableNames, "overriddenVariableNames");

            // create our curated configs
            final Map<String, ConfigField> commonConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(consumerConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .build();

            // create our curated configs
            final Map<String, ConfigField> producerConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(producerConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .build();

            // create our curated configs
            final Map<String, ConfigField> consumerConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(consumerConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .build();

            return new ConfigJavaClass(
                    packageName, className, parentClassName, commonConfigsCurated, consumerConfigsCurated,
                    producerConfigsCurated);
        }
    }
}
