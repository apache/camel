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

package org.apache.camel.test.infra.rabbitmq.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class RabbitMQServiceFactory {

    private static class SingletonRabbitMQService extends SingletonService<RabbitMQService> implements RabbitMQService {
        public SingletonRabbitMQService(RabbitMQService service, String name) {
            super(service, name);
        }

        @Override
        public ConnectionProperties connectionProperties() {
            return getService().connectionProperties();
        }

        @Override
        public int getHttpPort() {
            return getService().getHttpPort();
        }

        @Override
        public String managementUsername() {
            return getService().managementUsername();
        }

        @Override
        public String managementPassword() {
            return getService().managementPassword();
        }

        @Override
        public String managementUri() {
            return getService().managementUri();
        }
    }

    private RabbitMQServiceFactory() {

    }

    public static SimpleTestServiceBuilder<RabbitMQService> builder() {
        return new SimpleTestServiceBuilder<>("rabbitmq");
    }

    public static RabbitMQService createService() {
        return builder()
                .addLocalMapping(RabbitMQLocalContainerService::new)
                .addRemoteMapping(RabbitMQRemoteService::new)
                .build();
    }

    public static RabbitMQService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final RabbitMQService INSTANCE;
        static {
            SimpleTestServiceBuilder<RabbitMQService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonRabbitMQService(new RabbitMQLocalContainerService(), "rabbitmq"))
                    .addRemoteMapping(RabbitMQRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class RabbitMQLocalContainerService extends RabbitMQLocalContainerInfraService implements RabbitMQService {
    }

    public static class RabbitMQRemoteService extends RabbitMQRemoteInfraService implements RabbitMQService {
    }
}
