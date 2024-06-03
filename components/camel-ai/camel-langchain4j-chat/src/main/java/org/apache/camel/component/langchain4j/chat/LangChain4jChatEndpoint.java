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
package org.apache.camel.component.langchain4j.chat;

import java.util.Map;
import java.util.UUID;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.langchain4j.chat.tool.CamelSimpleToolParameter;
import org.apache.camel.component.langchain4j.chat.tool.CamelToolExecutorCache;
import org.apache.camel.component.langchain4j.chat.tool.CamelToolSpecification;
import org.apache.camel.component.langchain4j.chat.tool.NamedJsonSchemaProperty;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChat.SCHEME;

@UriEndpoint(firstVersion = "4.5.0", scheme = SCHEME,
             title = "langChain4j Chat",
             syntax = "langchain4j-chat:chatId", producerOnly = true,
             category = { Category.AI }, headersClass = LangChain4jChat.Headers.class)
public class LangChain4jChatEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The id")
    private final String chatId;

    @UriParam
    private LangChain4jChatConfiguration configuration;

    @Metadata(label = "consumer")
    @UriParam(description = "Tool description")
    private String description;

    @Metadata(label = "consumer")
    @UriParam(description = "List of Tool parameters in the form of parameter.<name>=<type>", prefix = "parameter.",
              multiValue = true, enums = "string,integer,number,object,array,boolean,null")
    private Map<String, String> parameters;

    @Metadata(label = "consumer,advanced")
    @UriParam(description = "Tool's Camel Parameters, programmatically define Tool description and parameters")
    private CamelSimpleToolParameter camelToolParameter;

    public LangChain4jChatEndpoint(String uri, LangChain4jChatComponent component, String chatId,
                                   LangChain4jChatConfiguration configuration) {
        super(uri, component);
        this.chatId = chatId;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new LangChain4jChatProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ToolSpecification.Builder toolSpecificationBuilder = ToolSpecification.builder();
        toolSpecificationBuilder.name(UUID.randomUUID().toString());
        if (camelToolParameter != null) {
            toolSpecificationBuilder.description(camelToolParameter.getDescription());

            for (NamedJsonSchemaProperty namedJsonSchemaProperty : camelToolParameter.getProperties()) {
                toolSpecificationBuilder.addParameter(namedJsonSchemaProperty.getName(),
                        namedJsonSchemaProperty.getProperties());
            }
        } else if (description != null) {
            toolSpecificationBuilder.description(description);

            if (parameters != null) {
                parameters.forEach((name, type) -> toolSpecificationBuilder.addParameter(name, JsonSchemaProperty.type(type)));
            }
        } else {
            // Consumer without toolParameter or description
            throw new IllegalArgumentException(
                    "In order to use the langchain4j component as a consumer, you need to specify at least description, or a camelToolParameter");
        }
        ToolSpecification toolSpecification = toolSpecificationBuilder.build();

        CamelToolSpecification camelToolSpecification
                = new CamelToolSpecification(toolSpecification, new LangChain4jChatConsumer(this, processor));
        CamelToolExecutorCache.getInstance().put(chatId, camelToolSpecification);

        return camelToolSpecification.getConsumer();
    }

    /**
     * Chat ID
     *
     * @return
     */
    public String getChatId() {
        return chatId;
    }

    public LangChain4jChatConfiguration getConfiguration() {
        return configuration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public CamelSimpleToolParameter getCamelToolParameter() {
        return camelToolParameter;
    }

    public void setCamelToolParameter(CamelSimpleToolParameter camelToolParameter) {
        this.camelToolParameter = camelToolParameter;
    }
}
