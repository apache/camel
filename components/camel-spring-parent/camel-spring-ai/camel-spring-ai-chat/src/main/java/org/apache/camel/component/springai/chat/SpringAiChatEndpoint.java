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
package org.apache.camel.component.springai.chat;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform chat operations using Spring AI.
 */
@UriEndpoint(firstVersion = "4.17.0", scheme = "spring-ai-chat", title = "Spring AI Chat",
             syntax = "spring-ai-chat:chatId", producerOnly = true, category = { Category.AI },
             headersClass = SpringAiChatConstants.class)
public class SpringAiChatEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath(description = "The ID of the chat endpoint")
    private String chatId;

    @UriParam
    private SpringAiChatConfiguration configuration;

    public SpringAiChatEndpoint(String uri, Component component, SpringAiChatConfiguration configuration, String chatId) {
        super(uri, component);
        this.configuration = configuration;
        this.chatId = chatId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringAiChatProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Spring AI Chat endpoint");
    }

    public SpringAiChatConfiguration getConfiguration() {
        return configuration;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    @Override
    public String getServiceUrl() {
        return "spring-ai-chat";
    }

    @Override
    public String getServiceProtocol() {
        return "spring-ai";
    }
}
