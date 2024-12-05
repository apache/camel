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
package org.apache.camel.test.infra.infinispan.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class InfinispanServiceFactory {
    static class SingletonInfinispanceService extends SingletonService<InfinispanService> implements InfinispanService {

        public SingletonInfinispanceService(InfinispanService service) {
            this(service, "infinispan");
        }

        public SingletonInfinispanceService(InfinispanService service, String name) {
            super(service, name);
        }

        @Override
        public String username() {
            return getService().username();
        }

        @Override
        public String password() {
            return getService().password();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public String getServiceAddress() {
            return getService().getServiceAddress();
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

    private static class SingletonServiceHolder {
        static final InfinispanService INSTANCE;

        static {
            SimpleTestServiceBuilder<InfinispanService> serviceSimpleTestServiceBuilder
                    = new SimpleTestServiceBuilder<>("infinispan");

            serviceSimpleTestServiceBuilder
                    .addLocalMapping(() -> new SingletonInfinispanceService(new InfinispanLocalContainerService()));

            INSTANCE = serviceSimpleTestServiceBuilder.build();
        }
    }

    private InfinispanServiceFactory() {

    }

    public static SimpleTestServiceBuilder<InfinispanService> builder() {
        return new SimpleTestServiceBuilder<>("infinispan");
    }

    public static InfinispanService createService() {
        return builder()
                .addLocalMapping(InfinispanLocalContainerService::new)
                .addRemoteMapping(InfinispanRemoteService::new)
                .build();
    }

    public static InfinispanService createSingletonInfinispanService() {
        return SingletonServiceHolder.INSTANCE;
    }

    public static class InfinispanLocalContainerService extends InfinispanLocalContainerInfraService
            implements InfinispanService {
    }

    public static class InfinispanRemoteService extends InfinispanRemoteInfraService implements InfinispanService {
    }
}
