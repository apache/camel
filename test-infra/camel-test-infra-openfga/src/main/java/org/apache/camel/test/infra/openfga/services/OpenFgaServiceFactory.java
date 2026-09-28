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
package org.apache.camel.test.infra.openfga.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class OpenFgaServiceFactory {

    private static class SingletonOpenFgaService extends SingletonService<OpenFgaService> implements OpenFgaService {
        public SingletonOpenFgaService(OpenFgaService service, String name) {
            super(service, name);
        }

        @Override
        public String getOpenFgaUrl() {
            return getService().getOpenFgaUrl();
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }
    }

    private OpenFgaServiceFactory() {
    }

    public static SimpleTestServiceBuilder<OpenFgaService> builder() {
        return new SimpleTestServiceBuilder<>("openfga");
    }

    public static OpenFgaService createService() {
        return builder()
                .addLocalMapping(OpenFgaLocalContainerTestService::new)
                .addRemoteMapping(OpenFgaRemoteTestService::new)
                .build();
    }

    public static OpenFgaService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final OpenFgaService INSTANCE;
        static {
            SimpleTestServiceBuilder<OpenFgaService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonOpenFgaService(new OpenFgaLocalContainerTestService(), "openfga"))
                    .addRemoteMapping(OpenFgaRemoteTestService::new);
            INSTANCE = instance.build();
        }
    }

    public static class OpenFgaLocalContainerTestService extends OpenFgaLocalContainerInfraService
            implements OpenFgaService {
    }

    public static class OpenFgaRemoteTestService extends OpenFgaRemoteInfraService implements OpenFgaService {
    }
}
