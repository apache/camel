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

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.time.Duration.ofSeconds;

public class OllamaTestSupport extends CamelTestSupport {

    protected ChatLanguageModel chatLanguageModel;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        chatLanguageModel = createModel();
    }

    public ChatLanguageModel createModel() {
        return OllamaChatModel.builder()
                .baseUrl(OLLAMA.getBaseUrl())
                .modelName(OLLAMA.getModel())
                .temperature(0.3)
                .timeout(ofSeconds(3000))
                .build();
    }
}
