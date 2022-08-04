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

package org.apache.camel.test.infra.mongodb.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MongoDBServiceFactory {
    static class SingletonMongoDBService extends SingletonService<MongoDBService> implements MongoDBService {
        public SingletonMongoDBService(MongoDBService service, String name) {
            super(service, name);
        }

        @Override
        public void beforeAll(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) {
            // NO-OP
        }

        @Override
        public String getReplicaSetUrl() {
            return getService().getReplicaSetUrl();
        }

        @Override
        public String getConnectionAddress() {
            return getService().getConnectionAddress();
        }
    }

    private static SimpleTestServiceBuilder<MongoDBService> instance;
    private static MongoDBService service;

    private MongoDBServiceFactory() {

    }

    public static SimpleTestServiceBuilder<MongoDBService> builder() {
        return new SimpleTestServiceBuilder<>("mongodb");
    }

    public static MongoDBService createService() {
        return builder()
                .addLocalMapping(MongoDBLocalContainerService::new)
                .addRemoteMapping(MongoDBRemoteService::new)
                .build();
    }

    public static MongoDBService createSingletonService() {
        if (service == null) {
            if (instance == null) {
                instance = builder();
                instance.addLocalMapping(() -> new SingletonMongoDBService(new MongoDBLocalContainerService(), "mongo-db"))
                        .addRemoteMapping(MongoDBRemoteService::new);
            }

            service = instance.build();
        }

        return service;
    }
}
