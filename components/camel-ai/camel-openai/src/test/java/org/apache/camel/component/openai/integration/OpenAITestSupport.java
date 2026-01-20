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
package org.apache.camel.component.openai.integration;

import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;

public class OpenAITestSupport extends CamelTestSupport {

    protected String apiKey;
    protected String baseUrl;
    protected String model;

    static OllamaService OLLAMA = hasEnvironmentConfiguration()
            ? null
            : OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        if (OLLAMA != null) {
            // Use Ollama service
            baseUrl = OLLAMA.baseUrlV1();
            model = OLLAMA.modelName();
            apiKey = "dummy"; // Ollama doesn't require API key
        } else {
            // Use environment variables
            apiKey = System.getenv("OPENAI_API_KEY");
            baseUrl = System.getenv("OPENAI_BASE_URL"); // Optional
            model = System.getenv("OPENAI_MODEL"); // Optional
        }
    }

    protected static boolean hasEnvironmentConfiguration() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
