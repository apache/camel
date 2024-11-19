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
package org.apache.camel.test.infra.arangodb.services;

import org.apache.camel.test.infra.common.services.ContainerTestService;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ArangoDBServiceFactory {
    private static class SingletonArangoDBService extends SingletonService<ArangoDBTestService> implements ArangoDBTestService {
        public SingletonArangoDBService(ArangoDBTestService service, String name) {
            super(service, name);
        }

        @Override
        public int getPort() {
            return getService().getPort();
        }

        @Override
        public String getHost() {
            return getService().getHost();
        }
    }

    private ArangoDBServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ArangoDBTestService> builder() {
        return new SimpleTestServiceBuilder<>("arangodb");
    }

    public static ArangoDBTestService createService() {
        return builder()
                .addLocalMapping(ArangoDBLocalContainerTestService::new)
                .addRemoteMapping(ArangoDBRemoteTestService::new)
                .build();
    }

    public static ArangoDBTestService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final ArangoDBTestService INSTANCE;
        static {
            SimpleTestServiceBuilder<ArangoDBTestService> instance = builder();
            instance.addLocalMapping(() -> new SingletonArangoDBService(new ArangoDBLocalContainerTestService(), "arangoDB"))
                    .addRemoteMapping(ArangoDBRemoteTestService::new)
                    .build();

            INSTANCE = instance.build();
        }
    }
}

class ArangoDBLocalContainerTestService extends ArangoDBLocalContainerService
        implements ArangoDBTestService, ContainerTestService {
}

class ArangoDBRemoteTestService extends ArangoDBRemoteService implements ArangoDBTestService {
}
