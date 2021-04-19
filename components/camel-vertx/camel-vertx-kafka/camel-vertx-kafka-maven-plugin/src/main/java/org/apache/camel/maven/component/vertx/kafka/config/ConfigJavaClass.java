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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tooling.util.srcgen.Annotation;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.text.WordUtils;

public final class ConfigJavaClass {

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
        setApplyAdditionalProperties();
        setAddPropertyIfNotNullMethod();
    }

    private void setPackage() {
        javaClass.setPackage(packageName);
    }

    private void setImports() {
        javaClass.addImport(RuntimeCamelException.class);
        javaClass.addImport(HashMap.class);
        javaClass.addImport(ObjectHelper.class);
    }

    private void setClassNameAndType() {
        if (ObjectHelper.isNotEmpty(parentClass)) {
            javaClass.setName(className)
                    .extendSuperType(parentClass)
                    .implementInterface("Cloneable")
                    .addAnnotation(UriParams.class);
        } else {
            javaClass.setName(className)
                    .implementInterface("Cloneable")
                    .addAnnotation(UriParams.class);
        }
    }

    private void setClassFields() {
        // set common configs first
        setClassFieldsForType(commonConfigs, "common");

        // add additional properties
        setAdditionalAdditionalPropertiesClassField();

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
                        .setComment(fieldName)
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

    private void setAdditionalAdditionalPropertiesClassField() {
        final GenericType type = new GenericType(Map.class, new GenericType(String.class), new GenericType(Object.class));

        final org.apache.camel.tooling.util.srcgen.Field field = javaClass.addField()
                .setName("additionalProperties")
                .setType(type)
                .setComment("Additional properties")
                .setLiteralInitializer("new HashMap<>()")
                .setPrivate();

        field.addAnnotation(UriParam.class)
                .setStringValue("label", "common")
                .setStringValue("prefix", "additionalProperties.")
                .setLiteralValue("multiValue", "true");
    }

    private void setFieldUriParamAnnotation(final ConfigField fieldConfig, final Annotation annotation, final String type) {
        final List<String> labels = new ArrayList<>();
        labels.add(type);
        labels.addAll(fieldConfig.getLabels());

        annotation.setLiteralValue("label", "\"" + String.join(",", labels) + "\"");

        if (ObjectHelper.isNotEmpty(fieldConfig.getDefaultValue())) {
            if (fieldConfig.isTimeField()) {
                annotation.setLiteralValue("defaultValue",
                        String.format("\"%s\"", fieldConfig.getDefaultValueAsTimeString()));
            } else {
                annotation.setLiteralValue("defaultValue", fieldConfig.getDefaultValueWrappedInAsString());
            }
        }

        if (ObjectHelper.isNotEmpty(fieldConfig.getValidEnumsStrings())) {
            annotation.setLiteralValue("enums", "\"" + String.join(",", fieldConfig.getValidEnumsStrings())
                                                + "\"");
        }

        // especial case for Duration field
        if (fieldConfig.isTimeField()) {
            annotation.setLiteralValue("javaType", "\"java.time.Duration\"");
        }
    }

    private void setSettersAndGettersMethodsForType() {
        setSettersAndGettersMethodsForType(commonConfigs);
        setAdditionalPropertiesGetterSetter();

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
                            "Description is not available here, please check Kafka website for corresponding key '%s' description.",
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

    private void setAdditionalPropertiesGetterSetter() {
        final Method method = javaClass.addMethod()
                .setName("setAdditionalProperties")
                .addParameter("Map<String, Object>", "additionalProperties")
                .setPublic()
                .setReturnType(Void.TYPE)
                .setBody(String.format("this.%1$s = %1$s;", "additionalProperties"));

        String description
                = "Sets additional properties for either kafka consumer or kafka producer in case they can't be set directly on the"
                  + " camel configurations (e.g: new Kafka properties that are not reflected yet in Camel configurations), the"
                  + " properties have to be prefixed with `additionalProperties.`. E.g:"
                  + " `additionalProperties.transactional.id=12345&additionalProperties.schema.registry.url=http://localhost:8811/avro`";

        method.getJavaDoc().setFullText(description);

        // getters
        javaClass.addMethod()
                .setName("getAdditionalProperties")
                .setPublic()
                .setReturnType("Map<String, Object>")
                .setBody(String.format("return %s;", "additionalProperties"));
    }

    private void setCreateConfigurationMethods() {
        if (ObjectHelper.isNotEmpty(consumerConfigs) || ObjectHelper.isNotEmpty(commonConfigs)) {
            setCreateConfigurationMethodPerType(consumerConfigs, commonConfigs, "consumer");
        }

        if (ObjectHelper.isNotEmpty(producerConfigs) || ObjectHelper.isNotEmpty(commonConfigs)) {
            setCreateConfigurationMethodPerType(producerConfigs, commonConfigs, "producer");
        }
    }

    private void setCreateConfigurationMethodPerType(
            final Map<String, ConfigField> configs, final Map<String, ConfigField> commonConfigs, final String type) {
        Method createConfig = javaClass.addMethod()
                .setName(String.format("create%sConfiguration", WordUtils.capitalize(type)))
                .setPublic()
                .setReturnType(Properties.class);

        // set config body
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("final Properties props = new Properties();\n");

        // set common configs first
        commonConfigs.forEach((fieldName, fieldConfig) -> setAddPropertyIfNotNullForEveryProperty(stringBuilder, fieldConfig));
        configs.forEach((fieldName, fieldConfig) -> setAddPropertyIfNotNullForEveryProperty(stringBuilder, fieldConfig));

        // add additional property
        setApplyAdditionalPropertiesInCreateConfig(stringBuilder);

        stringBuilder.append("return props;");

        createConfig.setBody(stringBuilder.toString());
    }

    private void setApplyAdditionalPropertiesInCreateConfig(final StringBuilder stringBuilder) {
        stringBuilder.append("applyAdditionalProperties(props, getAdditionalProperties());\n");
    }

    private void setAddPropertyIfNotNullForEveryProperty(final StringBuilder stringBuilder, final ConfigField fieldConfig) {
        if (!isFieldInternalOrDeprecated(fieldConfig)) {
            stringBuilder.append(String.format("addPropertyIfNotNull(props, \"%s\", %s);\n",
                    fieldConfig.getName(), fieldConfig.getVariableName()));
        }
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

    private void setApplyAdditionalProperties() {
        Method method = javaClass.addMethod()
                .setName("applyAdditionalProperties")
                .addParameter(Properties.class, "props")
                .addParameter("Map<String, Object>", "additionalProperties")
                .setReturnType("void")
                .setPrivate();

        final String body = "if (!ObjectHelper.isEmpty(getAdditionalProperties())) {\n"
                            + "\tadditionalProperties.forEach((property, value) -> addPropertyIfNotNull(props, property, value));\n"
                            + "}\n";
        method.setBody(body);
    }

    private boolean isFieldInternalOrDeprecated(final ConfigField field) {
        return field.isInternal() || field.isDeprecated();
    }

    public static final class ConfigJavaClassGeneratorBuilder {
        private String packageName;
        private String className;
        private String parentClassName;
        private Map<String, ConfigField> consumerConfigs = Collections.emptyMap();
        private Map<String, ConfigField> producerConfigs = Collections.emptyMap();

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

        public ConfigJavaClassGeneratorBuilder withConsumerConfigs(final Map<String, ConfigField> consumerConfigs) {
            this.consumerConfigs = consumerConfigs;
            return this;
        }

        public ConfigJavaClassGeneratorBuilder withProducerConfigs(final Map<String, ConfigField> producerConfigs) {
            this.producerConfigs = producerConfigs;
            return this;
        }

        public ConfigJavaClass build() {
            ObjectHelper.notNull(className, "className");
            ObjectHelper.notNull(packageName, "packageName");
            ObjectHelper.notNull(consumerConfigs, "consumerConfigs");
            ObjectHelper.notNull(producerConfigs, "producerConfigs");

            final Map<String, ConfigField> commonConfigsCurated
                    = ConfigUtils.extractCommonFields(consumerConfigs, producerConfigs);
            final Map<String, ConfigField> consumerConfigsCurated
                    = ConfigUtils.extractConsumerOnlyFields(consumerConfigs, producerConfigs);
            final Map<String, ConfigField> producerConfigsCurated
                    = ConfigUtils.extractProducerOnlyFields(consumerConfigs, producerConfigs);

            return new ConfigJavaClass(
                    packageName, className, parentClassName, commonConfigsCurated, consumerConfigsCurated,
                    producerConfigsCurated);
        }
    }
}
