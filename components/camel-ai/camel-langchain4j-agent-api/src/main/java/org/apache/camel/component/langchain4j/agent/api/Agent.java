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
package org.apache.camel.component.langchain4j.agent.api;

import dev.langchain4j.service.tool.ToolProvider;

/**
 * Agent interface that abstracts the different types of AI agents (with or without memory). This interface provides a
 * unified way to interact with AI agents regardless of their underlying implementation details.
 *
 * This is an internal interface used only within the LangChain4j agent component.
 */
public interface Agent {

    /**
     * Executes a chat interaction with the AI agent using the provided body.
     *
     * @param  aiAgentBody the body containing user message, system message, and memory ID
     * @return             the AI agent's response
     */
    String chat(AiAgentBody aiAgentBody, ToolProvider toolProvider);

}
