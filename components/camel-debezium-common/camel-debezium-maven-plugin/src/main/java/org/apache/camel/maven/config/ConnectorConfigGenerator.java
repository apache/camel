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
package org.apache.camel.maven.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.config.Configuration;
import io.debezium.config.Field;
import io.debezium.relational.history.FileDatabaseHistory;
import org.apache.camel.component.debezium.configuration.ConfigurationValidation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.tooling.util.srcgen.Annotation;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.source.SourceConnector;

public final class ConnectorConfigGenerator {

    private static final String PACKAGE_NAME = "org.apache.camel.component.debezium.configuration";
    private static final String PARENT_TYPE = "EmbeddedDebeziumConfiguration";
    private static final String CONNECTOR_SUFFIX = "ConnectorConfig";

    private final SourceConnector connector;
    private final Map<String, ConnectorConfigField> dbzConfigFields;
    private final String connectorName;
    private final String className;

    private final JavaClass javaClass = new JavaClass(getClass().getClassLoader());

    private ConnectorConfigGenerator(final SourceConnector connector, final Map<String, ConnectorConfigField> dbzConfigFields, final String connectorName) {
        this.connector = connector;
        this.dbzConfigFields = dbzConfigFields;
        this.connectorName = connectorName;
        this.className = connectorName + "Connector" + PARENT_TYPE;
        // generate our java class
        generateJavaClass();
    }

    public static ConnectorConfigGenerator create(final SourceConnector connector, final Class<?> dbzConfigClass) {
        return create(connector, dbzConfigClass, Collections.emptySet(), Collections.emptyMap());
    }

    public static ConnectorConfigGenerator create(final SourceConnector connector, final Class<?> dbzConfigClass, final Set<String> requiredFields) {
        return create(connector, dbzConfigClass, requiredFields, Collections.emptyMap());
    }

    public static ConnectorConfigGenerator create(final SourceConnector connector, final Class<?> dbzConfigClass, final Map<String, Object> overridenDefaultValues) {
        return create(connector, dbzConfigClass, Collections.emptySet(), overridenDefaultValues);
    }

    public static ConnectorConfigGenerator create(final SourceConnector connector, final Class<?> dbzConfigClass, final Set<String> requiredFields, final Map<String, Object> overridenDefaultValues) {
        ObjectHelper.notNull(connector, "connector");
        ObjectHelper.notNull(dbzConfigClass, "dbzConfigClass");
        ObjectHelper.notNull(requiredFields, "requiredFields");
        ObjectHelper.notNull(overridenDefaultValues, "overridenDefaultValues");

        // check if config class is correct
        if (!isConfigClassValid(dbzConfigClass)) {
            throw new IllegalArgumentException(String.format("Class '%s' is not valid Debezium configuration class", dbzConfigClass.getName()));
        }

        final ConfigDef configDef = connector.config();
        // add additional fields
        Field.group(configDef, "additionalFields", FileDatabaseHistory.FILE_PATH);
        // get the name of the connector from the configClass
        final String connectorName = dbzConfigClass.getSimpleName().replace(CONNECTOR_SUFFIX, "");

        return new ConnectorConfigGenerator(connector, ConnectorConfigFieldsFactory.createConnectorFieldsAsMap(configDef, dbzConfigClass, requiredFields, overridenDefaultValues), connectorName);
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return PACKAGE_NAME;
    }

    public String printClassAsString() {
        return javaClass.printClass(true);
    }

    @Override
    public String toString() {
        return printClassAsString();
    }

