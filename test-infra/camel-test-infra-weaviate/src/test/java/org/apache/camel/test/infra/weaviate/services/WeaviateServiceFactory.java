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
package org.apache.camel.test.infra.weaviate.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class WeaviateServiceFactory {
    private WeaviateServiceFactory() {

    }

    public static class SingletonWeaviateService extends SingletonService<WeaviateService> implements WeaviateService {

        public SingletonWeaviateService(WeaviateService service, String name) {
            super(service, name);
        }

        @Override
        public String getWeaviateEndpointUrl() {
            return getService().getWeaviateEndpointUrl();
        }

        @Override
        public String getWeaviateHost() {
            return getService().getWeaviateHost();
        }

        @Override
        public int getWeaviatePort() {
            return getService().getWeaviatePort();
        }
    }

    public static SimpleTestServiceBuilder<WeaviateService> builder() {
        return new SimpleTestServiceBuilder<>("weaviate");
    }

    public static WeaviateService createService() {
        return builder()
                .addLocalMapping(WeaviateLocalContainerService::new)
                .addRemoteMapping(WeaviateRemoteService::new)
                .build();
    }

    public static WeaviateService createSingletonService() {
        return builder()
                .addLocalMapping(() -> new SingletonWeaviateService(new WeaviateLocalContainerService(), "weaviate"))
                .build();
    }

    public static class WeaviateLocalContainerService extends WeaviateLocalContainerInfraService implements WeaviateService {
    }

    public static class WeaviateRemoteService extends WeaviateRemoteInfraService implements WeaviateService {
    }
}
