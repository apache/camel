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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "chatOperation", value = "CHAT_SINGLE_MESSAGE_WITH_PROMPT",
                                                         carriers = Carrier.MAP),
                                          @BodyType.When(option = "chatOperation", value = "CHAT_MULTIPLE_MESSAGES",
                                                         carriers = Carrier.LIST) },
                                  description = "The prompt as text, or a UserMessage or another Spring AI Message. A"
                                                + " byte[] is sent as media (image/png unless the"
                                                + " CamelSpringAiChatMediaType header says otherwise), and a WrappedFile"
                                                + " of a local file, or a List of them, as media files: these need"
                                                + " userMessage or the CamelSpringAiChatUserMessage header for the text."
                                                + " A stream, a java.io.File or another body is sent as its text, and no"
                                                + " body fails. CHAT_SINGLE_MESSAGE_WITH_PROMPT takes the variables of"
                                                + " the template in the CamelSpringAiChatPromptTemplate header as a Map,"
                                                + " and CHAT_MULTIPLE_MESSAGES a List of Message"),
           producerOut = @BodyType(value = Carrier.TEXT,
                                   dependsOn = { "entityClass", "structuredOutputConverter" },
                                   byOption = {
                                           @BodyType.When(option = "outputFormat", value = "BEAN", carriers = Carrier.POJO),
                                           @BodyType.When(option = "outputFormat", value = "MAP", carriers = Carrier.MAP),
                                           @BodyType.When(option = "outputFormat", value = "LIST",
                                                          carriers = Carrier.LIST) },
                                   description = "The answer of the model as text, also in the CamelSpringAiChatResponse"
                                                 + " header. entityClass makes it an object of that class, and"
                                                 + " outputFormat an object of outputClass, a Map or a List of texts;"
                                                 + " the CamelSpringAiChatEntityClass and CamelSpringAiChatOutputFormat"
                                                 + " headers do the same per message"))
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
