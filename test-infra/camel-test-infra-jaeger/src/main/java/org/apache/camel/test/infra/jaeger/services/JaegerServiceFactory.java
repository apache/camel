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
package org.apache.camel.test.infra.jaeger.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

/**
 * @since 4.21
 */
public final class JaegerServiceFactory {

    private static class SingletonJaegerService extends SingletonService<JaegerService> implements JaegerService {
        public SingletonJaegerService(JaegerService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int collectorGrpcPort() {
            return getService().collectorGrpcPort();
        }

        @Override
        public int collectorHttpPort() {
            return getService().collectorHttpPort();
        }

        @Override
        public int queryUiPort() {
            return getService().queryUiPort();
        }
    }

    private JaegerServiceFactory() {
    }

    public static SimpleTestServiceBuilder<JaegerService> builder() {
        return new SimpleTestServiceBuilder<>("jaeger");
    }

    public static JaegerService createService() {
        return builder()
                .addLocalMapping(JaegerLocalContainerService::new)
                .addRemoteMapping(JaegerRemoteService::new)
                .build();
    }

    public static JaegerService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final JaegerService INSTANCE;
        static {
            SimpleTestServiceBuilder<JaegerService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonJaegerService(new JaegerLocalContainerService(), "jaeger"))
                    .addRemoteMapping(JaegerRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class JaegerRemoteService extends JaegerRemoteInfraService implements JaegerService {
    }

    public static class JaegerLocalContainerService extends JaegerLocalContainerInfraService implements JaegerService {
    }
}
