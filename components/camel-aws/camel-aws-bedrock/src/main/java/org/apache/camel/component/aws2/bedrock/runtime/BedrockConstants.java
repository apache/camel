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
    @Metadata(description = "The operation that the producer performs. It replaces the operation option.", javaType = "String")
    String OPERATION = "CamelAwsBedrockOperation";
    @Metadata(description = "The MIME type of the request body of the invoke operations, usually application/json. The invoke"
                            + " operations fail without it, unless pojoRequest is true.",
              javaType = "String")
    String MODEL_CONTENT_TYPE = "CamelAwsBedrockContentType";
    @Metadata(description = "The MIME type of the response that the invoke operations ask the model for, usually"
                            + " application/json. The invoke operations fail without it, unless pojoRequest is true.",
              javaType = "String")
    String MODEL_ACCEPT_CONTENT_TYPE = "CamelAwsBedrockAcceptContentType";
    @Metadata(description = "The output mode of the streaming operations and converseStream: complete or chunks. It replaces"
                            + " the streamOutputMode option.",
              javaType = "String")
    String STREAM_OUTPUT_MODE = "CamelAwsBedrockStreamOutputMode";
    @Metadata(description = "The reason why the model stopped generating, in the terms of its model family, such as end_turn"
                            + " or FINISH. Set by the streaming invoke operations when includeStreamingMetadata is true and the"
                            + " model sends it.",
              javaType = "String")
    String STREAMING_COMPLETION_REASON = "CamelAwsBedrockCompletionReason";
    @Metadata(description = "The number of tokens that the model generated. Set by the streaming invoke operations when"
                            + " includeStreamingMetadata is true and the model sends a count.",
              javaType = "Integer")
    String STREAMING_TOKEN_COUNT = "CamelAwsBedrockTokenCount";
    @Metadata(description = "The number of chunks of the response stream. Set by the streaming invoke operations and"
                            + " converseStream when includeStreamingMetadata is true.",
              javaType = "Integer")
    String STREAMING_CHUNK_COUNT = "CamelAwsBedrockChunkCount";
    @Metadata(description = "The messages of the conversation for converse and converseStream, as a List of Message of the"
                            + " AWS SDK. When not set, the body as text is sent as one user message.",
              javaType = "List<software.amazon.awssdk.services.bedrockruntime.model.Message>")
    String CONVERSE_MESSAGES = "CamelAwsBedrockConverseMessages";
    @Metadata(description = "The system prompts for converse and converseStream, as a List of SystemContentBlock of the AWS"
                            + " SDK.",
              javaType = "List<software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock>")
    String CONVERSE_SYSTEM = "CamelAwsBedrockConverseSystem";
    @Metadata(description = "The inference parameters for converse and converseStream, such as maxTokens, temperature and"
                            + " topP.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration")
    String CONVERSE_INFERENCE_CONFIG = "CamelAwsBedrockConverseInferenceConfig";
    @Metadata(description = "The tools that the model can ask to use in converse and converseStream, and how it chooses them.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration")
    String CONVERSE_TOOL_CONFIG = "CamelAwsBedrockConverseToolConfig";
    @Metadata(description = "The request fields of the model that the Converse API does not define, for converse and"
                            + " converseStream, as a Document of the AWS SDK.",
              javaType = "software.amazon.awssdk.core.document.Document")
    String CONVERSE_ADDITIONAL_MODEL_REQUEST_FIELDS = "CamelAwsBedrockConverseAdditionalFields";
    @Metadata(description = "The reason why the model stopped generating, such as end_turn, tool_use or max_tokens. Set by"
                            + " converse, and by converseStream when includeStreamingMetadata is true.",
              javaType = "String")
    String CONVERSE_STOP_REASON = "CamelAwsBedrockConverseStopReason";
    @Metadata(description = "The input, output and total token counts of the request. Set by converse, and by converseStream"
                            + " when includeStreamingMetadata is true.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.TokenUsage")
    String CONVERSE_USAGE = "CamelAwsBedrockConverseUsage";
    @Metadata(description = "The message that the model returned, with all its content blocks, such as the requests to use a"
                            + " tool, while the body has only its text. Set by converse only.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.Message")
    String CONVERSE_OUTPUT_MESSAGE = "CamelAwsBedrockConverseOutputMessage";
    @Metadata(description = "The guardrail to apply in converse and converseStream, as a GuardrailConfiguration of the AWS"
                            + " SDK; converseStream also takes a GuardrailStreamConfiguration. It replaces the"
                            + " guardrailIdentifier, guardrailVersion and guardrailTrace options.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration")
    String GUARDRAIL_CONFIG = "CamelAwsBedrockGuardrailConfig";
    @Metadata(description = "The ID or ARN of the guardrail for applyGuardrail. It replaces the guardrailIdentifier option."
                            + " converse and converseStream do not read it.",
              javaType = "String")
    String GUARDRAIL_IDENTIFIER = "CamelAwsBedrockGuardrailIdentifier";
    @Metadata(description = "The content that applyGuardrail checks, as a List of GuardrailContentBlock of the AWS SDK. When"
                            + " not set, the body as text is checked.",
              javaType = "List<software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock>")
    String GUARDRAIL_CONTENT = "CamelAwsBedrockGuardrailContent";
    @Metadata(description = "Whether applyGuardrail checks the content as the input of a model (INPUT) or as its output"
                            + " (OUTPUT). The default is INPUT.",
              javaType = "String")
    String GUARDRAIL_SOURCE = "CamelAwsBedrockGuardrailSource";
    @Metadata(description = "The output of the guardrail, such as the blocked message or the text with masked content. Set by"
                            + " applyGuardrail when the guardrail returns it.",
              javaType = "List<software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputContent>")
    String GUARDRAIL_OUTPUT = "CamelAwsBedrockGuardrailOutput";
    @Metadata(description = "The trace of the guardrail evaluation. Set by converse, and by converseStream when"
                            + " includeStreamingMetadata is true, when the trace of the guardrail is enabled.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.GuardrailTraceAssessment")
    String GUARDRAIL_TRACE = "CamelAwsBedrockGuardrailTrace";
    @Metadata(description = "The assessments of the policies of the guardrail, such as its content and topic policies. Set by"
                            + " applyGuardrail.",
              javaType = "List<software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment>")
    String GUARDRAIL_ASSESSMENTS = "CamelAwsBedrockGuardrailAssessments";
    @Metadata(description = "The number of text units that each policy of the guardrail processed. Set by applyGuardrail.",
              javaType = "software.amazon.awssdk.services.bedrockruntime.model.GuardrailUsage")
    String GUARDRAIL_USAGE = "CamelAwsBedrockGuardrailUsage";
}
