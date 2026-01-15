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
package org.apache.camel.example.langchain4j;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Camel routes demonstrating LangChain4j chat integration with auto-configured ChatLanguageModel.
 * 
 * The ChatLanguageModel bean is automatically configured by the LangChain4j Spring Boot starter
 * based on the properties defined in application.yml.
 */
@Component
public class ChatRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        
        // Simple chat endpoint using auto-configured ChatLanguageModel
        from("direct:chat")
            .routeId("simple-chat-route")
            .log("Sending message to LLM: ${body}")
            .to("langchain4j-chat:openai?chatModel=#chatLanguageModel")
            .log("Received response from LLM: ${body}");
        
        // Chat with prompt template
        from("direct:chat-with-template")
            .routeId("chat-with-template-route")
            .log("Processing chat with template")
            .to("langchain4j-chat:openai?chatModel=#chatLanguageModel&chatOperation=CHAT_SINGLE_MESSAGE_WITH_PROMPT")
            .log("Template response: ${body}");
        
        // REST endpoint for chat
        rest("/api/chat")
            .post("/message")
            .consumes("application/json")
            .produces("application/json")
            .to("direct:process-chat-message");
        
        from("direct:process-chat-message")
            .routeId("rest-chat-route")
            .log("Received chat request: ${body}")
            .setBody(simple("${body[message]}"))
            .to("direct:chat")
            .setBody(simple("{\"response\": \"${body}\"}"));
    }
}

