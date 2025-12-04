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

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@Configurer
@UriParams
public class LangChain4jChatConfiguration implements Cloneable {

    @UriParam
    @Metadata(required = true, defaultValue = "CHAT_SINGLE_MESSAGE")
    private LangChain4jChatOperations chatOperation = LangChain4jChatOperations.CHAT_SINGLE_MESSAGE;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ChatModel chatModel;

    public LangChain4jChatConfiguration() {}

    /**
     * Operation in case of Endpoint of type CHAT. The value is one of the values of
     * org.apache.camel.component.langchain4j.chat.LangChain4jChatOperations
     *
     * @return
     */
    public LangChain4jChatOperations getChatOperation() {
        return chatOperation;
    }

    public void setChatOperation(LangChain4jChatOperations chatOperation) {
        this.chatOperation = chatOperation;
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

    public LangChain4jChatConfiguration copy() {
        try {
            return (LangChain4jChatConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
