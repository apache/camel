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

package org.apache.camel.test.infra.kafka.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class KafkaServiceFactory {
    static class SingletonKafkaService extends SingletonService<KafkaService> implements KafkaService {
        public SingletonKafkaService(KafkaService service, String name) {
            super(service, name);
        }

        @Override
        public String getBootstrapServers() {
            return getService().getBootstrapServers();
        }

        @Override
        public void beforeAll(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) {
            // NO-OP
        }
    }

    private static SimpleTestServiceBuilder<KafkaService> instance;
    private static KafkaService kafkaService;

    private KafkaServiceFactory() {

    }

    public static SimpleTestServiceBuilder<KafkaService> builder() {
        return new SimpleTestServiceBuilder<>("kafka");
    }

    public static KafkaService createService() {
        SimpleTestServiceBuilder<KafkaService> builder = new SimpleTestServiceBuilder<>("kafka");

        return builder.addLocalMapping(ContainerLocalKafkaService::new)
                .addMapping("local-strimzi-container", StrimziService::new)
                .addRemoteMapping(RemoteKafkaService::new)
                .addMapping("local-kafka3-container", ContainerLocalKafkaService::kafka3Container)
                .build();
    }

    public static synchronized KafkaService createSingletonService() {
        if (kafkaService == null) {
            if (instance == null) {
                instance = builder();

                instance.addLocalMapping(() -> new SingletonKafkaService(new ContainerLocalKafkaService(), "kafka"))
                        .addRemoteMapping(RemoteKafkaService::new)
                        .addMapping("local-kafka3-container",
                                () -> new SingletonKafkaService(ContainerLocalKafkaService.kafka3Container(), "kafka3"))
                        .addMapping("local-strimzi-container",
                                () -> new SingletonKafkaService(new StrimziService(), "strimzi"));

            }

            kafkaService = instance.build();
        }

        return kafkaService;
    }

}
