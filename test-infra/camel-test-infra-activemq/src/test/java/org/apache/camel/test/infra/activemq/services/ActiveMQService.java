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
package org.apache.camel.test.infra.activemq.services;

import org.apache.camel.test.infra.activemq.common.ActiveMQProperties;
import org.apache.camel.test.infra.common.services.TestService;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Test infra service for ActiveMQ
 */
public interface ActiveMQService
        extends BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, TestService {

    String serviceAddress();

    String userName();

    String password();

    default void registerProperties() {
        // For compatibility with the previous format used by camel-sjms tests
        System.setProperty(ActiveMQProperties.SERVICE_ADDRESS, serviceAddress());
        System.setProperty(ActiveMQProperties.AMQ_EXTERNAL, serviceAddress());
        System.setProperty(ActiveMQProperties.AMQ_USERNAME, userName());
        System.setProperty(ActiveMQProperties.AMQ_PASSWORD, userName());
    }

    @Override
    default void beforeAll(ExtensionContext extensionContext) throws Exception {
        initialize();
    }

    @Override
    default void afterAll(ExtensionContext extensionContext) throws Exception {
        shutdown();
    }

    @Override
    default void afterEach(ExtensionContext extensionContext) throws Exception {
        shutdown();
    }

    @Override
    default void beforeEach(ExtensionContext extensionContext) throws Exception {
        initialize();
    }

    void restart();
}
