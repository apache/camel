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
 * Headers populated when an AI framework invokes a tool route.
 */
public final class AiToolConstants {

    @Metadata(label = "consumer", description = "The tool invocation identifier supplied by the calling framework. "
                                                + "Absent when the framework does not supply one.",
              javaType = "String")
    public static final String TOOL_CALL_ID = "CamelAiToolCallId";

    @Metadata(label = "consumer", description = "The conversation identifier supplied by the calling framework. "
                                                + "Absent when the framework does not supply one.",
              javaType = "String")
    public static final String CONVERSATION_ID = "CamelAiToolConversationId";

    private AiToolConstants() {
    }
}
