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
package org.apache.camel.test.infra.iggy.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class IggyServiceFactory {

    private static class SingletonIggyService extends SingletonService<IggyService> implements IggyService {
        public SingletonIggyService(IggyService service, String name) {
            super(service, name);
        }

        @Override
        public String host() {
            return getService().host();
        }

        @Override
        public int port() {
            return getService().port();
        }

        @Override
        public String username() {
            return getService().username();
        }

        @Override
        public String password() {
            return getService().password();
        }
    }

    private IggyServiceFactory() {

    }

    public static SimpleTestServiceBuilder<IggyService> builder() {
        return new SimpleTestServiceBuilder<>("iggy");
    }

    public static IggyService createService() {
        return builder()
                .addLocalMapping(IggyLocalContainerService::new)
                .addRemoteMapping(IggyRemoteService::new)
                .build();
    }

    public static IggyService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final IggyService INSTANCE;
        static {
            SimpleTestServiceBuilder<IggyService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonIggyService(new IggyLocalContainerService(), "iggy"))
                    .addRemoteMapping(IggyRemoteService::new);
            INSTANCE = instance.build();
        }
    }

    public static class IggyRemoteService extends IggyRemoteInfraService implements IggyService {
    }

    public static class IggyLocalContainerService extends IggyLocalContainerInfraService implements IggyService {
    }
}
