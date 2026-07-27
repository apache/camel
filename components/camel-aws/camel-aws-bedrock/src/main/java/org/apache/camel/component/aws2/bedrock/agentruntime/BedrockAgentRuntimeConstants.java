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
package org.apache.camel.component.aws2.bedrock.agentruntime;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Bedrock Agent Runtime module SDK v2
 */
public interface BedrockAgentRuntimeConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsBedrockAgentRuntimeOperation";

    @Metadata(description = "When retrieving and generating a response, this header will contain the citations",
              javaType = "String")
    String CITATIONS = "CamelAwsBedrockAgentRuntimeCitations";

    @Metadata(description = "When retrieving and generating a response, this header will contain he unique identifier of the session. "
                            +
                            "Reuse the same value to continue the same session with the knowledge base.",
              javaType = "String")
    String SESSION_ID = "CamelAwsBedrockAgentRuntimeSessionId";

    @Metadata(description = "The unique identifier of the agent to invoke. Overrides the agentId configured on the endpoint.",
              javaType = "String")
    String AGENT_ID = "CamelAwsBedrockAgentRuntimeAgentId";

    @Metadata(description = "The unique identifier of the agent alias to invoke. Overrides the agentAliasId configured on the endpoint.",
              javaType = "String")
    String AGENT_ALIAS_ID = "CamelAwsBedrockAgentRuntimeAgentAliasId";

    @Metadata(description = "Enables tracing for the agent invocation. When set, overrides the enableTrace option on the endpoint.",
              javaType = "Boolean")
    String AGENT_ENABLE_TRACE = "CamelAwsBedrockAgentRuntimeAgentEnableTrace";

    @Metadata(description = "Ends the agent session after this invocation.", javaType = "Boolean")
    String AGENT_END_SESSION = "CamelAwsBedrockAgentRuntimeAgentEndSession";

    @Metadata(description = "The unique identifier of the agent memory to use across sessions.", javaType = "String")
    String AGENT_MEMORY_ID = "CamelAwsBedrockAgentRuntimeAgentMemoryId";

    @Metadata(description = "The streaming output mode (complete or chunks) for an agent invocation. When set, overrides the "
                            + "streamOutputMode option on the endpoint.",
              javaType = "String")
    String AGENT_STREAM_OUTPUT_MODE = "CamelAwsBedrockAgentRuntimeAgentStreamOutputMode";

    @Metadata(description = "The foundation model used by an inline agent. Overrides the foundationModel configured on the endpoint.",
              javaType = "String")
    String AGENT_FOUNDATION_MODEL = "CamelAwsBedrockAgentRuntimeAgentFoundationModel";

    @Metadata(description = "The instruction given to an inline agent. Overrides the instruction configured on the endpoint.",
              javaType = "String")
    String AGENT_INSTRUCTION = "CamelAwsBedrockAgentRuntimeAgentInstruction";

    @Metadata(description = "When invoking an agent with tracing enabled, this header will contain the list of TracePart emitted "
                            + "during the invocation. The elements are TracePart for invokeAgent and InlineAgentTracePart for invokeInlineAgent.",
              javaType = "java.util.List")
    String AGENT_TRACES = "CamelAwsBedrockAgentRuntimeAgentTraces";

    @Metadata(description = "When an agent invocation requires the caller to fulfil an action, this header will contain the list "
                            + "of return-control payloads emitted by the agent. The elements are ReturnControlPayload for invokeAgent and InlineAgentReturnControlPayload for invokeInlineAgent.",
              javaType = "java.util.List")
    String AGENT_RETURN_CONTROL = "CamelAwsBedrockAgentRuntimeAgentReturnControl";

    @Metadata(description = "When an agent invocation returns files, this header will contain the files emitted by the agent. The elements are FilePart for invokeAgent and InlineAgentFilePart for invokeInlineAgent.",
              javaType = "java.util.List")
    String AGENT_FILES = "CamelAwsBedrockAgentRuntimeAgentFiles";

    @Metadata(description = "The unique identifier of the flow to invoke. Overrides the flowIdentifier configured on the endpoint.",
              javaType = "String")
    String FLOW_IDENTIFIER = "CamelAwsBedrockAgentRuntimeFlowIdentifier";

    @Metadata(description = "The unique identifier of the flow alias to invoke. Overrides the flowAliasIdentifier configured on the endpoint.",
              javaType = "String")
    String FLOW_ALIAS_IDENTIFIER = "CamelAwsBedrockAgentRuntimeFlowAliasIdentifier";

    @Metadata(description = "Enables tracing for the flow invocation. When set, overrides the enableTrace option on the endpoint.",
              javaType = "Boolean")
    String FLOW_ENABLE_TRACE = "CamelAwsBedrockAgentRuntimeFlowEnableTrace";

    @Metadata(description = "The unique identifier of an in-progress flow execution to continue. Used for multi-turn flow conversations.",
              javaType = "String")
    String FLOW_EXECUTION_ID = "CamelAwsBedrockAgentRuntimeFlowExecutionId";

    @Metadata(description = "When invoking a flow, this header will contain the list of FlowOutputEvent emitted by the flow.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent>")
    String FLOW_OUTPUTS = "CamelAwsBedrockAgentRuntimeFlowOutputs";

    @Metadata(description = "When invoking a flow with tracing enabled, this header will contain the list of FlowTraceEvent emitted during execution.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.FlowTraceEvent>")
    String FLOW_TRACES = "CamelAwsBedrockAgentRuntimeFlowTraces";

    @Metadata(description = "When invoking a flow, this header will contain the reason the flow completed (set when a FlowCompletionEvent is received).",
              javaType = "String")
    String FLOW_COMPLETION_REASON = "CamelAwsBedrockAgentRuntimeFlowCompletionReason";

    @Metadata(description = "When performing a retrieve operation, this header will contain the list of "
                            + "KnowledgeBaseRetrievalResult chunks returned by the knowledge base.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult>")
    String RETRIEVED_RESULTS = "CamelAwsBedrockAgentRuntimeRetrievedResults";

    @Metadata(description = "Overrides the maximum number of results returned by the retrieve operation. "
                            + "Must be a positive Integer; when not set the AWS service default is used.",
              javaType = "Integer")
    String NUMBER_OF_RESULTS = "CamelAwsBedrockAgentRuntimeNumberOfResults";

    @Metadata(description = "Overrides the search type used by the retrieve operation. Accepts the AWS SearchType "
                            + "enum (HYBRID, SEMANTIC) or its String representation.",
              javaType = "String")
    String OVERRIDE_SEARCH_TYPE = "CamelAwsBedrockAgentRuntimeSearchType";

    @Metadata(description = "Pagination token used by the retrieve operation. Set on the in-message to request "
                            + "the next page; set on the out-message when the response carries one.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsBedrockAgentRuntimeNextToken";

    @Metadata(description = "When performing a retrieve operation, this header will contain the guardrail action "
                            + "(if any) applied by the knowledge base.",
              javaType = "String")
    String RETRIEVE_GUARDRAIL_ACTION = "CamelAwsBedrockAgentRuntimeRetrieveGuardrailAction";
}
