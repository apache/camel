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

package org.apache.camel.test.infra.azure.servicebus.services;

import org.apache.camel.test.infra.common.services.ContainerTestService;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Service Bus service: the local one by default, the remote one with {@code azure.instance.type=remote}.
 * Only the local service needs Docker, and skips the tests where Docker is not available.
 */
public final class AzureServiceBusServiceFactory {

    private static class SingletonAzureServiceBusService extends SingletonService<AzureServiceBusService>
            implements AzureServiceBusService, ContainerTestService {
        public SingletonAzureServiceBusService(AzureServiceBusService service, String name) {
            super(service, name);
        }

        @Override
        public String connectionString() {
            return getService().connectionString();
        }
    }

    private AzureServiceBusServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureServiceBusService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureServiceBusService createService() {
        return builder()
                .addLocalMapping(AzureServiceBusLocalContainerService::new)
                .addRemoteMapping(AzureServiceBusRemoteService::new)
                .build();
    }

    public static AzureServiceBusService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureServiceBusService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureServiceBusService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonAzureServiceBusService(new AzureServiceBusLocalContainerService(), "azure-servicebus"))
                    .addRemoteMapping(AzureServiceBusRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureServiceBusLocalContainerService extends AzureServiceBusLocalContainerInfraService
            implements AzureServiceBusService, ContainerTestService {
    }

    static class AzureServiceBusRemoteService extends AzureServiceBusRemoteInfraService implements AzureServiceBusService {
    }
}
