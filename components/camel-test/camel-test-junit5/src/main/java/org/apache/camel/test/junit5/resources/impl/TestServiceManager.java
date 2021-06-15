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
package org.apache.camel.test.junit5.resources.impl;

import java.lang.reflect.Field;

import org.apache.camel.test.junit5.resources.BaseResourceManager;
import org.apache.camel.test.junit5.resources.TestService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestServiceManager extends BaseResourceManager<TestService> {

    public TestServiceManager() {
        super(TestService.class);
    }

    @Override
    protected Holder createHolder(ExtensionContext context, Field field) {
        return new Holder() {
            org.apache.camel.test.infra.common.services.TestService service;

            @Override
            public synchronized Object get() throws Exception {
                if (service == null) {
                    Class<?> serviceClass = field.getType();
                    Class<?> factoryClass = serviceClass.getClassLoader().loadClass(serviceClass.getName() + "Factory");
                    service = (org.apache.camel.test.infra.common.services.TestService) factoryClass.getMethod("createService")
                            .invoke(null);
                    if (service instanceof BeforeAllCallback) {
                        ((BeforeAllCallback) service).beforeAll(context);
                    } else {
                        service.initialize();
                    }
                }
                return service;
            }

            @Override
            public synchronized void close() throws Exception {
                if (service instanceof AfterAllCallback) {
                    ((AfterAllCallback) service).afterAll(context);
                } else if (service != null) {
                    service.close();
                }
            }
        };
    }

}
