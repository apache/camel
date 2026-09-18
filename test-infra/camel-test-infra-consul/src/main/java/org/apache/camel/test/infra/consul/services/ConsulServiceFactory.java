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
package org.apache.camel.test.infra.consul.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ConsulServiceFactory {

    private static class SingletonConsulService extends SingletonService<ConsulService> implements ConsulService {
        public SingletonConsulService(ConsulService service, String name) {
            super(service, name);
        }

        @Override
        public String getConsulUrl() {
            return getService().getConsulUrl();
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }
    }

    private ConsulServiceFactory() {
    }

    public static SimpleTestServiceBuilder<ConsulService> builder() {
        return new SimpleTestServiceBuilder<>("consul");
    }

    public static ConsulService createService() {
        return builder()
                .addLocalMapping(ConsulLocalContainerTestService::new)
                .addRemoteMapping(ConsulRemoteTestService::new)
                .build();
    }

    public static ConsulService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final ConsulService INSTANCE;
        static {
            SimpleTestServiceBuilder<ConsulService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonConsulService(new ConsulLocalContainerTestService(), "consul"))
                    .addRemoteMapping(ConsulRemoteTestService::new);
            INSTANCE = instance.build();
        }
    }

    public static class ConsulLocalContainerTestService extends ConsulLocalContainerInfraService implements ConsulService {
    }

    public static class ConsulRemoteTestService extends ConsulRemoteInfraService implements ConsulService {
    }
}
