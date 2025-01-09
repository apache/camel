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
package org.apache.camel.test.infra.milvus.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class MilvusServiceFactory {
    private MilvusServiceFactory() {

    }

    public static class SingletonMilvusService extends SingletonService<MilvusService> implements MilvusService {

        public SingletonMilvusService(MilvusService service, String name) {
            super(service, name);
        }

        @Override
        public String getMilvusEndpointUrl() {
            return getService().getMilvusEndpointUrl();
        }

        @Override
        public String getMilvusHost() {
            return getService().getMilvusHost();
        }

        @Override
        public int getMilvusPort() {
            return getService().getMilvusPort();
        }
    }

    public static SimpleTestServiceBuilder<MilvusService> builder() {
        return new SimpleTestServiceBuilder<>("milvus");
    }

    public static MilvusService createService() {
        return builder()
                .addLocalMapping(MilvusLocalContainerService::new)
                .addRemoteMapping(MilvusRemoteService::new)
                .build();
    }

    public static MilvusService createSingletonService() {
        return builder()
                .addLocalMapping(() -> new SingletonMilvusService(new MilvusLocalContainerService(), "milvus"))
                .build();
    }

    public static class MilvusLocalContainerService extends MilvusLocalContainerInfraService implements MilvusService {
    }

    public static class MilvusRemoteService extends MilvusRemoteInfraService implements MilvusService {
    }
}
