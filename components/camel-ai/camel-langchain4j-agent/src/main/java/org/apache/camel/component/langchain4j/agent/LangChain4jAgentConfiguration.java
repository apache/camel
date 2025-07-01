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

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jAgentConfiguration implements Cloneable {

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatModel chatModel;

    @UriParam(description = "Tags for discovering and calling Camel route tools")
    private String tags;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    ChatMemoryProvider chatMemoryProvider;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    RetrievalAugmentor retrievalAugmentor;

    @UriParam(description = "Comma-separated list of input guardrail class names to validate user input before sending to LLM")
    private String inputGuardrails;

    @UriParam(description = "Comma-separated list of output guardrail class names to validate LLM responses")
    private String outputGuardrails;

    public LangChain4jAgentConfiguration() {
    }

    /**
     * Chat Model of type dev.langchain4j.model.chat.ChatModel
     *
     * @return
     */
    public ChatModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Tags for discovering and calling Camel route tools
     *
     * @return the tags
     */
    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public LangChain4jAgentConfiguration copy() {
        try {
            return (LangChain4jAgentConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Chat Memory Provider of type dev.langchain4j.memory.ChatMemoryProvider. Note for this to be successful, you need
     * to use a reliable ChatMemoryStore. This provider supposes that a user has multiple sessions, if need only a
     * single session, use a default memoryId
     *
     * @return
     */
    public ChatMemoryProvider getChatMemoryProvider() {
        return chatMemoryProvider;
    }

    public void setChatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
    }

    /**
     * Retrieval Augmentor for advanced RAG of type dev.langchain4j.rag.RetrievalAugmentor. This allows using RAG on
     * both Naive and Advanced RAG
     *
     * @return the retrieval augmentor
     */
    public RetrievalAugmentor getRetrievalAugmentor() {
        return retrievalAugmentor;
    }

    public void setRetrievalAugmentor(RetrievalAugmentor retrievalAugmentor) {
        this.retrievalAugmentor = retrievalAugmentor;
    }

    /**
     * Input guardrails class names for validating user input before sending to LLM. Provide comma-separated fully
     * qualified class names that implement InputGuardrail interface.
     *
     * @return comma-separated input guardrail class names
     */
    public String getInputGuardrails() {
        return inputGuardrails;
    }

    public void setInputGuardrails(String inputGuardrails) {
        this.inputGuardrails = inputGuardrails;
    }

    /**
     * Output guardrails class names for validating LLM responses. Provide comma-separated fully qualified class names
     * that implement OutputGuardrail interface.
     *
     * @return comma-separated output guardrail class names
     */
    public String getOutputGuardrails() {
        return outputGuardrails;
    }

    public void setOutputGuardrails(String outputGuardrails) {
        this.outputGuardrails = outputGuardrails;
    }
}
