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
    @Metadata(description = "The operation that the producer performs. It replaces the operation option.", javaType = "String")
    String OPERATION = "CamelAwsBedrockAgentRuntimeOperation";

    @Metadata(description = "The citations of the answer, which link its parts to the sources that they come from. Set by"
                            + " retrieveAndGenerate, invokeAgent and invokeInlineAgent when the answer has citations.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.Citation>")
    String CITATIONS = "CamelAwsBedrockAgentRuntimeCitations";

    @Metadata(description = "The ID of the session. retrieveAndGenerate, invokeAgent and invokeInlineAgent read it to"
                            + " continue a session, and set it to the session of the response; for the agent operations it"
                            + " replaces the sessionId option.",
              javaType = "String")
    String SESSION_ID = "CamelAwsBedrockAgentRuntimeSessionId";

    @Metadata(description = "The ID of the agent for invokeAgent. It replaces the agentId option.",
              javaType = "String")
    String AGENT_ID = "CamelAwsBedrockAgentRuntimeAgentId";

    @Metadata(description = "The ID of the alias of the agent for invokeAgent. It replaces the agentAliasId option.",
              javaType = "String")
    String AGENT_ALIAS_ID = "CamelAwsBedrockAgentRuntimeAgentAliasId";

    @Metadata(description = "Whether invokeAgent and invokeInlineAgent ask for the trace events of the invocation. It"
                            + " replaces the enableTrace option for them.",
              javaType = "Boolean")
    String AGENT_ENABLE_TRACE = "CamelAwsBedrockAgentRuntimeAgentEnableTrace";

    @Metadata(description = "Whether invokeAgent and invokeInlineAgent end the session after this invocation.",
              javaType = "Boolean")
    String AGENT_END_SESSION = "CamelAwsBedrockAgentRuntimeAgentEndSession";

    @Metadata(description = "The ID of the memory of the agent for invokeAgent, which keeps a summary of the earlier"
                            + " sessions. invokeInlineAgent does not read it.",
              javaType = "String")
    String AGENT_MEMORY_ID = "CamelAwsBedrockAgentRuntimeAgentMemoryId";

    @Metadata(description = "The output mode of invokeAgent and invokeInlineAgent: complete or chunks. It replaces the"
                            + " streamOutputMode option.",
              javaType = "String")
    String AGENT_STREAM_OUTPUT_MODE = "CamelAwsBedrockAgentRuntimeAgentStreamOutputMode";

    @Metadata(description = "The ID of the foundation model of the inline agent for invokeInlineAgent. It replaces the"
                            + " foundationModel option.",
              javaType = "String")
    String AGENT_FOUNDATION_MODEL = "CamelAwsBedrockAgentRuntimeAgentFoundationModel";

    @Metadata(description = "The instructions of the inline agent for invokeInlineAgent. It replaces the instruction option.",
              javaType = "String")
    String AGENT_INSTRUCTION = "CamelAwsBedrockAgentRuntimeAgentInstruction";

    @Metadata(description = "The trace events of the invocation: TracePart elements for invokeAgent, and InlineAgentTracePart"
                            + " elements for invokeInlineAgent. Set when the trace is enabled and the agent sends trace events.",
              javaType = "java.util.List")
    String AGENT_TRACES = "CamelAwsBedrockAgentRuntimeAgentTraces";

    @Metadata(description = "The actions that the agent returns to the caller to carry out, instead of calling an action"
                            + " group itself: ReturnControlPayload elements for invokeAgent, and"
                            + " InlineAgentReturnControlPayload elements for invokeInlineAgent. Set when the agent returns"
                            + " control.",
              javaType = "java.util.List")
    String AGENT_RETURN_CONTROL = "CamelAwsBedrockAgentRuntimeAgentReturnControl";

    @Metadata(description = "The files that the agent generated: FilePart elements for invokeAgent, and InlineAgentFilePart"
                            + " elements for invokeInlineAgent. Set when the agent sends files.",
              javaType = "java.util.List")
    String AGENT_FILES = "CamelAwsBedrockAgentRuntimeAgentFiles";

    @Metadata(description = "The ID of the flow for invokeFlow. It replaces the flowIdentifier option.",
              javaType = "String")
    String FLOW_IDENTIFIER = "CamelAwsBedrockAgentRuntimeFlowIdentifier";

    @Metadata(description = "The ID of the alias of the flow for invokeFlow. It replaces the flowAliasIdentifier option.",
              javaType = "String")
    String FLOW_ALIAS_IDENTIFIER = "CamelAwsBedrockAgentRuntimeFlowAliasIdentifier";

    @Metadata(description = "Whether invokeFlow asks for the trace events of the flow. It replaces the enableTrace option for"
                            + " invokeFlow.",
              javaType = "Boolean")
    String FLOW_ENABLE_TRACE = "CamelAwsBedrockAgentRuntimeFlowEnableTrace";

    @Metadata(description = "The ID of a flow execution that invokeFlow continues, for a flow that waits for more input, as"
                            + " in a multi-turn conversation.",
              javaType = "String")
    String FLOW_EXECUTION_ID = "CamelAwsBedrockAgentRuntimeFlowExecutionId";

    @Metadata(description = "The output events of the flow, set by invokeFlow, as an empty List when the flow has no output."
                            + " The body is the document of the last one.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.FlowOutputEvent>")
    String FLOW_OUTPUTS = "CamelAwsBedrockAgentRuntimeFlowOutputs";

    @Metadata(description = "The trace events of the flow. Set by invokeFlow when the trace is enabled and the flow sends"
                            + " trace events.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.FlowTraceEvent>")
    String FLOW_TRACES = "CamelAwsBedrockAgentRuntimeFlowTraces";

    @Metadata(description = "The reason why the flow completed: SUCCESS, or INPUT_REQUIRED when it waits for more input. Set"
                            + " by invokeFlow when the flow sends a completion event.",
              javaType = "String")
    String FLOW_COMPLETION_REASON = "CamelAwsBedrockAgentRuntimeFlowCompletionReason";

    @Metadata(description = "The results that retrieve found in the knowledge base, the same List as the body.",
              javaType = "java.util.List<software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult>")
    String RETRIEVED_RESULTS = "CamelAwsBedrockAgentRuntimeRetrievedResults";

    @Metadata(description = "The maximum number of results that retrieve returns. If not set, Amazon Bedrock uses its"
                            + " default.",
              javaType = "Integer")
    String NUMBER_OF_RESULTS = "CamelAwsBedrockAgentRuntimeNumberOfResults";

    @Metadata(description = "The search type of retrieve: HYBRID, which combines the vector search with a text search, or"
                            + " SEMANTIC, which uses the vector search only. If not set, Amazon Bedrock chooses one.",
              javaType = "String")
    String OVERRIDE_SEARCH_TYPE = "CamelAwsBedrockAgentRuntimeSearchType";

    @Metadata(description = "The token of the next page of the results of retrieve. Set it to get that page; retrieve sets it"
                            + " when there are more results, and removes it when there are none.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsBedrockAgentRuntimeNextToken";

    @Metadata(description = "Whether the guardrail of the knowledge base intervened in retrieve: INTERVENED or NONE. Set by"
                            + " retrieve when the response has it, and removed otherwise.",
              javaType = "String")
    String RETRIEVE_GUARDRAIL_ACTION = "CamelAwsBedrockAgentRuntimeRetrieveGuardrailAction";
}
