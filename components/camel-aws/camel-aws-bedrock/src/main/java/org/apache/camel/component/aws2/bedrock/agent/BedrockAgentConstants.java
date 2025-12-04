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

package org.apache.camel.component.aws2.bedrock.agent;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Bedrock Agent Runtime module SDK v2
 */
public interface BedrockAgentConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String", label = "producer")
    String OPERATION = "CamelAwsBedrockAgentOperation";

    @Metadata(
            description = "The header could be used to set up a model Id dynamically while performing operation",
            javaType = "String",
            label = "producer")
    String MODEL_ID = "CamelAwsBedrockAgentRuntimeModelId";

    @Metadata(
            description = "The header could be used to set up a data source Id dynamically while performing operation",
            javaType = "String",
            label = "producer")
    String DATASOURCE_ID = "CamelAwsBedrockAgentDataSourceId";

    @Metadata(
            description =
                    "The header could be used to set up a knowledge base Id dynamically while performing operation",
            javaType = "String",
            label = "producer")
    String KNOWLEDGE_BASE_ID = "CamelAwsBedrockAgentKnowledgeBaseId";

    @Metadata(description = "The header contains the id of the ingestion job", javaType = "String", label = "producer")
    String INGESTION_JOB_ID = "CamelAwsBedrockAgentIngestionJobId";

    @Metadata(
            description = "The header contains the status of the ingestion job",
            javaType = "String",
            label = "consumer")
    String INGESTION_JOB_STATUS = "CamelAwsBedrockAgentIngestionJobStatus";

    @Metadata(
            description = "The header contains the failure reasons of the ingestion job",
            javaType = "java.util.List",
            label = "common")
    String INGESTION_JOB_FAILURE_REASONS = "CamelAwsBedrockAgentIngestionJobFailureReasons";
}
