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
package org.apache.camel.component.salesforce.codegen;

import java.io.File;
import java.nio.file.Files;

import com.salesforce.eventbus.protobuf.TopicInfo;
import org.apache.avro.compiler.specific.SpecificCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GeneratePubSubExecution extends AbstractSalesforceExecution {

    private String[] topics;
    private File outputDirectory;

    @Override
    protected void executeWithClient() throws Exception {
        for (String topicName : topics) {
            final TopicInfo topicInfo = getPubSubApiClient().getTopicInfo(topicName);
            final String schemaJson = getPubSubApiClient().getSchemaJson(topicInfo.getSchemaId());
            final File schemaFile = File.createTempFile("schema", ".json", outputDirectory);
            Files.writeString(schemaFile.toPath(), schemaJson);
            SpecificCompiler.compileSchema(schemaFile, outputDirectory);
        }
    }

    public void setTopics(String[] topics) {
        this.topics = topics;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    protected Logger getLog() {
        return LoggerFactory.getLogger(GeneratePubSubExecution.class);
    }
}
