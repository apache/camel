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
package org.apache.camel.component.openai;

/**
 * Strategy for handling exceptions thrown during MCP tool execution in the agentic loop.
 */
public enum ToolExecutionErrorStrategy {

    /**
     * Propagate the exception to the Camel exchange so that standard Camel error handling ({@code onException},
     * dead-letter channel) can process it. This is the default behavior, and the safer choice because
     * {@code REPROMPT_MODEL} sends raw exception messages (which may contain connection strings, hostnames, or internal
     * paths) to a third-party LLM provider.
     */
    FAIL_EXCHANGE,

    /**
     * Catch the exception, format it as a tool result error message, and send it back to the model so it can attempt to
     * recover. Note that this sends raw {@code e.getMessage()} content to the LLM provider, which may include internal
     * details.
     */
    REPROMPT_MODEL
}
