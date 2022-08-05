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
package org.apache.camel.test.infra.hbase.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class HBaseServiceFactory {
    static class SingletonHBaseService extends SingletonService<HBaseService> implements HBaseService {
        public SingletonHBaseService(HBaseService service, String name) {
            super(service, name);
        }

        @Override
        public Configuration getConfiguration() {
            return getService().getConfiguration();
        }

        @Override
        public void beforeAll(ExtensionContext extensionContext) {
            addToStore(extensionContext);
        }

        @Override
        public void afterAll(ExtensionContext extensionContext) {
            // NO-OP
        }
    }

    private static SimpleTestServiceBuilder<HBaseService> instance;
    private static HBaseService service;

    private HBaseServiceFactory() {

    }

    public static SimpleTestServiceBuilder<HBaseService> builder() {
        return new SimpleTestServiceBuilder<>("hbase");
    }

    public static HBaseService createService() {
        return builder()
                .addLocalMapping(HBaseLocalContainerService::new)
                .build();
    }

    public static HBaseService createSingletonService() {
        if (service == null) {
            if (instance == null) {
                instance = builder();

                instance.addLocalMapping(() -> new SingletonHBaseService(new HBaseLocalContainerService(), "hbase"));
            }

            service = instance.build();
        }

        return service;
    }
}
