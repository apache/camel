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
package org.apache.camel.test.infra.hivemq.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.apache.camel.test.infra.hivemq.common.HiveMQProperties;

public final class HiveMQServiceFactory {
    static class SingletonHiveMQService extends SingletonService<HiveMQService> implements HiveMQService {
        public SingletonHiveMQService(HiveMQService service, String name) {
            super(service, name);
        }

        @Override
        public int getMqttPort() {
            return getService().getMqttPort();
        }

        @Override
        public String getMqttHost() {
            return getService().getMqttHost();
        }

        @Override
        public boolean isRunning() {
            return getService().isRunning();
        }

        @Override
        public String getUserName() {
            return getService().getUserName();
        }

        @Override
        public char[] getUserPassword() {
            return getService().getUserPassword();
        }

    }

    private HiveMQServiceFactory() {
    }

    public static SimpleTestServiceBuilder<HiveMQService> builder() {
        return new SimpleTestServiceBuilder<HiveMQService>(HiveMQProperties.HIVEMQ_TEST_SERVICE_NAME)
                .withPropertyNameFormat(HiveMQProperties.HIVEMQ_PROPERTY_NAME_FORMAT);
    }

    public static HiveMQService createService() {
        return builder()
                .addLocalMapping(LocalHiveMQService::new)
                .addRemoteMapping(RemoteHiveMQService::new)
                .addMapping(HiveMQProperties.HIVEMQ_SPARKPLUG_INSTANCE_SELECTOR, LocalHiveMQSparkplugTCKService::new)
                .build();
    }

    public static HiveMQService createSingletonService() {
        return SingletonServiceHolder.INSTANCE;
    }

    public static HiveMQService createSingletonService(String mappingName) {
        System.setProperty(HiveMQProperties.HIVEMQ_INSTANCE_TYPE, mappingName);
        return createSingletonService();
    }

    private static class SingletonServiceHolder {
        static final HiveMQService INSTANCE;
        static {
            SimpleTestServiceBuilder<HiveMQService> builder = builder();

            builder.addLocalMapping(() -> new SingletonHiveMQService(new LocalHiveMQService(), "hivemq"))
                    .addRemoteMapping(RemoteHiveMQService::new)
                    .addMapping(HiveMQProperties.HIVEMQ_SPARKPLUG_INSTANCE_SELECTOR,
                            () -> new SingletonHiveMQService(new LocalHiveMQSparkplugTCKService(), "hivemq-sparkplug"));

            INSTANCE = builder.build();
        }
    }
}
