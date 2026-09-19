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

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-24820: the chat model is declared by its provider and options, on the endpoint or the component, without a
 * ChatModel bean.
 */
public class LangChain4jChatProviderTest extends CamelTestSupport {

    @Test
    void modelFromEndpointOptions() {
        LangChain4jChatEndpoint endpoint = context.getEndpoint(
                "langchain4j-chat:test?provider=openai&apiKey=demo&modelName=gpt-4o-mini&baseUrl=http://localhost:1/v1"
                                                               + "&temperature=0&timeout=30s&model.maxTokens=100",
                LangChain4jChatEndpoint.class);
        assertThat(endpoint.getConfiguration().getChatModel()).isInstanceOf(OpenAiChatModel.class);
        assertThat(endpoint.getConfiguration().getProvider()).isEqualTo("openai");
        assertThat(endpoint.getConfiguration().getModelProperties()).containsEntry("maxTokens", "100");
    }

    @Test
    void modelFromComponentOptionsIsSharedByEndpoints() {
        LangChain4jChatComponent component = context.getComponent("langchain4j-chat", LangChain4jChatComponent.class);
        component.getConfiguration().setProvider("openai");
        component.getConfiguration().setApiKey("demo");
        component.getConfiguration().setModelName("gpt-4o-mini");

        LangChain4jChatEndpoint one = context.getEndpoint("langchain4j-chat:one", LangChain4jChatEndpoint.class);
        LangChain4jChatEndpoint two = context.getEndpoint("langchain4j-chat:two", LangChain4jChatEndpoint.class);
        assertThat(one.getConfiguration().getChatModel()).isInstanceOf(OpenAiChatModel.class)
                .isSameAs(two.getConfiguration().getChatModel());

        // an endpoint with its own options gets its own model
        LangChain4jChatEndpoint other
                = context.getEndpoint("langchain4j-chat:three?modelName=gpt-4o", LangChain4jChatEndpoint.class);
        assertThat(other.getConfiguration().getChatModel()).isInstanceOf(OpenAiChatModel.class)
                .isNotSameAs(one.getConfiguration().getChatModel());
    }

    @Test
    void aConfiguredModelWins() {
        OpenAiChatModel model = OpenAiChatModel.builder().apiKey("demo").modelName("gpt-4o-mini").build();
        context.getRegistry().bind("myModel", model);
        LangChain4jChatEndpoint endpoint = context.getEndpoint(
                "langchain4j-chat:test?chatModel=#myModel&provider=ollama&modelName=qwen2.5", LangChain4jChatEndpoint.class);
        assertThat(endpoint.getConfiguration().getChatModel()).isSameAs(model);
    }

    @Test
    void customProviderIsAClassName() {
        LangChain4jChatEndpoint endpoint = context.getEndpoint(
                "langchain4j-chat:test?customProvider=dev.langchain4j.model.openai.OpenAiChatModel&apiKey=demo&modelName=gpt-4o",
                LangChain4jChatEndpoint.class);
        assertThat(endpoint.getConfiguration().getChatModel()).isInstanceOf(OpenAiChatModel.class);

        assertThatThrownBy(() -> context.getEndpoint(
                "langchain4j-chat:test?provider=dev.langchain4j.model.openai.OpenAiChatModel&apiKey=demo"))
                .hasMessageContaining("A class name is set as customProvider, not as provider");
    }

    @Test
    void anOptionTheProviderDoesNotHaveFailsWithWhatItAccepts() {
        assertThatThrownBy(() -> context.getEndpoint("langchain4j-chat:test?provider=openai&modelName=x&model.numPredict=1"))
                .hasMessageContaining("Cannot configure dev.langchain4j.model.openai.OpenAiChatModel of LangChain4j provider"
                                      + " openai")
                .hasMessageContaining("numPredict=1")
                .hasMessageContaining("accepts: apiKey, baseUrl");
    }
}
