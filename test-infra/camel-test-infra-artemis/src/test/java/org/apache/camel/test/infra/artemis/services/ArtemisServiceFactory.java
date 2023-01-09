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

    private static SimpleTestServiceBuilder<ArtemisService> nonPersistentInstanceBuilder;
    private static SimpleTestServiceBuilder<ArtemisService> persistentInstanceBuilder;
    private static SimpleTestServiceBuilder<ArtemisService> amqpInstanceBuilder;

    private static ArtemisService persistentService;
    private static ArtemisService nonPersistentService;
    private static ArtemisService amqpService;

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
        public void beforeAll(ExtensionContext extensionContext) throws Exception {
            addToStore(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) throws Exception {
            // NO-OP
        }

        @Override
        public void afterEach(ExtensionContext extensionContext) throws Exception {
            // NO-OP
        }

        @Override
        public void beforeEach(ExtensionContext extensionContext) throws Exception {
            addToStore(extensionContext);
        }
    }

    private ArtemisServiceFactory() {

    }

    public static synchronized ArtemisService createVMService() {
        return createSingletonVMService();
    }

    public static synchronized ArtemisService createPersistentVMService() {
        return createSingletonPersistentVMService();
    }

    public static ArtemisService createAMQPService() {
        return new ArtemisAMQPService();
    }

    public static synchronized ArtemisService createSingletonVMService() {
        if (nonPersistentService == null) {
            if (nonPersistentInstanceBuilder == null) {
                nonPersistentInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

                nonPersistentInstanceBuilder
                        .addLocalMapping(() -> new SingletonArtemisService(new ArtemisVMService(), "artemis"));
            }

            nonPersistentService = nonPersistentInstanceBuilder.build();
        }

        return nonPersistentService;
    }

    public static synchronized ArtemisService createSingletonPersistentVMService() {
        if (persistentService == null) {
            if (persistentInstanceBuilder == null) {
                persistentInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

                persistentInstanceBuilder.addLocalMapping(
                        () -> new SingletonArtemisService(new ArtemisPersistentVMService(), "artemis-persistent"));
            }

            persistentService = persistentInstanceBuilder.build();
        }

        return persistentService;
    }

    public static synchronized ArtemisService createSingletonAMQPService() {
        if (amqpService == null) {
            if (amqpInstanceBuilder == null) {
                amqpInstanceBuilder = new SimpleTestServiceBuilder<>("artemis");

                amqpInstanceBuilder
                        .addLocalMapping(() -> new SingletonArtemisService(new ArtemisAMQPService(), "artemis-amqp"));
            }

            amqpService = amqpInstanceBuilder.build();
        }

        return amqpService;
    }

    public static ArtemisService createTCPAllProtocolsService() {
        return new ArtemisTCPAllProtocolsService();
    }
}
