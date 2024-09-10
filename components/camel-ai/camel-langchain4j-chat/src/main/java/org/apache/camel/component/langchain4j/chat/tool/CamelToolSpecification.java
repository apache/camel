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
package org.apache.camel.component.langchain4j.chat.tool;

import java.util.Objects;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.component.langchain4j.chat.LangChain4jChatConsumer;

/**
 * Holds ToolSpecification needed by langchain4j and the associated Camel Consumer. In this way, a specific route can be
 * invoked by a specific Tool
 */
@Deprecated(since = "4.8.0")
public class CamelToolSpecification {

    private ToolSpecification toolSpecification;
    private LangChain4jChatConsumer consumer;

    public CamelToolSpecification(ToolSpecification toolSpecification, LangChain4jChatConsumer consumer) {
        this.toolSpecification = toolSpecification;
        this.consumer = consumer;
    }

    public ToolSpecification getToolSpecification() {
        return toolSpecification;
    }

    public void setToolSpecification(ToolSpecification toolSpecification) {
        this.toolSpecification = toolSpecification;
    }

    public LangChain4jChatConsumer getConsumer() {
        return consumer;
    }

    public void setConsumer(LangChain4jChatConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CamelToolSpecification that = (CamelToolSpecification) o;
        return Objects.equals(toolSpecification, that.toolSpecification) && Objects.equals(consumer,
                that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecification, consumer);
    }

    @Override
    public String toString() {
        return "CamelToolSpecification{" +
               "toolSpecification=" + toolSpecification +
               ", consumer=" + consumer +
               '}';
    }
}
