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
package org.apache.camel.test.infra.activemq.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ActiveMQServiceFactory {

    public static class SingletonActiveMQService extends SingletonService<ActiveMQService> implements ActiveMQService {
        public SingletonActiveMQService(ActiveMQService service, String name) {
            super(service, name);
        }

        @Override
        public String serviceAddress() {
            return getService().serviceAddress();
        }

        @Override
        public String userName() {
            return getService().userName();
        }

        @Override
        public String password() {
            return getService().password();
        }

        @Override
        public void beforeAll(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) {
            // NO-OP
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            // NO-OP
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }

        @Override
        public void restart() {
            getService().restart();
        }

        @Override
        public ActiveMQService getService() {
            return super.getService();
        }
    }

    private static SimpleTestServiceBuilder<ActiveMQService> nonPersistentInstanceBuilder;
    private static ActiveMQService nonPersistentService;

    private static SimpleTestServiceBuilder<ActiveMQService> persistentInstanceBuilder;
    private static ActiveMQService persistentService;

    private ActiveMQServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ActiveMQService> builder() {
        return new SimpleTestServiceBuilder<>("activemq");
    }

    public static ActiveMQService createService() {
        return builder()
                .addLocalMapping(ActiveMQEmbeddedService::new)
                .addRemoteMapping(ActiveMQRemoteService::new)
                .build();
    }

    /**
     * Creates a new instance of an embedded ActiveMQ
     * 
     * @return a new instance of an embedded ActiveMQ
     */
    public static synchronized ActiveMQService createVMService() {
        return createSingletonVMService();
    }

    /**
     * Creates a new instance of an embedded ActiveMQ. It may use a single instance if possible/supported.
     * 
     * @return a new instance of an embedded ActiveMQ
     */
    public static synchronized ActiveMQService createVMServiceInstance() {
        SimpleTestServiceBuilder<ActiveMQService> instance = new SimpleTestServiceBuilder<>("activemq");
        instance.addLocalMapping(ActiveMQVMService::new);

        return instance.build();
    }

    /**
     * Creates or reuses a new singleton instance of an embedded ActiveMQ
     * 
     * @return an instance of an embedded ActiveMQ
     */
    public static synchronized ActiveMQService createSingletonVMService() {
        if (nonPersistentService == null) {
            if (nonPersistentInstanceBuilder == null) {
                nonPersistentInstanceBuilder = new SimpleTestServiceBuilder<>("activemq");

                nonPersistentInstanceBuilder
                        .addLocalMapping(() -> new SingletonActiveMQService(new ActiveMQVMService(), "activemq"));
            }

            nonPersistentService = nonPersistentInstanceBuilder.build();
        }

        return nonPersistentService;
    }

    /**
     * Creates a new instance of a persistent embedded ActiveMQ. It may use a single instance if possible/supported.
     * 
     * @return a new instance of a persistent embedded ActiveMQ
     */
    public static synchronized ActiveMQService createPersistentVMService() {
        return createSingletonPersistentVMService();
    }

    /**
     * Creates a new instance of a persistent embedded ActiveMQ
     * 
     * @return a new instance of a persistent embedded ActiveMQ
     */
    public static synchronized ActiveMQService createPersistentVMServiceInstance() {
        SimpleTestServiceBuilder<ActiveMQService> instance = new SimpleTestServiceBuilder<>("activemq");

        instance.addLocalMapping(ActiveMQPersistentVMService::new);

        return instance.build();
    }

    /**
     * Creates or reuses a new singleton instance of a persistent embedded ActiveMQ
     * 
     * @return an instance of a persistent embedded ActiveMQ
     */
    public static synchronized ActiveMQService createSingletonPersistentVMService() {
        if (persistentService == null) {
            if (persistentInstanceBuilder == null) {
                persistentInstanceBuilder = new SimpleTestServiceBuilder<>("activemq");

                persistentInstanceBuilder
                        .addLocalMapping(
                                () -> new SingletonActiveMQService(new ActiveMQPersistentVMService(), "activemq-persistent"));
            }

            persistentService = persistentInstanceBuilder.build();
        }

        return persistentService;
    }
}
