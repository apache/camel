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

package org.apache.camel.test.infra.azure.eventhubs.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Event Hubs service: the local one, unless the {@code azure.instance.type} system property is
 * {@code remote}.
 */
public final class AzureEventHubsServiceFactory {

    private static class SingletonAzureEventHubsService extends SingletonService<AzureEventHubsService>
            implements AzureEventHubsService {
        public SingletonAzureEventHubsService(AzureEventHubsService service, String name) {
            super(service, name);
        }

        @Override
        public String connectionString() {
            return getService().connectionString();
        }

        @Override
        public String blobEndpoint() {
            return getService().blobEndpoint();
        }

        @Override
        public String blobAccountName() {
            return getService().blobAccountName();
        }

        @Override
        public String blobAccessKey() {
            return getService().blobAccessKey();
        }
    }

    private AzureEventHubsServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureEventHubsService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureEventHubsService createService() {
        return builder()
                .addLocalMapping(AzureEventHubsLocalContainerService::new)
                .addRemoteMapping(AzureEventHubsRemoteService::new)
                .build();
    }

    public static AzureEventHubsService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureEventHubsService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureEventHubsService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonAzureEventHubsService(new AzureEventHubsLocalContainerService(), "azure-eventhubs"))
                    .addRemoteMapping(AzureEventHubsRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureEventHubsLocalContainerService extends AzureEventHubsLocalContainerInfraService
            implements AzureEventHubsService {
    }

    static class AzureEventHubsRemoteService extends AzureEventHubsRemoteInfraService implements AzureEventHubsService {
    }
}
