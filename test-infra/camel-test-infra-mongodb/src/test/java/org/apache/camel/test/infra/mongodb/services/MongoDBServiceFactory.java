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

public final class MongoDBServiceFactory {
    static class SingletonMongoDBService extends SingletonService<MongoDBService> implements MongoDBService {
        public SingletonMongoDBService(MongoDBService service, String name) {
            super(service, name);
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
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final MongoDBService INSTANCE;
        static {
            SimpleTestServiceBuilder<MongoDBService> instance = builder();
            instance.addLocalMapping(() -> new SingletonMongoDBService(new MongoDBLocalContainerService(), "mongo-db"))
                    .addRemoteMapping(MongoDBRemoteService::new);

            INSTANCE = instance.build();
        }
    }
}
