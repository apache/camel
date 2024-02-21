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
package org.apache.camel.test.infra.chatscript.services;

import org.apache.camel.test.infra.common.services.TestService;
import org.apache.camel.test.infra.common.services.TestServiceUtil;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Test infra service for ChatScript
 */
public interface ChatScriptService extends BeforeEachCallback, AfterEachCallback, TestService {

    String serviceAddress();

    @Override
    default void afterEach(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryShutdown(this, extensionContext);
    }

    @Override
    default void beforeEach(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryInitialize(this, extensionContext);
    }
}
