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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.ai.tool.AiTool.SCHEME;

/**
 * Framework-agnostic consumer endpoint that registers a Camel route as an LLM tool in the shared
 * {@link AiToolRegistry}.
 *
 * @since 4.22
 */
@UriEndpoint(
             firstVersion = "4.22.0",
             scheme = SCHEME,
             title = "AI Tool",
             syntax = "ai-tool:toolName",
             consumerOnly = true,
             remote = false,
             category = { Category.AI })
public class AiToolEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The tool name. This is the name the LLM sees and uses to invoke the tool.")
    private final String toolName;

    @UriParam(description = "Tool configuration including tags, description, and parameter definitions.")
    private AiToolConfiguration configuration;

    public AiToolEndpoint(String uri, AiToolComponent component, String toolName,
                          AiToolConfiguration configuration) {
        super(uri, component);
        this.toolName = toolName;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException(
                "ai-tool does not support producer mode. "
                                                + "Use a framework-specific component (langchain4j-tools, spring-ai-chat, openai) "
                                                + "with a matching tags parameter to invoke tools.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AiToolConsumer consumer = new AiToolConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getToolName() {
        return toolName;
    }

    public AiToolConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AiToolConfiguration configuration) {
        this.configuration = configuration;
    }
}
