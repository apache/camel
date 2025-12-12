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
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.infra.ollama.services.OpenAIService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.time.Duration.ofSeconds;

public class OllamaTestSupport extends CamelTestSupport {

    protected ChatModel chatModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatModel = createModel();
    }

    protected ChatModel createModel() {
        if (OLLAMA instanceof OpenAIService) {
            return OpenAiChatModel.builder()
                    .apiKey(OLLAMA.apiKey())
                    .baseUrl(OLLAMA.baseUrl())
                    .modelName(OLLAMA.modelName())
                    .temperature(0.3)
                    .timeout(ofSeconds(60))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        }
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA.baseUrl())
                .modelName(OLLAMA.modelName())
                .temperature(0.3)
                .timeout(ofSeconds(60))
                .build();
    }
}
