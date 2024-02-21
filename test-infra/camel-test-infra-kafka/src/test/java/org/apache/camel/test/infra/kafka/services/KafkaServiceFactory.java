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

public final class KafkaServiceFactory {
    static class SingletonKafkaService extends SingletonService<KafkaService> implements KafkaService {
        public SingletonKafkaService(KafkaService service, String name) {
            super(service, name);
        }

        @Override
        public String getBootstrapServers() {
            return getService().getBootstrapServers();
        }
    }

    private KafkaServiceFactory() {

    }

    public static SimpleTestServiceBuilder<KafkaService> builder() {
        return new SimpleTestServiceBuilder<>("kafka");
    }

    public static KafkaService createService() {
        SimpleTestServiceBuilder<KafkaService> builder = new SimpleTestServiceBuilder<>("kafka");

        return builder.addLocalMapping(ContainerLocalKafkaService::kafka3Container)
                .addMapping("local-strimzi-container", StrimziService::new)
                .addRemoteMapping(RemoteKafkaService::new)
                .addMapping("local-kafka3-container", ContainerLocalKafkaService::kafka3Container)
                .addMapping("local-kafka2-container", ContainerLocalKafkaService::kafka2Container)
                .addMapping("local-redpanda-container", RedpandaService::new)
                .build();
    }

    public static KafkaService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final KafkaService INSTANCE;
        static {
            SimpleTestServiceBuilder<KafkaService> instance = builder();

            instance.addLocalMapping(
                    () -> new SingletonKafkaService(ContainerLocalKafkaService.kafka3Container(), "kafka"))
                    .addRemoteMapping(RemoteKafkaService::new)
                    .addMapping("local-kafka3-container",
                            () -> new SingletonKafkaService(ContainerLocalKafkaService.kafka3Container(), "kafka3"))
                    .addMapping("local-kafka2-container",
                            () -> new SingletonKafkaService(ContainerLocalKafkaService.kafka2Container(), "kafka2"))
                    .addMapping("local-strimzi-container",
                            () -> new SingletonKafkaService(new StrimziService(), "strimzi"))
                    .addMapping("local-redpanda-container",
                            () -> new SingletonKafkaService(new RedpandaService(), "redpanda"));

            INSTANCE = instance.build();
        }
    }
}
