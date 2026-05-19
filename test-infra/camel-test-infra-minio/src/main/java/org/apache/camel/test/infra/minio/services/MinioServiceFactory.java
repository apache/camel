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
package org.apache.camel.test.infra.minio.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class MinioServiceFactory {

    private static class SingletonMinioService extends SingletonService<MinioService> implements MinioService {
        public SingletonMinioService(MinioService service, String name) {
            super(service, name);
        }

        @Override
        public String secretKey() {
            return getService().secretKey();
        }

        @Override
        public String accessKey() {
            return getService().accessKey();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int consolePort() {
            return getService().consolePort();
        }

        @Override
        public String consoleUsername() {
            return getService().consoleUsername();
        }

        @Override
        public String consolePassword() {
            return getService().consolePassword();
        }
    }

    private MinioServiceFactory() {

    }

    public static SimpleTestServiceBuilder<MinioService> builder() {
        return new SimpleTestServiceBuilder<>("minio");
    }

    public static MinioService createService() {
        return builder()
                .addLocalMapping(MinioLocalContainerService::new)
                .addRemoteMapping(MinioRemoteService::new)
                .build();
    }

    public static MinioService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final MinioService INSTANCE;
        static {
            SimpleTestServiceBuilder<MinioService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonMinioService(new MinioLocalContainerService(), "minio"))
                    .addRemoteMapping(MinioRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class MinioLocalContainerService extends MinioLocalContainerInfraService implements MinioService {
    }

    public static class MinioRemoteService extends MinioRemoteInfraService implements MinioService {
    }
}
