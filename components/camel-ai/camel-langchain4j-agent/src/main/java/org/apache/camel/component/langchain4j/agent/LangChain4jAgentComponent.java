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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.core.LangChain4jModelFactory;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import static org.apache.camel.component.langchain4j.agent.LangChain4jAgent.SCHEME;

@Component(SCHEME)
public class LangChain4jAgentComponent extends DefaultComponent {
    @Metadata
    LangChain4jAgentConfiguration configuration;

    private final Map<LangChain4jModelFactory.ModelSpec, ChatModel> models = new ConcurrentHashMap<>();

    public LangChain4jAgentComponent() {
        this(null);
    }

    public LangChain4jAgentComponent(CamelContext context) {
        super(context);
        this.configuration = new LangChain4jAgentConfiguration();
    }

    public LangChain4jAgentConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration
     *
     * @param configuration
     */
    public void setConfiguration(LangChain4jAgentConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        LangChain4jAgentConfiguration langchain4jChatConfiguration = this.configuration.copy();

        Endpoint endpoint = new LangChain4jAgentEndpoint(uri, this, remaining, langchain4jChatConfiguration);
        langchain4jChatConfiguration.setModelProperties(
                LangChain4jModelFactory.extractModelProperties(parameters, langchain4jChatConfiguration.getModelProperties()));
        setProperties(endpoint, parameters);
        boolean noAgent = langchain4jChatConfiguration.getAgent() == null
                && langchain4jChatConfiguration.getAgentFactory() == null
                && langchain4jChatConfiguration.getAgentConfiguration() == null;
        if (noAgent && langchain4jChatConfiguration.modelSpec() != null) {
            // the chat model is declared by its provider and options (CAMEL-24820): the agent is created from it as
            // with an AgentConfiguration holding only the model; endpoints with the same options share the model
            LangChain4jModelFactory.ModelSpec spec = langchain4jChatConfiguration.modelSpec();
            ChatModel chatModel
                    = models.computeIfAbsent(spec, s -> LangChain4jModelFactory.createChatModel(getCamelContext(), s));
            langchain4jChatConfiguration.setAgentConfiguration(new AgentConfiguration().withChatModel(chatModel));
        }
        return endpoint;
    }
}
