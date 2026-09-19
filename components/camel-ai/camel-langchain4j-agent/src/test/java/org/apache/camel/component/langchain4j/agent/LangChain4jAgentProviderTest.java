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

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24820: the chat model that drives the agent is declared by its provider and options, on the endpoint or the
 * component, without a ChatModel or AgentConfiguration bean.
 */
public class LangChain4jAgentProviderTest extends CamelTestSupport {

    @Test
    void agentFromProviderOptions() {
        LangChain4jAgentEndpoint endpoint = context.getEndpoint(
                "langchain4j-agent:assistant?provider=openai&apiKey=demo&modelName=gpt-4o-mini&tags=support",
                LangChain4jAgentEndpoint.class);
        AgentConfiguration agentConfiguration = endpoint.getConfiguration().getAgentConfiguration();
        assertThat(agentConfiguration).isNotNull();
        assertThat(agentConfiguration.getChatModel()).isInstanceOf(OpenAiChatModel.class);
        assertThat(endpoint.getConfiguration().getTags()).isEqualTo("support");
    }

    @Test
    void aConfiguredAgentConfigurationWins() {
        AgentConfiguration mine = new AgentConfiguration()
                .withChatModel(OpenAiChatModel.builder().apiKey("demo").modelName("gpt-4o-mini").build());
        context.getRegistry().bind("agentConfiguration", mine);
        LangChain4jAgentEndpoint endpoint = context.getEndpoint(
                "langchain4j-agent:assistant?provider=ollama&modelName=qwen2.5", LangChain4jAgentEndpoint.class);
        assertThat(endpoint.getConfiguration().getAgentConfiguration()).isSameAs(mine);
    }

    @Test
    void modelFromComponentOptionsIsSharedByEndpoints() {
        LangChain4jAgentComponent component = context.getComponent("langchain4j-agent", LangChain4jAgentComponent.class);
        component.getConfiguration().setProvider("openai");
        component.getConfiguration().setApiKey("demo");
        component.getConfiguration().setModelName("gpt-4o-mini");

        LangChain4jAgentEndpoint one = context.getEndpoint("langchain4j-agent:one", LangChain4jAgentEndpoint.class);
        LangChain4jAgentEndpoint two = context.getEndpoint("langchain4j-agent:two", LangChain4jAgentEndpoint.class);
        assertThat(one.getConfiguration().getAgentConfiguration().getChatModel())
                .isSameAs(two.getConfiguration().getAgentConfiguration().getChatModel());
    }
}
