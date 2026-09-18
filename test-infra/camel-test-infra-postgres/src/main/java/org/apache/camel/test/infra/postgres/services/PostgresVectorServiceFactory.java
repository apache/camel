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
package org.apache.camel.test.infra.postgres.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class PostgresVectorServiceFactory {

    private static class SingletonPostgresVectorService extends SingletonService<PostgresService> implements PostgresService {
        public SingletonPostgresVectorService(PostgresService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
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
        public String getServiceAddress() {
            return getService().getServiceAddress();
        }
    }

    private PostgresVectorServiceFactory() {
    }

    public static SimpleTestServiceBuilder<PostgresService> builder() {
        return new SimpleTestServiceBuilder<>("postgres");
    }

    public static PostgresService createService() {
        return builder().addLocalMapping(PostgresVectorLocalContainerService::new)
                .addRemoteMapping(PostgresServiceFactory.PostgresRemoteService::new)
                .build();
    }

    public static PostgresService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final PostgresService INSTANCE;
        static {
            SimpleTestServiceBuilder<PostgresService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonPostgresVectorService(new PostgresVectorLocalContainerService(), "postgres-vector"))
                    .addRemoteMapping(PostgresServiceFactory.PostgresRemoteService::new);
            INSTANCE = instance.build();
        }
    }
}
