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
package org.apache.camel.component.ai.observability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context for a single GenAI client operation observation.
 */
public final class GenAiObservationContext {

    private final GenAiOperationName operationName;
    private final String system;
    private final String requestModel;
    private final String componentScheme;
    private final String agentName;
    private final String toolName;
    private final String toolCallId;
    private final String conversationId;
    private final Map<String, Object> requestParameters;
    private final GenAiObservation parent;
    private final boolean tracedByFramework;

    private GenAiObservationContext(Builder builder) {
        this.operationName = builder.operationName;
        this.system = builder.system;
        this.requestModel = builder.requestModel;
        this.componentScheme = builder.componentScheme;
        this.agentName = builder.agentName;
        this.toolName = builder.toolName;
        this.toolCallId = builder.toolCallId;
        this.conversationId = builder.conversationId;
        this.requestParameters = Collections.unmodifiableMap(builder.requestParameters);
        this.parent = builder.parent;
        this.tracedByFramework = builder.tracedByFramework;
    }

    public GenAiOperationName operationName() {
        return operationName;
    }

    public String system() {
        return system;
    }

    public String requestModel() {
        return requestModel;
    }

    public String componentScheme() {
        return componentScheme;
    }

    /**
     * The agent name of an {@link GenAiOperationName#INVOKE_AGENT} operation.
     *
     * @since 4.24
     */
    public String agentName() {
        return agentName;
    }

    /**
     * The tool name of an {@link GenAiOperationName#EXECUTE_TOOL} operation.
     *
     * @since 4.24
     */
    public String toolName() {
        return toolName;
    }

    /**
     * The id the model gave to the tool call, when known.
     *
     * @since 4.24
     */
    public String toolCallId() {
        return toolCallId;
    }

    /**
     * The conversation (chat memory, session) id, when known.
     *
     * @since 4.24
     */
    public String conversationId() {
        return conversationId;
    }

    /**
     * Request parameters such as {@code temperature}, {@code max_tokens} or {@code top_p}, recorded as
     * {@code gen_ai.request.<name>} from level {@link GenAiObservabilityLevel#DETAILED}.
     *
     * @since 4.24
     */
    public Map<String, Object> requestParameters() {
        return requestParameters;
    }

    /**
     * The observation to nest this one under, instead of the span active on the exchange. Used for the LLM calls and
     * tool calls of an agent run.
     *
     * @since 4.24
     */
    public GenAiObservation parent() {
        return parent;
    }

    /**
     * Whether the AI framework behind the component already traces this call. Such a call is not recorded by Camel when
     * {@link GenAiObservabilityProperties#DEFER_TO_FRAMEWORK} is enabled.
     *
     * @since 4.24
     */
    public boolean tracedByFramework() {
        return tracedByFramework;
    }

    /**
     * The span name, following the GenAI semantic conventions: {@code {operation} {model}}, {@code invoke_agent
     * {agent}} or {@code execute_tool {tool}}.
     */
    public String spanName() {
        String target = switch (operationName) {
            case INVOKE_AGENT -> agentName;
            case EXECUTE_TOOL -> toolName;
            default -> requestModel;
        };
        if (target == null || target.isBlank()) {
            return operationName == GenAiOperationName.INVOKE_AGENT || operationName == GenAiOperationName.EXECUTE_TOOL
                    ? operationName.value() : operationName.value() + " unknown";
        }
        return operationName.value() + " " + target;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder holding the values of this context.
     *
     * @since 4.24
     */
    public Builder toBuilder() {
        Builder builder = new Builder()
                .operationName(operationName)
                .system(system)
                .requestModel(requestModel)
                .componentScheme(componentScheme)
                .agentName(agentName)
                .toolName(toolName)
                .toolCallId(toolCallId)
                .conversationId(conversationId)
                .parent(parent)
                .tracedByFramework(tracedByFramework);
        requestParameters.forEach(builder::requestParameter);
        return builder;
    }

    public static final class Builder {
        private GenAiOperationName operationName = GenAiOperationName.CHAT;
        private String system = "unknown";
        private String requestModel;
        private String componentScheme;
        private String agentName;
        private String toolName;
        private String toolCallId;
        private String conversationId;
        private final Map<String, Object> requestParameters = new LinkedHashMap<>();
        private GenAiObservation parent;
        private boolean tracedByFramework;

        public Builder operationName(GenAiOperationName operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder system(String system) {
            this.system = system;
            return this;
        }

        public Builder requestModel(String requestModel) {
            this.requestModel = requestModel;
            return this;
        }

        public Builder componentScheme(String componentScheme) {
            this.componentScheme = componentScheme;
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder conversationId(Object conversationId) {
            this.conversationId = conversationId != null ? conversationId.toString() : null;
            return this;
        }

        /**
         * Adds a request parameter (for example {@code temperature}); {@code null} values are ignored.
         *
         * @since 4.24
         */
        public Builder requestParameter(String name, Object value) {
            if (name != null && value != null) {
                this.requestParameters.put(name, value);
            }
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder parent(GenAiObservation parent) {
            this.parent = parent;
            return this;
        }

        /**
         * @since 4.24
         */
        public Builder tracedByFramework(boolean tracedByFramework) {
            this.tracedByFramework = tracedByFramework;
            return this;
        }

        public GenAiObservationContext build() {
            return new GenAiObservationContext(this);
        }
    }
}
