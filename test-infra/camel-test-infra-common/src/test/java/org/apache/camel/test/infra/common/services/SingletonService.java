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

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a wrapper class for creating singleton services
 *
 * @param <T> The type of the service to be wrapped
 */
public class SingletonService<T extends TestService> implements ExtensionContext.Store.CloseableResource, TestService {
    private static final Logger LOG = LoggerFactory.getLogger(SingletonService.class);

    private final T service;
    private final String name;

    public SingletonService(T service, String name) {
        this.service = service;
        this.name = name;
    }

    protected void addToStore(ExtensionContext extensionContext) {
        final ExtensionContext root = extensionContext.getRoot();
        LOG.debug("Using root: {}", root);

        final ExtensionContext.Store store = root.getStore(ExtensionContext.Namespace.GLOBAL);
        LOG.debug("Using store: {}", store);

        store.getOrComputeIfAbsent(name, this::doInitializeService);
    }

    protected SingletonService<T> doInitializeService(String name) {
        LOG.debug("Registering singleton service {}", name);
        service.initialize();
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        addToStore(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        // NO-OP
    }

    @Override
    public void registerProperties() {
        service.registerProperties();
    }

    @Override
    public void initialize() {
        service.initialize();
    }

    @Override
    public void shutdown() {
        service.shutdown();
    }

    @Override
    public void close() {
        service.shutdown();
    }

    protected T getService() {
        return service;
    }
}
