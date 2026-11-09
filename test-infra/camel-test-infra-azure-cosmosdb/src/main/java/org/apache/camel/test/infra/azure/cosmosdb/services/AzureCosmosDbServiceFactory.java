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

package org.apache.camel.test.infra.azure.cosmosdb.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class AzureCosmosDbServiceFactory {

    private static class SingletonAzureCosmosDbService extends SingletonService<AzureCosmosDbService>
            implements AzureCosmosDbService {
        public SingletonAzureCosmosDbService(AzureCosmosDbService service, String name) {
            super(service, name);
        }

        @Override
        public String endpoint() {
            return getService().endpoint();
        }

        @Override
        public String accessKey() {
            return getService().accessKey();
        }
    }

    private AzureCosmosDbServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureCosmosDbService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureCosmosDbService createService() {
        return builder()
                .addLocalMapping(AzureCosmosDbLocalContainerService::new)
                .addRemoteMapping(AzureCosmosDbRemoteService::new)
                .build();
    }

    public static AzureCosmosDbService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureCosmosDbService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureCosmosDbService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonAzureCosmosDbService(new AzureCosmosDbLocalContainerService(), "azure-cosmos-db"))
                    .addRemoteMapping(AzureCosmosDbRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureCosmosDbLocalContainerService extends AzureCosmosDbLocalContainerInfraService
            implements AzureCosmosDbService {
    }

    static class AzureCosmosDbRemoteService extends AzureCosmosDbRemoteInfraService implements AzureCosmosDbService {
    }
}
