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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.camel.maven.config.ConnectorConfigGenerator;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractGenerateConnectorConfig extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/connector-configurations")
    private File generatedSrcDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Set<String> requiredFields = getRequiredFields();
        final Map<String, Object> overrideFields = getOverrideFields();

        final ConnectorConfigGenerator connectorConfigGenerator = ConnectorConfigGenerator.create(getConnector(), getConnectorConfigClass(), requiredFields, overrideFields);
        try {
            final File parentPath = new File(generatedSrcDir, connectorConfigGenerator.getPackageName().replace(".", "/"));
            final File connectorConfigClassFile = new File(parentPath, connectorConfigGenerator.getClassName() + ".java");
            if (!connectorConfigClassFile.exists()) {
                connectorConfigClassFile.getParentFile().mkdirs();
                connectorConfigClassFile.createNewFile();
            }
            connectorConfigGenerator.printGeneratedClass(new FileOutputStream(connectorConfigClassFile));
        } catch (IOException e) {
            throw new MojoFailureException("error", e);
        }
    }

    public void setGeneratedSrcDir(final File generatedSrcDir) {
        this.generatedSrcDir = generatedSrcDir;
    }

    protected abstract Set<String> getRequiredFields();

    protected abstract Map<String, Object> getOverrideFields();

    protected abstract SourceConnector getConnector();

    protected abstract Class<?> getConnectorConfigClass();
}
