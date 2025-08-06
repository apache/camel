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
package org.apache.camel.component.langchain4j.agent;

public class AiAgentBody {
    private String userMessage;
    private String systemMessage;
    private Object memoryId;

    public AiAgentBody() {
    }

    public AiAgentBody(String userMessage) {
        this.userMessage = userMessage;
    }

    public AiAgentBody(String userMessage, String systemMessage, Object memoryId) {
        this.userMessage = userMessage;
        this.systemMessage = systemMessage;
        this.memoryId = memoryId;
    }

    public AiAgentBody withUserMessage(String userMessage) {
        this.userMessage = userMessage;
        return this;
    }

    public AiAgentBody withSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
        return this;
    }

    public AiAgentBody withMemoryId(Object memoryId) {
        this.memoryId = memoryId;
        return this;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public Object getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Object memoryId) {
        this.memoryId = memoryId;
    }
}
