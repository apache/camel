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
package org.apache.camel.test.infra.nats.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class NatsServiceFactory {

    private static class SingletonNatsService extends SingletonService<NatsService> implements NatsService {
        public SingletonNatsService(NatsService service, String name) {
            super(service, name);
        }

        @Override
        public String getServiceAddress() {
            return getService().getServiceAddress();
        }
    }

    private NatsServiceFactory() {

    }

    public static SimpleTestServiceBuilder<NatsService> builder() {
        return new SimpleTestServiceBuilder<>("nats");
    }

    public static NatsService createService() {
        return builder().addLocalMapping(NatsLocalContainerService::new)
                .addRemoteMapping(NatsRemoteService::new)
                .build();
    }

    public static NatsService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final NatsService INSTANCE;
        static {
            SimpleTestServiceBuilder<NatsService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonNatsService(new NatsLocalContainerService(), "nats"))
                    .addRemoteMapping(NatsRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class NatsRemoteService extends NatsRemoteInfraService implements NatsService {
    }
}
