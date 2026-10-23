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
 * Constants used in Camel AWS Bedrock Agent module SDK v2
 */
public interface BedrockAgentConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String", label = "producer")
    String OPERATION = "CamelAwsBedrockAgentOperation";

    @Metadata(description = "The data source Id of the operation; it overrides the dataSourceId option",
              javaType = "String", label = "producer")
    String DATASOURCE_ID = "CamelAwsBedrockAgentDataSourceId";

    @Metadata(description = "The knowledge base Id of the operation; it overrides the knowledgeBaseId option",
              javaType = "String", label = "producer")
    String KNOWLEDGE_BASE_ID = "CamelAwsBedrockAgentKnowledgeBaseId";

    @Metadata(description = "The id of the ingestion job of the getIngestionJob operation; it overrides the ingestionJobId option",
              javaType = "String", label = "producer")
    String INGESTION_JOB_ID = "CamelAwsBedrockAgentIngestionJobId";

    @Metadata(description = "The status of the ingestion job, set by the consumer and by the getIngestionJob operation",
              javaType = "software.amazon.awssdk.services.bedrockagent.model.IngestionJobStatus", label = "common")
    String INGESTION_JOB_STATUS = "CamelAwsBedrockAgentIngestionJobStatus";

    @Metadata(description = "The failure reasons of the ingestion job, set by the consumer and by the getIngestionJob operation when the job has failure reasons",
              javaType = "List<String>", label = "common")
    String INGESTION_JOB_FAILURE_REASONS = "CamelAwsBedrockAgentIngestionJobFailureReasons";
}
