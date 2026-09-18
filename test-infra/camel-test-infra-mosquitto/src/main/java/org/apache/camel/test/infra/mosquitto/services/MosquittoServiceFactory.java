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
package org.apache.camel.test.infra.mosquitto.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class MosquittoServiceFactory {

    private static class SingletonMosquittoService extends SingletonService<MosquittoService> implements MosquittoService {
        public SingletonMosquittoService(MosquittoService service, String name) {
            super(service, name);
        }

        @Override
        public Integer getPort() {
            return getService().getPort();
        }
    }

    private MosquittoServiceFactory() {

    }

    public static SimpleTestServiceBuilder<MosquittoService> builder() {
        return new SimpleTestServiceBuilder<>("mosquitto");
    }

    public static MosquittoService createService() {
        return builder()
                .addLocalMapping(MosquittoLocalContainerService::new)
                .addRemoteMapping(MosquittoRemoteService::new)
                .build();
    }

    public static MosquittoService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final MosquittoService INSTANCE;
        static {
            SimpleTestServiceBuilder<MosquittoService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonMosquittoService(new MosquittoLocalContainerService(), "mosquitto"))
                    .addRemoteMapping(MosquittoRemoteService::new);
            INSTANCE = instance.build();
        }
    }
}
