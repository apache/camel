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
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.debezium.connector.mysql.MySqlConnector;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class GenerateConnectorConfigMojoTest {

    @Rule
    public TemporaryFolder configFolder = new TemporaryFolder();

    @Test
    public void testIfGeneratedConfigFileCorrectly() throws MojoFailureException, IOException {
        final GenerateConnectorConfigMojo generateConnectorConfigMojo = new GenerateConnectorConfigMojo();
        final File connectorConfigFolder = configFolder.newFolder("connector-configurations");

        generateConnectorConfigMojo.setLog(new SystemStreamLog());
        generateConnectorConfigMojo.setGeneratedSrcDir(connectorConfigFolder);
        generateConnectorConfigMojo.setConnectorClassName(MySqlConnector.class.getName());
        generateConnectorConfigMojo.setConnectorConfigClassName(MySqlConnectorConfig.class.getName());

        generateConnectorConfigMojo.execute();

        // check if we created the file correctly
        final File connectorConfigFile = new File(connectorConfigFolder, "org/apache/camel/component/debezium/configuration/MySqlConnectorEmbeddedDebeziumConfiguration.java");
        assertTrue(connectorConfigFile.exists());

        // we check the file content
        final String connectorConfigFileAsText = FileUtils.readFileToString(connectorConfigFile, StandardCharsets.UTF_8);
        assertNotNull(connectorConfigFileAsText);
        assertTrue(connectorConfigFileAsText.contains("MySqlConnectorEmbeddedDebeziumConfiguration"));
    }

}
