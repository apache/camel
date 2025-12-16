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

import org.apache.camel.spi.Metadata;

/**
 * Headers that the agent component may rely on
 */
public class Headers {
    @Metadata(description = "The system prompt.", javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelLangChain4jAgentSystemMessage";

    @Metadata(description = "Memory ID.", javaType = "Object")
    public static final String MEMORY_ID = "CamelLangChain4jAgentMemoryId";

    @Metadata(description = "The user message to accompany file content when using WrappedFile as input.", javaType = "String")
    public static final String USER_MESSAGE = "CamelLangChain4jAgentUserMessage";

    @Metadata(description = "The media type (MIME type) of the file content. Overrides auto-detection from file extension.",
              javaType = "String")
    public static final String MEDIA_TYPE = "CamelLangChain4jAgentMediaType";
}
