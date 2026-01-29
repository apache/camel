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
package org.apache.camel.test.infra.ollama.services;

import org.apache.camel.test.infra.ollama.commons.OllamaProperties;

/**
 * OllamaInfraService implementation for OpenAI and OpenAI-compatible endpoints.
 *
 * Usage example:
 *
 * <pre>
 * mvn verify -Dollama.instance.type=openai \
 *            -Dopenai.api.key=sk-xxx \
 *            -Dopenai.model=gpt-4o-mini
 * </pre>
 */
public class OpenAIInfraService implements OllamaInfraService {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1/";
    private static final String DEFAULT_MODEL_NAME = "gpt-4o-mini";

    // Environment variable names
    private static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    private static final String ENV_OPENAI_BASE_URL = "OPENAI_BASE_URL";
    private static final String ENV_OPENAI_MODEL = "OPENAI_MODEL";

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP
    }

    @Override
    public String getEndpoint() {
        return baseUrl();
    }

    @Override
    public String getModel() {
        return modelName();
    }

    @Override
    public String modelName() {
        // First try openai.model system property
        String sysProp = System.getProperty(OllamaProperties.OPENAI_MODEL);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        // Then try OPENAI_MODEL environment variable
        String envVar = System.getenv(ENV_OPENAI_MODEL);
        if (envVar != null && !envVar.trim().isEmpty()) {
            return envVar;
        }
        return DEFAULT_MODEL_NAME;
    }

    @Override
    public String baseUrl() {
        // First try openai.endpoint system property
        String sysProp = System.getProperty(OllamaProperties.OPENAI_ENDPOINT);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        // Then try OPENAI_BASE_URL environment variable
        String envVar = System.getenv(ENV_OPENAI_BASE_URL);
        if (envVar != null && !envVar.trim().isEmpty()) {
            return envVar;
        }
        return DEFAULT_BASE_URL;
    }

    @Override
    public String baseUrlV1() {
        String url = baseUrl();
        // OpenAI URLs typically already end with /v1/
        if (url.endsWith("/v1/") || url.endsWith("/v1")) {
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        return url + (url.endsWith("/") ? "v1" : "/v1");
    }

    @Override
    public String apiKey() {
        // First try openai.api.key system property
        String sysProp = System.getProperty(OllamaProperties.OPENAI_API_KEY);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp;
        }
        // Then try OPENAI_API_KEY environment variable
        return System.getenv(ENV_OPENAI_API_KEY);
    }
}
