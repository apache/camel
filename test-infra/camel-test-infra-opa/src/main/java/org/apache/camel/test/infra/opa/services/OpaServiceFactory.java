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
package org.apache.camel.test.infra.opa.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class OpaServiceFactory {

    private static class SingletonOpaService extends SingletonService<OpaService> implements OpaService {
        public SingletonOpaService(OpaService service, String name) {
            super(service, name);
        }

        @Override
        public String getOpaUrl() {
            return getService().getOpaUrl();
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

    private OpaServiceFactory() {
    }

    public static SimpleTestServiceBuilder<OpaService> builder() {
        return new SimpleTestServiceBuilder<>("opa");
    }

    public static OpaService createService() {
        return builder()
                .addLocalMapping(OpaLocalContainerTestService::new)
                .addRemoteMapping(OpaRemoteTestService::new)
                .build();
    }

    public static OpaService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final OpaService INSTANCE;
        static {
            SimpleTestServiceBuilder<OpaService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonOpaService(new OpaLocalContainerTestService(), "opa"))
                    .addRemoteMapping(OpaRemoteTestService::new);
            INSTANCE = instance.build();
        }
    }

    public static class OpaLocalContainerTestService extends OpaLocalContainerInfraService implements OpaService {
    }

    public static class OpaRemoteTestService extends OpaRemoteInfraService implements OpaService {
    }
}
