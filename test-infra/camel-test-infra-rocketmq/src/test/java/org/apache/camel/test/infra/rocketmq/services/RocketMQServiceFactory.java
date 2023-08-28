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
package org.apache.camel.test.infra.rocketmq.services;

import java.io.IOException;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class RocketMQServiceFactory {

    static class SingletonKRocketMQService extends SingletonService<RocketMQService> implements RocketMQService {
        public SingletonKRocketMQService(RocketMQService service, String name) {
            super(service, name);
        }

        @Override
        public String nameserverAddress() {
            return getService().nameserverAddress();
        }

        @Override
        public void createTopic(String topic) {
            getService().createTopic(topic);
        }

        @Override
        public void deleteTopic(String topic) throws IOException, InterruptedException {
            getService().deleteTopic(topic);
        }
    }

    private RocketMQServiceFactory() {

    }

    public static SimpleTestServiceBuilder<RocketMQService> builder() {
        return new SimpleTestServiceBuilder<>("rocketmq");
    }

    public static RocketMQService createService() {
        return builder()
                .addLocalMapping(RocketMQContainer::new)
                .build();
    }

    public static RocketMQService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final RocketMQService INSTANCE;

        static {
            SimpleTestServiceBuilder<RocketMQService> instance = builder();

            instance.addLocalMapping(() -> new SingletonKRocketMQService(new RocketMQContainer(), "rocketmq"));

            INSTANCE = instance.build();
        }
    }
}
