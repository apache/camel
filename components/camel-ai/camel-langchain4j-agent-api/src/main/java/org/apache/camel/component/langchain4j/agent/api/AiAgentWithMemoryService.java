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

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI Agent Service interface for LangChain4j integration with chat memory support. This is a separate interface due to
 * limitations in @MemoryId annotation usage.
 */
public interface AiAgentWithMemoryService {

    /**
     * Simple chat with a user message and window memory
     *
     * @param  memoryId
     * @param  message
     * @return
     */
    String chat(@MemoryId Object memoryId, @UserMessage String message);

    /**
     * Simple chat with a user message, system message and memory window
     *
     * @param  memoryId
     * @param  message
     * @param  prompt
     * @return
     */
    @SystemMessage("{{prompt}}")
    String chat(@MemoryId Object memoryId, @UserMessage String message, @V("prompt") String prompt);
}
