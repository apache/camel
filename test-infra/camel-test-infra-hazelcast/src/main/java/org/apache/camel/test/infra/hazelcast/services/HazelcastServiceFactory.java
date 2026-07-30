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
package org.apache.camel.test.infra.hazelcast.services;

import com.hazelcast.config.Config;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class HazelcastServiceFactory {

    private static class SingletonHazelcastService extends SingletonService<HazelcastService> implements HazelcastService {
        public SingletonHazelcastService(HazelcastService service, String name) {
            super(service, name);
        }

        @Override
        public Config createConfiguration(String name, int port, String instanceName, String componentName) {
            return getService().createConfiguration(name, port, instanceName, componentName);
        }
    }

    private HazelcastServiceFactory() {

    }

    public static SimpleTestServiceBuilder<HazelcastService> builder() {
        return new SimpleTestServiceBuilder<>("hazelcast");
    }

    public static HazelcastService createService() {
        return builder()
                .addLocalMapping(HazelcastEmbeddedService::new)
                .build();
    }

    public static HazelcastService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    private static class SingletonServiceHolder {
        static final HazelcastService INSTANCE;
        static {
            SimpleTestServiceBuilder<HazelcastService> instance = builder();
            instance.addLocalMapping(
                    () -> new SingletonHazelcastService(new HazelcastEmbeddedService(), "hazelcast"));
            INSTANCE = instance.build();
        }
    }

    public static class HazelcastEmbeddedService extends HazelcastEmbeddedInfraService implements HazelcastService {
    }
}
