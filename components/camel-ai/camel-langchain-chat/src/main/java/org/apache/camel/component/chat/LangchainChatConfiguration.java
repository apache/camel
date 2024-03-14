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
package org.apache.camel.component.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangchainChatConfiguration implements Cloneable {

    @UriParam
    @Metadata(required = true, defaultValue = "CHAT_SINGLE_MESSAGE")
    private LangchainChatOperations chatOperation = LangchainChatOperations.CHAT_SINGLE_MESSAGE;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatLanguageModel chatModel;

    public LangchainChatConfiguration() {
    }

    /**
     * Operation in case of Endpoint of type CHAT. value is one the values of
     * org.apache.camel.component.langchain.LangchainChatOperations
     *
     * @return
     */
    public LangchainChatOperations getChatOperation() {
        return chatOperation;
    }

    public void setChatOperation(LangchainChatOperations chatOperation) {
        this.chatOperation = chatOperation;
    }

    /**
     * Chat Language Model of type dev.langchain4j.model.chat.ChatLanguageModel
     *
     * @return
     */
    public ChatLanguageModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    public LangchainChatConfiguration copy() {
        try {
            return (LangchainChatConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
