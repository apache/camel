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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public interface TestService extends AutoCloseable, BeforeAllCallback, AfterAllCallback {

    /**
     * Register service properties (such as using System.setProperties) so that they can be resolved at distance (ie.:
     * when using Spring's PropertySourcesPlaceholderConfigurer or simply when trying to collect test infra information
     * outside of the test class itself).
     */
    void registerProperties();

    /**
     * Perform any initialization necessary
     */
    void initialize();

    /**
     * Shuts down the service after the test has completed
     */
    void shutdown();

    @Override
    default void close() {
        shutdown();
    }

    @Override
    default void beforeAll(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryInitialize(this, extensionContext);
    }

    @Override
    default void afterAll(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryShutdown(this, extensionContext);
    }
}
