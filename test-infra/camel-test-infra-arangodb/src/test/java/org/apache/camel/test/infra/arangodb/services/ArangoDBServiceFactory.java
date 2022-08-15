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

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ArangoDBServiceFactory {
    private static class SingletonArangoDBService extends SingletonService<ArangoDBService> implements ArangoDBService {
        public SingletonArangoDBService(ArangoDBService service, String name) {
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
        public int getPort() {
            return getService().getPort();
        }

        @Override
        public String getHost() {
            return getService().getHost();
        }
    }

    private static SimpleTestServiceBuilder<ArangoDBService> instance;
    private static ArangoDBService arangoDBService;

    private ArangoDBServiceFactory() {

    }

    public static SimpleTestServiceBuilder<ArangoDBService> builder() {
        return new SimpleTestServiceBuilder<>("arangodb");
    }

    public static ArangoDBService createService() {
        return builder()
                .addLocalMapping(ArangoDBLocalContainerService::new)
                .addRemoteMapping(ArangoDBRemoteService::new)
                .build();
    }

    public static ArangoDBService createSingletonService() {
        if (arangoDBService == null) {

            if (instance == null) {
                instance = builder();

                instance.addLocalMapping(() -> new SingletonArangoDBService(new ArangoDBLocalContainerService(), "arangoDB"))
                        .addRemoteMapping(ArangoDBRemoteService::new)
                        .build();
            }

            arangoDBService = instance.build();
        }

        return arangoDBService;
    }
}
