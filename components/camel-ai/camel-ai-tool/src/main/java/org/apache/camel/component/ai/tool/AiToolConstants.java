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
package org.apache.camel.component.ai.tool;

import org.apache.camel.spi.Metadata;

/**
 * Headers populated when an AI framework invokes a tool route, and the exchange properties that carry the caller's
 * identity to it.
 */
public final class AiToolConstants {

    /**
     * Exchange property carrying the caller's role names (a {@code Collection<String>}), which the {@code allowedRoles}
     * option checks. Over the MCP server the bridge sets it from the authenticated caller; on the other paths the
     * calling route sets it before the agent runs. It is an exchange property rather than a message header because on a
     * tool route the headers carry the tool arguments, which the model controls.
     *
     * @since 4.24
     */
    public static final String SECURITY_PRINCIPAL_ROLES_PROPERTY = "CamelAiToolSecurityPrincipalRoles";

    /**
     * Exchange property carrying the caller's name (a {@code String}), set where
     * {@link #SECURITY_PRINCIPAL_ROLES_PROPERTY} is. The log entry of a call that {@code allowedRoles} refused names
     * the caller with it.
     *
     * @since 4.24
     */
    public static final String SECURITY_PRINCIPAL_NAME_PROPERTY = "CamelAiToolSecurityPrincipalName";

    @Metadata(label = "consumer", description = "The tool invocation identifier supplied by the calling framework. "
                                                + "Set when an in-process producer (such as openai or langchain4j-agent) "
                                                + "calls the tool. Absent when the framework does not supply one, "
                                                + "such as for a call over MCP, where the bridge has no tool call id.",
              javaType = "String")
    public static final String TOOL_CALL_ID = "CamelAiToolCallId";

    @Metadata(label = "consumer", description = "The conversation identifier supplied by the calling framework. "
                                                + "Absent when the framework does not supply one.",
              javaType = "String")
    public static final String CONVERSATION_ID = "CamelAiToolConversationId";

    private AiToolConstants() {
    }
}
