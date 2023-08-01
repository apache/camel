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
package org.apache.camel.test.infra.artemis.services;

import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ArtemisServiceFactory {

    public static class SingletonArtemisService extends SingletonService<ArtemisService> implements ArtemisService {

        public SingletonArtemisService(ArtemisService service, String name) {
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
        public int brokerPort() {
            return getService().brokerPort();
        }

        @Override
        public void restart() {
            getService().restart();
        }

        @Override
        public long countMessages(String queue) throws Exception {
            return getService().countMessages(queue);
        }

        @Override
        public QueueQueryResult getQueueQueryResult(String queueQuery) throws Exception {
            return getService().getQueueQueryResult(queueQuery);
        }

        @Override
        public ArtemisService getService() {
            return super.getService();
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) {
            // NO-OP
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }
    }

    private ArtemisServiceFactory() {

    }

    public static synchronized ArtemisService createVMService() {
        return new ArtemisVMService();
    }

    public static synchronized ArtemisService createPersistentVMService() {
        return new ArtemisPersistentVMService();
    }

    public static ArtemisService createAMQPService() {
        return new ArtemisAMQPService();
    }

    public static ArtemisService createSingletonVMService() {
        return SingletonVMServiceHolder.INSTANCE;
    }

    public static ArtemisService createSingletonPersistentVMService() {
        return SingletonPersistentVMServiceHolder.INSTANCE;
    }

    public static ArtemisService createSingletonAMQPService() {
        return SingletonAMQPServiceHolder.INSTANCE;
    }

    public static ArtemisService createSingletonMQTTService() {
        return SingletonMQTTServiceHolder.INSTANCE;
    }

    public static ArtemisService createTCPAllProtocolsService() {
        return new ArtemisTCPAllProtocolsService();
    }

    private static class SingletonVMServiceHolder {
        static final ArtemisService INSTANCE;
        static {
            SimpleTestServiceBuilder<ArtemisService> nonPersistentInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

            nonPersistentInstanceBuilder
                    .addLocalMapping(() -> new SingletonArtemisService(new ArtemisVMService(), "artemis"));

            INSTANCE = nonPersistentInstanceBuilder.build();
        }
    }

    private static class SingletonPersistentVMServiceHolder {
        static final ArtemisService INSTANCE;
        static {
            SimpleTestServiceBuilder<ArtemisService> persistentInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

            persistentInstanceBuilder.addLocalMapping(
                    () -> new SingletonArtemisService(new ArtemisPersistentVMService(), "artemis-persistent"));

            INSTANCE = persistentInstanceBuilder.build();
        }
    }

    private static class SingletonAMQPServiceHolder {
        static final ArtemisService INSTANCE;
        static {
            SimpleTestServiceBuilder<ArtemisService> amqpInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

            amqpInstanceBuilder
                    .addLocalMapping(() -> new SingletonArtemisService(new ArtemisAMQPService(), "artemis-amqp"));

            INSTANCE = amqpInstanceBuilder.build();
        }
    }

    private static class SingletonMQTTServiceHolder {
        static final ArtemisService INSTANCE;
        static {
            SimpleTestServiceBuilder<ArtemisService> mqttInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

            mqttInstanceBuilder
                    .addLocalMapping(() -> new SingletonArtemisService(new ArtemisMQTTService(), "artemis-mqtt"));

            INSTANCE = mqttInstanceBuilder.build();
        }
    }
}
