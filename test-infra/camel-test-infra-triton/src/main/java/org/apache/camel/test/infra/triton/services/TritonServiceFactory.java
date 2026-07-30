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
package org.apache.camel.test.infra.triton.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class TritonServiceFactory {

    private static class SingletonTritonService extends SingletonService<TritonService> implements TritonService {
        public SingletonTritonService(TritonService service, String name) {
            super(service, name);
        }

        @Override
        public int httpPort() {
            return getService().httpPort();
        }

        @Override
        public int grpcPort() {
            return getService().grpcPort();
        }

        @Override
        public int metricsPort() {
            return getService().metricsPort();
        }
    }

    private TritonServiceFactory() {
    }

    public static SimpleTestServiceBuilder<TritonService> builder() {
        return new SimpleTestServiceBuilder<>("triton");
    }

    public static TritonService createService() {
        return builder()
                .addLocalMapping(TritonLocalContainerService::new)
                .addRemoteMapping(TritonRemoteService::new)
                .build();
    }

    public static TritonService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final TritonService INSTANCE;
        static {
            SimpleTestServiceBuilder<TritonService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonTritonService(new TritonLocalContainerService(), "triton"))
                    .addRemoteMapping(TritonRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class TritonLocalContainerService extends TritonLocalContainerInfraService
            implements TritonService {
    }

    public static class TritonRemoteService extends TritonRemoteInfraService implements TritonService {
    }
}
