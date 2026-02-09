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
package org.apache.camel.component.langchain4j.tools.spec;

import java.util.Objects;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.component.langchain4j.tools.LangChain4jToolsConsumer;

/**
 * Holds ToolSpecification needed by langchain4j and the associated Camel Consumer. In this way, a specific route can be
 * invoked by a specific Tool
 */
public class CamelToolSpecification {

    private ToolSpecification toolSpecification;
    private LangChain4jToolsConsumer consumer;
    private boolean exposed;

    public CamelToolSpecification(ToolSpecification toolSpecification, LangChain4jToolsConsumer consumer) {
        this(toolSpecification, consumer, true);
    }

    public CamelToolSpecification(ToolSpecification toolSpecification, LangChain4jToolsConsumer consumer, boolean exposed) {
        this.toolSpecification = toolSpecification;
        this.consumer = consumer;
        this.exposed = exposed;
    }

    public ToolSpecification getToolSpecification() {
        return toolSpecification;
    }

    public void setToolSpecification(ToolSpecification toolSpecification) {
        this.toolSpecification = toolSpecification;
    }

    public LangChain4jToolsConsumer getConsumer() {
        return consumer;
    }

    public void setConsumer(LangChain4jToolsConsumer consumer) {
        this.consumer = consumer;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
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
        return exposed == that.exposed && Objects.equals(toolSpecification, that.toolSpecification)
                && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecification, consumer, exposed);
    }

    @Override
    public String toString() {
        return "CamelToolSpecification{" +
               "toolSpecification=" + toolSpecification +
               ", consumer=" + consumer +
               ", exposed=" + exposed +
               '}';
    }
}
