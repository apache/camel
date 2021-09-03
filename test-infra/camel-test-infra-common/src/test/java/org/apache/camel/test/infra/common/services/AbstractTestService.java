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
package org.apache.camel.test.infra.common.services;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class AbstractTestService implements TestService, BeforeAllCallback, AfterAllCallback {

    protected ExtensionContext context;

    @Override
    public void registerProperties() {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        registerProperties(store::put);
    }

    @Override
    public void initialize() {
        try {
            setUp();
            registerProperties();
        } catch (Exception e) {
            Assertions.fail("Unable to initialize the service " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        try {
            tearDown();
        } catch (Exception e) {
            Assertions.fail("Unable to terminate the service " + e.getMessage(), e);
        }
    }

    protected void registerProperties(BiConsumer<String, String> store) {
    }

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        this.context = extensionContext;
        initialize();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        shutdown();
        this.context = null;
    }

}
