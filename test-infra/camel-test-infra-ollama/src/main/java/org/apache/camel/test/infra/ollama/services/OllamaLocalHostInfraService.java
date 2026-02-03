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

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.ollama.commons.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation that connects to a locally running Ollama instance on the host machine. This service does not
 * manage any container lifecycle and assumes Ollama is already running.
 */
public class OllamaLocalHostInfraService implements OllamaInfraService {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaLocalHostInfraService.class);
    private static final String DEFAULT_OLLAMA_HOST_URL = "http://localhost:11434";

    private static class DefaultServiceConfiguration implements OllamaServiceConfiguration {

        @Override
        public String modelName() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.MODEL);
        }

        @Override
        public String embeddingModelName() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.EMBEDDING_MODEL);
        }

        @Override
        public String apiKey() {
            return LocalPropertyResolver.getProperty(OllamaLocalContainerInfraService.class, OllamaProperties.API_KEY);
        }
    }

    private final OllamaServiceConfiguration configuration;
    private final String hostUrl;

    public OllamaLocalHostInfraService() {
        this(new DefaultServiceConfiguration());
    }

    public OllamaLocalHostInfraService(OllamaServiceConfiguration serviceConfiguration) {
        this.configuration = serviceConfiguration;
        this.hostUrl = System.getProperty(OllamaProperties.OLLAMA_HOST_URL, DEFAULT_OLLAMA_HOST_URL);
    }

    @Override
    public void registerProperties() {
        System.setProperty(OllamaProperties.ENDPOINT, hostUrl);
        LOG.info("Registered Ollama endpoint property: {}", hostUrl);
    }

    @Override
    public void initialize() {
        LOG.info("Using local Ollama instance at {}", hostUrl);
        registerProperties();
    }

    @Override
    public void shutdown() {
        // NO-OP - we don't manage the lifecycle of the local Ollama instance
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
        return configuration.modelName();
    }

    @Override
    public String embeddingModelName() {
        return configuration.embeddingModelName();
    }

    @Override
    public String baseUrl() {
        return hostUrl;
    }

    @Override
    public String baseUrlV1() {
        return hostUrl + "/v1";
    }

    @Override
    public String apiKey() {
        return configuration.apiKey();
    }

}
