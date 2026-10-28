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
package org.apache.camel.test.infra.spiffe.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class SpiffeServiceFactory {

    private SpiffeServiceFactory() {
    }

    private static class SingletonSpiffeService extends SingletonService<SpiffeService> implements SpiffeService {
        public SingletonSpiffeService(SpiffeService service, String name) {
            super(service, name);
        }

        @Override
        public String getWorkloadApiSocketPath() {
            return getService().getWorkloadApiSocketPath();
        }

        @Override
        public String getTrustDomain() {
            return getService().getTrustDomain();
        }

        @Override
        public String getWorkloadSpiffeId() {
            return getService().getWorkloadSpiffeId();
        }
    }

    public static SimpleTestServiceBuilder<SpiffeService> builder() {
        return new SimpleTestServiceBuilder<>("spiffe");
    }

    public static SpiffeService createService() {
        return builder()
                .addLocalMapping(SpiffeLocalContainerTestService::new)
                .addRemoteMapping(SpiffeRemoteTestService::new)
                .build();
    }

    public static SpiffeService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final SpiffeService INSTANCE;
        static {
            SimpleTestServiceBuilder<SpiffeService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonSpiffeService(new SpiffeLocalContainerTestService(), "spiffe"))
                    .addRemoteMapping(SpiffeRemoteTestService::new);
            INSTANCE = instance.build();
        }
    }

    public static class SpiffeLocalContainerTestService extends SpiffeLocalContainerInfraService implements SpiffeService {
    }

    public static class SpiffeRemoteTestService extends SpiffeRemoteInfraService implements SpiffeService {
    }
}
