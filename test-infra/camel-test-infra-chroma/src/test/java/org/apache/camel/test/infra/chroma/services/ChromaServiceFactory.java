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
package org.apache.camel.test.infra.chroma.services;

import org.apache.camel.test.infra.chroma.common.ChromaProperties;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class ChromaServiceFactory {

    private ChromaServiceFactory() {
    }

    public static class SingletonChromaService extends SingletonService<ChromaService> implements ChromaService {
        public SingletonChromaService(ChromaService service, String name) {
            super(service, name);
        }

        @Override
        public String getHost() {
            return getService().getHost();
        }

        @Override
        public int getPort() {
            return getService().getPort();
        }

        @Override
        public String getEndpoint() {
            return getService().getEndpoint();
        }
    }

    public static SimpleTestServiceBuilder<ChromaService> builder() {
        return new SimpleTestServiceBuilder<>(ChromaProperties.INFRA_TYPE);
    }

    public static ChromaService createService() {
        return builder()
                .addLocalMapping(ChromaLocalContainerService::new)
                .addRemoteMapping(ChromaRemoteService::new)
                .build();
    }

    public static ChromaService createSingletonService() {
        return builder()
                .addLocalMapping(
                        () -> new SingletonChromaService(new ChromaLocalContainerService(), ChromaProperties.INFRA_TYPE))
                .build();
    }

    public static class ChromaLocalContainerService extends ChromaLocalContainerInfraService implements ChromaService {
    }

    public static class ChromaRemoteService extends ChromaRemoteInfraService implements ChromaService {
    }
}
