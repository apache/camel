package org.apache.camel.maven.component.vertx.kafka.config;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tooling.util.srcgen.Annotation;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.text.WordUtils;
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

        // generate class
        generateJavaClass();
    }

    public static ConfigJavaClassGeneratorBuilder builder() {
        return new ConfigJavaClassGeneratorBuilder();
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public String getParentClass() {
        return parentClass;
    }

    public String printClassAsString() {
        return javaClass.printClass(true);
    }

    @Override
    public String toString() {
        return printClassAsString();
    }

    private void generateJavaClass() {
        setPackage();
        setImports();
        setClassNameAndType();
        setClassFields();
        setSettersAndGettersMethodsForType();
        setCreateConfigurationMethods();
        setCopyMethod();
        setAddPropertyIfNotNullMethod();
    }

    private void setPackage() {
        javaClass.setPackage(packageName);
    }

    private void setImports() {
        javaClass.addImport(RuntimeCamelException.class);
    }

    private void setClassNameAndType() {
        if (ObjectHelper.isNotEmpty(parentClass)) {
            javaClass.setName(className)
                    .extendSuperType(parentClass)
                    .addAnnotation(UriParams.class);
        } else {
            javaClass.setName(className)
                    .addAnnotation(UriParams.class);
        }
    }

    private void setClassFields() {
        // set common configs first
        setClassFieldsForType(commonConfigs, "common");

        // set consumer configs
        setClassFieldsForType(consumerConfigs, "consumer");

        // set producer configs
        setClassFieldsForType(producerConfigs, "producer");
    }

    private void setClassFieldsForType(final Map<String, ConfigField> configs, final String type) {
        configs.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                final org.apache.camel.tooling.util.srcgen.Field field = javaClass.addField()
                        .setName(fieldConfig.getVariableName())
                        .setType(fieldConfig.getRawType())
                        .setPrivate();

                field.setLiteralInitializer(fieldConfig.getDefaultValueAsAssignableFriendly());

                if (fieldConfig.isUriPathOption()) {
                    setFieldUriParamAnnotation(fieldConfig, field.addAnnotation(UriPath.class), type);
                } else {
                    setFieldUriParamAnnotation(fieldConfig, field.addAnnotation(UriParam.class), type);
                }

                if (fieldConfig.isRequired()) {
                    field.addAnnotation(Metadata.class)
                            .setLiteralValue("required", "true");
                }
            }
        });
    }

    private void setFieldUriParamAnnotation(final ConfigField fieldConfig, final Annotation annotation, final String type) {
        if (fieldConfig.isSecurityType()) {
            annotation.setLiteralValue("label", "\"" + type + ",security\"");
        } else {
            annotation.setLiteralValue("label", "\"" + type + "\"");
        }

        if (ObjectHelper.isNotEmpty(fieldConfig.getDefaultValue())) {
            if (fieldConfig.isTimeField()) {
                annotation.setLiteralValue("defaultValue",
                        String.format("\"%s\"", fieldConfig.getDefaultValueAsTimeString()));
            } else {
                annotation.setLiteralValue("defaultValue", fieldConfig.getDefaultValueWrappedInAsString());
            }
        }

        if (ObjectHelper.isNotEmpty(fieldConfig.getValidStrings())) {
            annotation.setLiteralValue("enums", "\"" + String.join(",", fieldConfig.getValidStrings())
                                                + "\"");
        }

        // especial case for Duration field
        if (fieldConfig.isTimeField()) {
            annotation.setLiteralValue("javaType", "\"java.time.Duration\"");
        }
    }

    private void setSettersAndGettersMethodsForType() {
        setSettersAndGettersMethodsForType(commonConfigs);
        setSettersAndGettersMethodsForType(consumerConfigs);
        setSettersAndGettersMethodsForType(producerConfigs);
    }

    private void setSettersAndGettersMethodsForType(final Map<String, ConfigField> configs) {
        configs.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                // setters with javaDoc
                final Method method = javaClass.addMethod()
                        .setName(fieldConfig.getFieldSetterMethodName())
                        .addParameter(fieldConfig.getRawType(), fieldConfig.getVariableName())
                        .setPublic()
                        .setReturnType(Void.TYPE)
                        .setBody(String.format("this.%1$s = %1$s;", fieldConfig.getVariableName()));

                String description = fieldConfig.getDescription();

                if (description == null || description.isEmpty()) {
                    description = String.format(
                            "Description is not available here, please check Debezium website for corresponding key '%s' description.",
                            fieldName);
                }

                method.getJavaDoc().setFullText(description);

                // getters
                javaClass.addMethod()
                        .setName(fieldConfig.getFieldGetterMethodName())
                        .setPublic()
                        .setReturnType(fieldConfig.getRawType())
                        .setBody(String.format("return %s;", fieldConfig.getVariableName()));
            }
        });
    }

    private void setCreateConfigurationMethods() {
        if (ObjectHelper.isNotEmpty(commonConfigs)) {
            setCreateConfigurationMethodPerType(commonConfigs, "common");
        }

        if (ObjectHelper.isNotEmpty(consumerConfigs)) {
            setCreateConfigurationMethodPerType(consumerConfigs, "consumer");
        }

        if (ObjectHelper.isNotEmpty(producerConfigs)) {
            setCreateConfigurationMethodPerType(producerConfigs, "producer");
        }
    }

    private void setCreateConfigurationMethodPerType(final Map<String, ConfigField> configs, final String type) {
        Method createConfig = javaClass.addMethod()
                .setName(String.format("create%sConfiguration", WordUtils.capitalize(type)))
                .setPublic()
                .setReturnType(Properties.class);

        // set config body
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("final Properties props = new Properties();\n");
        configs.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                stringBuilder.append(String.format("addPropertyIfNotNull(props, \"%s\", %s);\n",
                        fieldConfig.getName(), fieldConfig.getVariableName()));
            }
        });
        stringBuilder.append("return props;");

        createConfig.setBody(stringBuilder.toString());
    }

    private void setCopyMethod() {
        Method method = javaClass.addMethod()
                .setName("copy")
                .setPublic()
                .setReturnType(className);

        final String body = "try {\n"
                            + "\treturn (" + className + ") clone();\n"
                            + "} catch (CloneNotSupportedException e) {\n"
                            + "\tthrow new RuntimeCamelException(e);\n"
                            + "}\n";

        method.setBody(body);
    }

    private void setAddPropertyIfNotNullMethod() {
        Method method = javaClass.addMethod()
                .setName("addPropertyIfNotNull")
                .addParameter(Properties.class, "props")
                .addParameter(String.class, "key")
                .addParameter("T", "value")
                .setStatic()
                .setReturnType("<T> void")
                .setPrivate();

        final String body = "if (value != null) {\n"
                            + "\tprops.put(key, value.toString());\n"
                            + "}\n";
        method.setBody(body);
    }

    private boolean isFieldInternalOrDeprecated(final ConfigField field) {
        return field.isInternal() || field.isDeprecated();
    }

    public static final class ConfigJavaClassGeneratorBuilder {
        private String packageName = null;
        private String className = null;
        private String parentClassName = null;
        private Map<String, ConfigDef.ConfigKey> commonConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> consumerConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> producerConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> additionalCommonConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> additionalConsumerConfigs = Collections.emptyMap();
        private Map<String, ConfigDef.ConfigKey> additionalProducerConfigs = Collections.emptyMap();
        private Set<String> requiredFields = Collections.emptySet();
        private Set<String> deprecatedFields = Collections.emptySet();
        private Set<String> skippedFields = Collections.emptySet();
        private Set<String> uriPathFields = Collections.emptySet();
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

        public ConfigJavaClassGeneratorBuilder withAdditionalCommonConfigs(
                final Map<String, ConfigDef.ConfigKey> commonConfigs) {
            this.additionalCommonConfigs = commonConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withAdditionalConsumerConfigs(
                final Map<String, ConfigDef.ConfigKey> consumerConfigs) {
            this.additionalConsumerConfigs = consumerConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withAdditionalProducerConfigs(
                final Map<String, ConfigDef.ConfigKey> producerConfigs) {
            this.additionalProducerConfigs = producerConfigs;
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

        public ConfigJavaClassGeneratorBuilder withUriPathFields(final Set<String> uriPathFields) {
            this.uriPathFields = uriPathFields;
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
            ObjectHelper.notNull(additionalCommonConfigs, "additionalCommonConfigs");
            ObjectHelper.notNull(additionalConsumerConfigs, "additionalConsumerConfigs");
            ObjectHelper.notNull(additionalProducerConfigs, "additionalProducerConfigs");
            ObjectHelper.notNull(skippedFields, "skippedFields");
            ObjectHelper.notNull(deprecatedFields, "deprecatedFields");
            ObjectHelper.notNull(uriPathFields, "uriPathFields");
            ObjectHelper.notNull(overriddenDefaultValues, "overriddenDefaultValues");
            ObjectHelper.notNull(overriddenVariableNames, "overriddenVariableNames");

            // create our curated configs
            final Map<String, ConfigField> commonConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(commonConfigs)
                    .setAdditionalConfigs(additionalCommonConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .setUriPathFields(uriPathFields)
                    .build();

            // create our curated configs
            final Map<String, ConfigField> producerConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(producerConfigs)
                    .setAdditionalConfigs(additionalProducerConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .setUriPathFields(uriPathFields)
                    .build();

            // create our curated configs
            final Map<String, ConfigField> consumerConfigsCurated = new ConfigFieldsBuilder()
                    .setConfigs(consumerConfigs)
                    .setAdditionalConfigs(additionalConsumerConfigs)
                    .setDeprecatedFields(deprecatedFields)
                    .setRequiredFields(requiredFields)
                    .setSkippedFields(skippedFields)
                    .setOverriddenDefaultValues(overriddenDefaultValues)
                    .setOverriddenVariableNames(overriddenVariableNames)
                    .setUriPathFields(uriPathFields)
                    .build();

            return new ConfigJavaClass(
                    packageName, className, parentClassName, commonConfigsCurated, consumerConfigsCurated,
                    producerConfigsCurated);
        }
    }
}
