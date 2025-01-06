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

package org.apache.camel.test.infra.smb.services;

import java.io.InputStream;

import org.apache.camel.test.infra.common.TestUtils;
import org.apache.camel.test.infra.common.services.TestService;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.utility.ThrowingFunction;

public interface SmbService extends TestService, BeforeTestExecutionCallback, AfterTestExecutionCallback {
    String address();

    String shareName();

    String userName();

    String password();

    <T> T copyFileFromContainer(String fileName, ThrowingFunction<InputStream, T> function);

    String smbFile(String file);

    @Override
    default void beforeAll(ExtensionContext extensionContext) {
        try {
            initialize();
        } catch (Exception e) {
            TestUtils.logInitializationFailure(extensionContext, SmbService.class);

            throw e;
        }
    }

    @Override
    default void beforeTestExecution(ExtensionContext extensionContext) {
        //no op
    }

    @Override
    default void afterAll(ExtensionContext extensionContext) {
        shutdown();
    }

    @Override
    default void afterTestExecution(ExtensionContext context) {
        //no op
    }
}
