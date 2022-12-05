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

package org.apache.camel.test.infra.hdfs.v2.services;

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class HDFSServiceFactory {

    static class SingletonHDFSService extends SingletonService<HDFSService> implements HDFSService {
        public SingletonHDFSService(HDFSService service, String name) {
            super(service, name);
        }

        @Override
        public String getHDFSHost() {
            return getService().getHDFSHost();
        }

        @Override
        public int getPort() {
            return getService().getPort();
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

    private static SimpleTestServiceBuilder<HDFSService> instance;
    private static HDFSService service;

    private HDFSServiceFactory() {

    }

    public static SimpleTestServiceBuilder<HDFSService> builder() {
        return new SimpleTestServiceBuilder<>("hdfs");
    }

    public static HDFSService createSingletonService() {
        if (service == null) {
            if (instance == null) {
                instance = builder();
                instance.addLocalMapping(() -> new SingletonHDFSService(new ContainerLocalHDFSService(), "hdfs"));
            }
            service = instance.build();
        }
        return service;
    }
}
