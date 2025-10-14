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
import org.junit.jupiter.api.extension.ExtensionContext;

public final class OllamaServiceFactory {

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

    public static OllamaService createService() {
        return builder()
                .addLocalMapping(OllamaLocalContainerService::new)
                .addRemoteMapping(OllamaRemoteService::new)
                .build();
    }

    public static OllamaService createServiceWithConfiguration(OllamaServiceConfiguration serviceConfiguration) {
        return builder()
                .addLocalMapping(() -> new OllamaLocalContainerService(serviceConfiguration))
                .addRemoteMapping(() -> new OllamaRemoteService(serviceConfiguration))
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

            instance.addLocalMapping(() -> new SingletonOllamaService(new OllamaLocalContainerService(), "ollama"))
                    .addRemoteMapping(OllamaRemoteService::new);

            INSTANCE = instance.build();
        }
    }

    private static class SingletonServiceWithConfigurationHolder {
        private static volatile OllamaService INSTANCE;

        static synchronized OllamaService getInstance(OllamaServiceConfiguration serviceConfiguration) {
            if (INSTANCE == null) {
                INSTANCE = new SingletonOllamaService(new OllamaLocalContainerService(serviceConfiguration), "ollama");
            }
            return INSTANCE;
        }
    }
}
