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
package org.apache.camel.test.infra.pulsar.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class PulsarServiceFactory {
    private PulsarServiceFactory() {

    }

    public static class SingletonPulsarService extends SingletonService<PulsarService> implements PulsarService {

        public SingletonPulsarService(PulsarService service, String name) {
            super(service, name);
        }

        @Override
        public String getPulsarAdminUrl() {
            return getService().getPulsarAdminUrl();
        }

        @Override
        public String getPulsarBrokerUrl() {
            return getService().getPulsarBrokerUrl();
        }
    }

    public static SimpleTestServiceBuilder<PulsarService> builder() {
        return new SimpleTestServiceBuilder<>("pulsar");
    }

    public static PulsarService createService() {
        return builder()
                .addLocalMapping(PulsarLocalContainerService::new)
                .addRemoteMapping(PulsarRemoteService::new)
                .build();
    }

    public static PulsarService createSingletonService() {
        return builder()
                .addLocalMapping(() -> new SingletonPulsarService(new PulsarLocalContainerService(), "pulsar"))
                .build();
    }
}
