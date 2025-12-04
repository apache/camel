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

package org.apache.camel.component.aws2.bedrock.runtime;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Bedrock module SDK v2
 */
public interface BedrockConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsBedrockOperation";

    @Metadata(description = "The model content type", javaType = "String")
    String MODEL_CONTENT_TYPE = "CamelAwsBedrockContentType";

    @Metadata(description = "The model accept content type", javaType = "String")
    String MODEL_ACCEPT_CONTENT_TYPE = "CamelAwsBedrockAcceptContentType";

    @Metadata(description = "The streaming output mode (complete or chunks)", javaType = "String")
    String STREAM_OUTPUT_MODE = "CamelAwsBedrockStreamOutputMode";

    @Metadata(description = "The completion reason for streaming response", javaType = "String")
    String STREAMING_COMPLETION_REASON = "CamelAwsBedrockCompletionReason";

    @Metadata(description = "The number of tokens generated in streaming response", javaType = "Integer")
    String STREAMING_TOKEN_COUNT = "CamelAwsBedrockTokenCount";

    @Metadata(description = "The number of chunks received in streaming response", javaType = "Integer")
    String STREAMING_CHUNK_COUNT = "CamelAwsBedrockChunkCount";

    @Metadata(description = "The conversation messages for Converse API", javaType = "List<Message>")
    String CONVERSE_MESSAGES = "CamelAwsBedrockConverseMessages";

    @Metadata(description = "The system prompts for Converse API", javaType = "List<SystemContentBlock>")
    String CONVERSE_SYSTEM = "CamelAwsBedrockConverseSystem";

    @Metadata(description = "The inference configuration for Converse API", javaType = "InferenceConfiguration")
    String CONVERSE_INFERENCE_CONFIG = "CamelAwsBedrockConverseInferenceConfig";

    @Metadata(description = "The tool configuration for Converse API", javaType = "ToolConfiguration")
    String CONVERSE_TOOL_CONFIG = "CamelAwsBedrockConverseToolConfig";

    @Metadata(
            description = "The additional model request fields for Converse API",
            javaType = "software.amazon.awssdk.core.document.Document")
    String CONVERSE_ADDITIONAL_MODEL_REQUEST_FIELDS = "CamelAwsBedrockConverseAdditionalFields";

    @Metadata(description = "The stop reason from Converse API response", javaType = "String")
    String CONVERSE_STOP_REASON = "CamelAwsBedrockConverseStopReason";

    @Metadata(description = "The usage metrics from Converse API response", javaType = "TokenUsage")
    String CONVERSE_USAGE = "CamelAwsBedrockConverseUsage";

    @Metadata(description = "The output message from Converse API response", javaType = "Message")
    String CONVERSE_OUTPUT_MESSAGE = "CamelAwsBedrockConverseOutputMessage";

    @Metadata(description = "The guardrail configuration to apply to the request", javaType = "GuardrailConfiguration")
    String GUARDRAIL_CONFIG = "CamelAwsBedrockGuardrailConfig";

    @Metadata(description = "The content blocks for ApplyGuardrail operation", javaType = "List<GuardrailContentBlock>")
    String GUARDRAIL_CONTENT = "CamelAwsBedrockGuardrailContent";

    @Metadata(description = "The source type for ApplyGuardrail operation (INPUT or OUTPUT)", javaType = "String")
    String GUARDRAIL_SOURCE = "CamelAwsBedrockGuardrailSource";

    @Metadata(description = "The guardrail assessment output from the response", javaType = "GuardrailAssessment")
    String GUARDRAIL_OUTPUT = "CamelAwsBedrockGuardrailOutput";

    @Metadata(description = "The trace information from guardrail evaluation", javaType = "GuardrailTrace")
    String GUARDRAIL_TRACE = "CamelAwsBedrockGuardrailTrace";

    @Metadata(
            description = "The guardrail assessments from ApplyGuardrail response",
            javaType = "List<GuardrailAssessment>")
    String GUARDRAIL_ASSESSMENTS = "CamelAwsBedrockGuardrailAssessments";

    @Metadata(description = "The guardrail usage metrics from ApplyGuardrail response", javaType = "GuardrailUsage")
    String GUARDRAIL_USAGE = "CamelAwsBedrockGuardrailUsage";
}
