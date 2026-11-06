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
package org.apache.camel.test.infra.azure.functions.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class AzureFunctionsServiceFactory {

    private static class SingletonAzureFunctionsService extends SingletonService<AzureFunctionsService>
            implements AzureFunctionsService {
        public SingletonAzureFunctionsService(AzureFunctionsService service, String name) {
            super(service, name);
        }

        @Override
        public String resourceManagerEndpoint() {
            return getService().resourceManagerEndpoint();
        }

        @Override
        public String subscriptionId() {
            return getService().subscriptionId();
        }

        @Override
        public String resourceGroup() {
            return getService().resourceGroup();
        }

        @Override
        public String functionsEndpoint() {
            return getService().functionsEndpoint();
        }
    }

    private AzureFunctionsServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureFunctionsService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureFunctionsService createService() {
        return builder()
                .addLocalMapping(AzureFunctionsLocalContainerService::new)
                .addRemoteMapping(AzureFunctionsRemoteService::new)
                .build();
    }

    public static AzureFunctionsService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureFunctionsService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureFunctionsService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonAzureFunctionsService(new AzureFunctionsLocalContainerService(), "azure-functions"))
                    .addRemoteMapping(AzureFunctionsRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureFunctionsLocalContainerService extends AzureFunctionsLocalContainerInfraService
            implements AzureFunctionsService {
    }

    static class AzureFunctionsRemoteService extends AzureFunctionsRemoteInfraService implements AzureFunctionsService {
    }
}
