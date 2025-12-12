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

import org.apache.camel.CamelContext;
import org.apache.camel.component.openai.OpenAIComponent;
import org.apache.camel.test.infra.ollama.services.OllamaService;
import org.apache.camel.test.infra.ollama.services.OllamaServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OpenAITestSupport extends CamelTestSupport {

    protected String apiKey;
    protected String baseUrl;
    protected String model;

    @RegisterExtension
    static OllamaService OLLAMA = OllamaServiceFactory.createSingletonService();

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        baseUrl = OLLAMA.baseUrlV1();
        model = OLLAMA.modelName();
        apiKey = OLLAMA.apiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "dummy";
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        OpenAIComponent component = new OpenAIComponent();
        if (ObjectHelper.isNotEmpty(apiKey)) {
            component.setApiKey(apiKey);
        }

        if (ObjectHelper.isNotEmpty(model)) {
            component.setModel(model);
        }

        if (ObjectHelper.isNotEmpty(baseUrl)) {
            component.setBaseUrl(baseUrl);
        }

        camelContext.addComponent("openai", component);
        return camelContext;
    }

}
