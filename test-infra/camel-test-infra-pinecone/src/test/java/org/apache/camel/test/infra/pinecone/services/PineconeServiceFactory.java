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
package org.apache.camel.test.infra.pinecone.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class PineconeServiceFactory {
    private PineconeServiceFactory() {

    }

    public static class SingletonPineconeService extends SingletonService<PineconeService> implements PineconeService {

        public SingletonPineconeService(PineconeService service, String name) {
            super(service, name);
        }

        @Override
        public String getPineconeEndpointUrl() {
            return getService().getPineconeEndpointUrl();
        }

        @Override
        public String getPineconeHost() {
            return getService().getPineconeHost();
        }

        @Override
        public int getPineconePort() {
            return getService().getPineconePort();
        }
    }

    public static SimpleTestServiceBuilder<PineconeService> builder() {
        return new SimpleTestServiceBuilder<>("pinecone");
    }

    public static PineconeService createService() {
        return builder()
                .addLocalMapping(PineconeLocalContainerService::new)
                .addRemoteMapping(PineconeRemoteService::new)
                .build();
    }

    public static PineconeService createSingletonService() {
        return builder()
                .addLocalMapping(() -> new SingletonPineconeService(new PineconeLocalContainerService(), "pinecone"))
                .build();
    }

    public static class PineconeLocalContainerService extends PineconeLocalContainerInfraService implements PineconeService {
    }

    public static class PineconeRemoteService extends PineconeRemoteInfraService implements PineconeService {
    }
}
