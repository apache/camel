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
package org.apache.camel.test.infra.ignite.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.apache.ignite.configuration.IgniteConfiguration;

public final class IgniteServiceFactory {

    private static class SingletonIgniteService extends SingletonService<IgniteService> implements IgniteService {
        public SingletonIgniteService(IgniteService service, String name) {
            super(service, name);
        }

        @Override
        public IgniteConfiguration createConfiguration() {
            return getService().createConfiguration();
        }
    }

    private IgniteServiceFactory() {

    }

    public static SimpleTestServiceBuilder<IgniteService> builder() {
        return new SimpleTestServiceBuilder<>("ignite");
    }

    public static IgniteService createService() {
        return builder()
                .addLocalMapping(IgniteEmbeddedService::new)
                .build();
    }

    public static IgniteService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final IgniteService INSTANCE;
        static {
            SimpleTestServiceBuilder<IgniteService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonIgniteService(new IgniteEmbeddedService(), "ignite"));
            INSTANCE = instance.build();
        }
    }

    public static class IgniteEmbeddedService extends IgniteEmbeddedInfraService implements IgniteService {
    }
}
