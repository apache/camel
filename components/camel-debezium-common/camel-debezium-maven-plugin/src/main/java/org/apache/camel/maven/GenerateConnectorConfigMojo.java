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
package org.apache.camel.maven;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.maven.config.ConnectorConfigGenerator;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generate-connector-config", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateConnectorConfigMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/connector-configurations")
    private File generatedSrcDir;

    /**
     * Debezium connector's class name, this has to be fully name with the package, e.g:
     * 'io.debezium.connector.mysql.MySqlConnector'
     */
    @Parameter(
            property = "camel.debezium.connector.class",
            required = true
    )
    private String connectorClassName;

    /**
     * Debezium connector's config class name, this has to be fully name with the package, e.g:
     * 'io.debezium.connector.mysql.MySqlConnectorConfig'
     */
    @Parameter(
            property = "camel.debezium.connector.config.class",
            required = true
    )
    private String connectorConfigClassName;

    /**
     * Fields to override their default value
     */
    @Parameter(property = "camel.debezium.fields")
    private Map<String, Object> fields = Collections.emptyMap();

    /**
     * Fields that are required
     */
    @Parameter(property = "camel.debezium.required.fields")
    private List<String> requiredFields = Collections.emptyList();


    @Override
    public void execute() throws MojoFailureException {
        final Set<String> requiredFields = getRequiredFields();
        final Map<String, Object> overrideFields = getFields();
        final Class<?> configClazz = ObjectHelper.loadClass(connectorConfigClassName);

        if (configClazz == null) {
            throw new MojoFailureException("connectorConfigClassName not found.");
        }

        try {
            final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(getConnector(), configClazz, requiredFields, overrideFields);
            final File parentPath = new File(generatedSrcDir, connectorConfigGenerator.getPackageName().replace(".", "/"));
            final File connectorConfigClassFile = new File(parentPath, connectorConfigGenerator.getClassName() + ".java");
            if (!connectorConfigClassFile.exists()) {
                connectorConfigClassFile.getParentFile().mkdirs();
                connectorConfigClassFile.createNewFile();
            }
            FileUtil.updateFile(connectorConfigClassFile.toPath(), connectorConfigGenerator.printClassAsString());
        } catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private SourceConnector getConnector() {
        return org.apache.camel.support.ObjectHelper.newInstance(ObjectHelper.loadClass(connectorClassName), SourceConnector.class);
    }

    public void setGeneratedSrcDir(final File generatedSrcDir) {
        this.generatedSrcDir = generatedSrcDir;
    }

    public void setConnectorClassName(String connectorClassName) {
        this.connectorClassName = connectorClassName;
    }

    public void setConnectorConfigClassName(String connectorConfigClassName) {
        this.connectorConfigClassName = connectorConfigClassName;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public void setRequiredFields(List<String> requiredFields) {
        this.requiredFields = requiredFields;
    }

    public Set<String> getRequiredFields() {
        return new HashSet<>(requiredFields);
    }

    public Map<String, Object> getFields() {
        return fields;
    }
}
