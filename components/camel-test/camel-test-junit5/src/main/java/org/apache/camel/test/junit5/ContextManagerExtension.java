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
package org.apache.camel.test.junit5;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContextManagerExtension
        implements BeforeEachCallback, AfterEachCallback, AfterAllCallback, BeforeAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(ContextManagerExtension.class);

    private final TestExecutionConfiguration testConfigurationBuilder;
    private final CamelContextConfiguration camelContextConfiguration;

    private CamelContextManager contextManager;
    private final ContextManagerFactory contextManagerFactory;
    private String currentTestName;

    public ContextManagerExtension() {
        this(new ContextManagerFactory());
    }

    public ContextManagerExtension(ContextManagerFactory contextManagerFactory) {
        testConfigurationBuilder = new TestExecutionConfiguration();
        camelContextConfiguration = new CamelContextConfiguration();

        this.contextManagerFactory = contextManagerFactory;
    }

    public ContextManagerExtension(TestExecutionConfiguration testConfigurationBuilder,
                                   CamelContextConfiguration camelContextConfiguration) {
        this.testConfigurationBuilder = testConfigurationBuilder;
        this.camelContextConfiguration = camelContextConfiguration;

        this.contextManagerFactory = new ContextManagerFactory();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        final boolean perClassPresent
                = context.getTestInstanceLifecycle().filter(lc -> lc.equals(TestInstance.Lifecycle.PER_CLASS)).isPresent();
        if (perClassPresent) {
            LOG.warn("Creating a legacy context manager for {}. This function is deprecated and will be removed in the future",
                    context.getDisplayName());
            contextManager = contextManagerFactory.createContextManager(
                    ContextManagerFactory.Type.BEFORE_ALL, testConfigurationBuilder, camelContextConfiguration);

            ExtensionContext.Store globalStore = context.getStore(ExtensionContext.Namespace.GLOBAL);
            contextManager.setGlobalStore(globalStore);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        contextManager.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (contextManager == null) {
            LOG.trace("Creating a transient context manager for {}", context.getDisplayName());
            contextManager = contextManagerFactory.createContextManager(ContextManagerFactory.Type.BEFORE_EACH,
                    testConfigurationBuilder, camelContextConfiguration);
        }

        currentTestName = context.getDisplayName();
        ExtensionContext.Store globalStore = context.getStore(ExtensionContext.Namespace.GLOBAL);
        contextManager.setGlobalStore(globalStore);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        DefaultCamelContext.clearOptions();
        contextManager.stop();
    }

    public CamelContextManager getContextManager() {
        return contextManager;
    }

    public String getCurrentTestName() {
        return currentTestName;
    }

    public TestExecutionConfiguration getTestConfigurationBuilder() {
        return testConfigurationBuilder;
    }

    public CamelContextConfiguration getCamelContextConfiguration() {
        return camelContextConfiguration;
    }
}
