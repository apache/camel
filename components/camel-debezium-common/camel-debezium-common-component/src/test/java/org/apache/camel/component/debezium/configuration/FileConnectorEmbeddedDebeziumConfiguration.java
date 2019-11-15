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
package org.apache.camel.component.debezium.configuration;

import java.nio.file.Path;

import io.debezium.config.Configuration;
import org.apache.kafka.connect.file.FileStreamSourceConnector;

public class FileConnectorEmbeddedDebeziumConfiguration extends EmbeddedDebeziumConfiguration {

    private Path testFilePath;
    private String topicConfig;

    @Override
    protected Configuration createConnectorConfiguration() {
        return Configuration.create()
                .with(FileStreamSourceConnector.FILE_CONFIG, testFilePath)
                .with(FileStreamSourceConnector.TOPIC_CONFIG, topicConfig)
                .build();
    }

    @Override
    protected ConfigurationValidation validateConnectorConfiguration() {
        if (isFieldValueNotSet(testFilePath)) {
            return ConfigurationValidation.notValid("testFilePath is not set");
        }
        if (isFieldValueNotSet(topicConfig)) {
            return ConfigurationValidation.notValid("topicConfig is not set");
        }
        return ConfigurationValidation.valid();
    }

    @Override
    public String getConnectorDatabaseType() {
        return "file";
    }

    @Override
    protected Class<?> configureConnectorClass() {
        return FileStreamSourceConnector.class;
    }

    public Path getTestFilePath() {
        return testFilePath;
    }

    public void setTestFilePath(Path testFilePath) {
        this.testFilePath = testFilePath;
    }

    public String getTopicConfig() {
        return topicConfig;
    }

    public void setTopicConfig(String topicConfig) {
        this.topicConfig = topicConfig;
    }
}
