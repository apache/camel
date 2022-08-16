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
        public void restart() {
            getService().restart();
        }

        @Override
        public ActiveMQService getService() {
            return super.getService();
        }
    }

    private static SimpleTestServiceBuilder<ActiveMQService> instance;
    private static ActiveMQService service;

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

    public static synchronized ActiveMQService createVMService() {
        return createSingletonVMService();
    }

    public static synchronized ActiveMQService createVMServiceInstance() {
        if (service == null) {
            if (instance == null) {
                instance = new SimpleTestServiceBuilder<>("activemq");

                instance.addLocalMapping(() -> new SingletonActiveMQService(new ActiveMQVMService(), "activemq"));
            }
        }

        return instance.build();
    }

    public static synchronized ActiveMQService createSingletonVMService() {
        if (service == null) {
            service = createVMServiceInstance();
        }

        return service;
    }

    public static synchronized ActiveMQService createPersistentVMService() {
        return createSingletonPersistentVMService();
    }

    public static synchronized ActiveMQService createPersistentVMServiceInstance() {
        if (service == null) {
            if (instance == null) {
                instance = new SimpleTestServiceBuilder<>("activemq");

                instance.addLocalMapping(() -> new SingletonActiveMQService(new ActiveMQPersistentVMService(), "activemq"));
            }
        }

        return instance.build();
    }

    public static synchronized ActiveMQService createSingletonPersistentVMService() {
        if (service == null) {
            service = createVMServiceInstance();
        }

        return service;
    }
}
