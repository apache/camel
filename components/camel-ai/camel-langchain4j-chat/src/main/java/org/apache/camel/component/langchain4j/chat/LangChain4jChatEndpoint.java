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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

import static org.apache.camel.component.langchain4j.chat.LangChain4jChat.SCHEME;

@UriEndpoint(firstVersion = "4.5.0", scheme = SCHEME,
             title = "LangChain4j Chat",
             syntax = "langchain4j-chat:chatId",
             category = { Category.AI }, headersClass = LangChain4jChat.Headers.class)
public class LangChain4jChatEndpoint extends DefaultEndpoint {

    @Metadata(required = true)
    @UriPath(description = "The id")
    private final String chatId;

    @UriParam
    private LangChain4jChatConfiguration configuration;

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
        throw new UnsupportedOperationException("Consumer not supported");
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

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
}
