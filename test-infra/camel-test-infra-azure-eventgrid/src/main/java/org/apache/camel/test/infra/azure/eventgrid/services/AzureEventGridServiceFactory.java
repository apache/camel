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

package org.apache.camel.test.infra.azure.eventgrid.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * Creates the Event Grid service: a local one by default, a remote one with {@code azure.instance.type=remote}
 */
public final class AzureEventGridServiceFactory {

    private static class SingletonAzureEventGridService extends SingletonService<AzureEventGridService>
            implements AzureEventGridService {
        public SingletonAzureEventGridService(AzureEventGridService service, String name) {
            super(service, name);
        }

        @Override
        public String topicEndpoint() {
            return getService().topicEndpoint();
        }

        @Override
        public String topicAccessKey() {
            return getService().topicAccessKey();
        }

        @Override
        public String topicId() {
            return getService().topicId();
        }

        @Override
        public String resourceManagerEndpoint() {
            return getService().resourceManagerEndpoint();
        }

        @Override
        public String trustStore() {
            return getService().trustStore();
        }

        @Override
        public String trustStorePassword() {
            return getService().trustStorePassword();
        }
    }

    private AzureEventGridServiceFactory() {

    }

    public static SimpleTestServiceBuilder<AzureEventGridService> builder() {
        return new SimpleTestServiceBuilder<>("azure");
    }

    public static AzureEventGridService createService() {
        return builder()
                .addLocalMapping(AzureEventGridLocalContainerService::new)
                .addRemoteMapping(AzureEventGridRemoteService::new)
                .build();
    }

    public static AzureEventGridService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final AzureEventGridService INSTANCE;
        static {
            SimpleTestServiceBuilder<AzureEventGridService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonAzureEventGridService(new AzureEventGridLocalContainerService(), "azure-eventgrid"))
                    .addRemoteMapping(AzureEventGridRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    static class AzureEventGridLocalContainerService extends AzureEventGridLocalContainerInfraService
            implements AzureEventGridService {
    }

    static class AzureEventGridRemoteService extends AzureEventGridRemoteInfraService implements AzureEventGridService {
    }
}
