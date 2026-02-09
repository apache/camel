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

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.apache.camel.test.infra.ollama.common.OllamaConnectionChecker;
import org.apache.camel.test.infra.ollama.commons.OllamaProperties;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OllamaServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaServiceFactory.class);
    private static final String DEFAULT_OLLAMA_HOST_URL = "http://localhost:11434";

    static class SingletonOllamaService extends SingletonService<OllamaService> implements OllamaService {
        public SingletonOllamaService(OllamaService service, String name) {
            super(service, name);
        }

        @Override
        public String getEndpoint() {
            return getService().getEndpoint();
        }

        @Override
        public String getModel() {
            return getService().getModel();
        }

        @Override
        public String modelName() {
            return getService().modelName();
        }

        @Override
        public String embeddingModelName() {
            return getService().embeddingModelName();
        }

        @Override
        public String baseUrl() {
            return getService().baseUrl();
        }

        @Override
        public String baseUrlV1() {
            return getService().baseUrlV1();
        }

        @Override
        public String apiKey() {
            return getService().apiKey();
        }

        @Override
        public final void beforeAll(ExtensionContext extensionContext) {
            super.beforeAll(extensionContext);
        }

        @Override
        public final void afterAll(ExtensionContext extensionContext) {
            // NO-OP
        }
    }

    private OllamaServiceFactory() {

    }

    public static SimpleTestServiceBuilder<OllamaService> builder() {
        return new SimpleTestServiceBuilder<>("ollama");
    }

    /**
     * Selects the appropriate local Ollama service implementation. First tries to connect to a local Ollama instance on
     * the host. If not available, falls back to starting a container.
     *
     * @return OllamaService implementation (either host or container)
     */
    private static OllamaService selectLocalService() {
        String hostUrl = System.getProperty(OllamaProperties.OLLAMA_HOST_URL, DEFAULT_OLLAMA_HOST_URL);

        if (OllamaConnectionChecker.isAvailable(hostUrl)) {
            LOG.info("Detected local Ollama instance at {}, using host service", hostUrl);
            return new OllamaLocalHostService();
        }

        LOG.info("No local Ollama instance detected at {}, starting container", hostUrl);
        return new OllamaLocalContainerService();
    }

    /**
     * Selects the appropriate local Ollama service implementation with custom configuration.
     *
     * @param  serviceConfiguration custom service configuration
     * @return                      OllamaService implementation (either host or container)
     */
    private static OllamaService selectLocalService(OllamaServiceConfiguration serviceConfiguration) {
        String hostUrl = System.getProperty(OllamaProperties.OLLAMA_HOST_URL, DEFAULT_OLLAMA_HOST_URL);

        if (OllamaConnectionChecker.isAvailable(hostUrl)) {
            LOG.info("Detected local Ollama instance at {}, using host service", hostUrl);
            return new OllamaLocalHostService(serviceConfiguration);
        }

        LOG.info("No local Ollama instance detected at {}, starting container", hostUrl);
        return new OllamaLocalContainerService(serviceConfiguration);
    }

    public static OllamaService createService() {
        return builder()
                .addLocalMapping(OllamaServiceFactory::selectLocalService)
                .addRemoteMapping(OllamaRemoteService::new)
                .addMapping("openai", OpenAIService::new)
                .build();
    }

    public static OllamaService createServiceWithConfiguration(OllamaServiceConfiguration serviceConfiguration) {
        return builder()
                .addLocalMapping(() -> selectLocalService(serviceConfiguration))
                .addRemoteMapping(() -> new OllamaRemoteService(serviceConfiguration))
                .addMapping("openai", OpenAIService::new)
                .build();
    }

    public static OllamaService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    public static OllamaService createSingletonServiceWithConfiguration(OllamaServiceConfiguration serviceConfiguration) {
        return SingletonServiceWithConfigurationHolder.getInstance(serviceConfiguration);
    }

    private static class SingletonServiceHolder {
        static final OllamaService INSTANCE;
        static {
            SimpleTestServiceBuilder<OllamaService> instance = builder();

            instance.addLocalMapping(() -> new SingletonOllamaService(selectLocalService(), "ollama"))
                    .addRemoteMapping(OllamaRemoteService::new)
                    .addMapping("openai", OpenAIService::new);

            INSTANCE = instance.build();
        }
    }

    private static class SingletonServiceWithConfigurationHolder {
        private static volatile OllamaService INSTANCE;

        static synchronized OllamaService getInstance(OllamaServiceConfiguration serviceConfiguration) {
            if (INSTANCE == null) {
                INSTANCE = new SingletonOllamaService(selectLocalService(serviceConfiguration), "ollama");
            }
            return INSTANCE;
        }
    }
}
