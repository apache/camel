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
package org.apache.camel.test.infra.duckdb.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class DuckDbServiceFactory {

    public static class SingletonDuckDbService extends SingletonService<DuckDbService> implements DuckDbService {
        public SingletonDuckDbService(DuckDbService service, String name) {
            super(service, name);
        }

        @Override
        public String getJdbcUrl() {
            return getService().getJdbcUrl();
        }

        @Override
        public String getDatabasePath() {
            return getService().getDatabasePath();
        }

        @Override
        public DuckDbService getService() {
            return super.getService();
        }
    }

    private DuckDbServiceFactory() {
    }

    public static SimpleTestServiceBuilder<DuckDbService> builder() {
        return new SimpleTestServiceBuilder<>("duckdb");
    }

    public static DuckDbService createService() {
        return builder()
                .addLocalMapping(DuckDbEmbeddedService::new)
                .addRemoteMapping(DuckDbRemoteService::new)
                .build();
    }

    public static DuckDbService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final DuckDbService INSTANCE;
        static {
            SimpleTestServiceBuilder<DuckDbService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonDuckDbService(new DuckDbEmbeddedService(), "duckdb"))
                    .addRemoteMapping(DuckDbRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class DuckDbRemoteService extends RemoteDuckDbInfraService implements DuckDbService {
    }
}
