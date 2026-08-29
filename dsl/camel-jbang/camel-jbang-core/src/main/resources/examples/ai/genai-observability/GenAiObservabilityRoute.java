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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

import org.apache.camel.builder.RouteBuilder;

import static java.time.Duration.ofSeconds;

/**
 * Minimal GenAI route for observing LLM calls with Camel 4.23+.
 * <p>
 * Requires Ollama running locally (see README.md).
 */
public class GenAiObservabilityRoute extends RouteBuilder {

    @Override
    public void configure() {
        ChatModel chatModel = OllamaChatModel.builder()
                .baseUrl("{{ollama.baseUrl:http://localhost:11434}}")
                .modelName("{{ollama.model:llama3.2}}")
                .temperature(0.2)
                .timeout(ofSeconds(120))
                .build();
        getContext().getRegistry().bind("chatModel", chatModel);

        from("timer:genai?period={{genai.period:15000}}")
                .routeId("genai-chat")
                .setBody(constant("In one sentence, what is Apache Camel integration?"))
                .to("langchain4j-chat:demo?chatModel=#chatModel")
                .log("LLM reply: ${body}")
                .log("Request model: ${header.CamelLangChain4jChatRequestModel}")
                .log("Response model: ${header.CamelLangChain4jChatResponseModel}");
    }
}