    private static boolean isConfigClassValid(final Class<?> configClass) {
        // config class should be a subtype of CommonConnectorConfig
        Class<?> clazz = configClass;
        while (clazz != null) {
            if (clazz == CommonConnectorConfig.class) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private void generateJavaClass() {
        setPackage();
        setImports();
        setClassNameAndType();
        setClassFields();
        setSettersAndGettersMethods();
        setCreateConnectorConfigurationMethod();
        setConfigureConnectorClassMethod();
        setValidateConnectorConfiguration();
        setGetConnectorDatabaseType();
    }

    private void setPackage() {
        javaClass.setPackage(PACKAGE_NAME);
    }

    private void setImports() {
        javaClass.addImport(Configuration.class);
        javaClass.addImport(connector.getClass());
        javaClass.addImport(Metadata.class);
        javaClass.addImport(UriParam.class);
        javaClass.addImport(UriParams.class);
    }

    private void setClassNameAndType() {
        javaClass.setName(className)
                .extendSuperType(PARENT_TYPE)
                .addAnnotation(UriParams.class);
    }

    private void setClassFields() {
        // String LABEL_NAME
        javaClass.addField()
                .setName("LABEL_NAME")
                .setFinal(true)
                .setStatic(true)
                .setPrivate()
                .setType(String.class)
                .setLiteralInitializer(String.format("\"consumer,%s\"", connectorName.toLowerCase()));

        // connector fields
        dbzConfigFields.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                final org.apache.camel.tooling.util.srcgen.Field field = javaClass.addField()
                        .setName(fieldConfig.getFieldName())
                        .setType(fieldConfig.getRawType())
                        .setPrivate();

                // especial case for database.server.id, we don't set the default value, we let debezium do that
                if (!fieldConfig.getRawName().equals("database.server.id")) {
                    field.setLiteralInitializer(fieldConfig.getDefaultValueAsString());
                }

                final Annotation annotation = field.addAnnotation(UriParam.class)
                        .setLiteralValue("label", "LABEL_NAME");

                // especial case for database.server.id, we don't set the default value, we let debezium do that
                if (fieldConfig.getDefaultValue() != null && !fieldConfig.getRawName().equals("database.server.id")) {
                    annotation.setLiteralValue("defaultValue", String.format("\"%s\"", fieldConfig.getDefaultValue()));
                }

                if (fieldConfig.isRequired()) {
                    field.addAnnotation(Metadata.class)
                            .setLiteralValue("required", "true");
                }
            }
        });
    }

    private void setSettersAndGettersMethods() {
        dbzConfigFields.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                // setters with javaDoc
                final Method method = javaClass.addMethod()
                        .setName(fieldConfig.getFieldSetterMethodName())
                        .addParameter(fieldConfig.getRawType(), fieldConfig.getFieldName())
                        .setPublic()
                        .setReturnType(Void.TYPE)
                        .setBody(String.format("this.%1$s = %1$s;", fieldConfig.getFieldName()));

                String description = fieldConfig.getDescription();

                if (description == null || description.isEmpty()) {
                    description = String.format("Description is not available here, please check Debezium website for corresponding key '%s' description.", fieldName);
                }

                method.getJavaDoc().setFullText(description);

                // getters
                javaClass.addMethod()
                        .setName(fieldConfig.getFieldGetterMethodName())
                        .setPublic()
                        .setReturnType(fieldConfig.getRawType())
                        .setBody(String.format("return %s;", fieldConfig.getFieldName()));
            }
        });
    }

    private void setCreateConnectorConfigurationMethod() {
        Method createConfig = javaClass.addMethod()
                .setName("createConnectorConfiguration")
                .setProtected()
                .setReturnType(Configuration.class);

        createConfig.addAnnotation(Override.class);

        // set config body
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("final Configuration.Builder configBuilder = Configuration.create();\n\n");
        dbzConfigFields.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig)) {
                stringBuilder.append(String.format("addPropertyIfNotNull(configBuilder, \"%s\", %s);\n", fieldConfig.getRawName(), fieldConfig.getFieldName()));
            }
        });
        stringBuilder.append("\n");
        stringBuilder.append("return configBuilder.build();");

        createConfig.setBody(stringBuilder.toString());
    }

    private void setConfigureConnectorClassMethod() {
        javaClass.addMethod()
                .setName("configureConnectorClass")
                .setProtected()
                .setReturnType(Class.class)
                .setBody(String.format("return %s.class;", connector.getClass().getSimpleName()))
                .addAnnotation(Override.class);
    }

    private void setValidateConnectorConfiguration() {
        // validate config
        Method validateConfig = javaClass.addMethod()
                .setName("validateConnectorConfiguration")
                .setReturnType(ConfigurationValidation.class)
                .setProtected();

        // set validate body
        final StringBuilder stringBuilder = new StringBuilder();
        dbzConfigFields.forEach((fieldName, fieldConfig) -> {
            if (!isFieldInternalOrDeprecated(fieldConfig) && fieldConfig.isRequired()) {
                stringBuilder.append(String.format("if (isFieldValueNotSet(%s)) {\n", fieldConfig.getFieldName()));
                stringBuilder.append(String.format("\treturn ConfigurationValidation.notValid(\"Required field '%s' must be set.\");\n}\n", fieldConfig.getFieldName()));
            }
        });

        stringBuilder.append("return ConfigurationValidation.valid();");

        validateConfig.setBody(stringBuilder.toString())
                .addAnnotation(Override.class);
    }

    private void setGetConnectorDatabaseType() {
        javaClass.addMethod()
                .setName("getConnectorDatabaseType")
                .setPublic()
                .setReturnType(String.class)
                .setBody(String.format("return \"%s\";", getConnectorName()).toLowerCase())
                .addAnnotation(Override.class);
    }

    private boolean isFieldInternalOrDeprecated(final ConnectorConfigField field) {
        return field.isInternal() || field.isDeprecated();
    }
}
