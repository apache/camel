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

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Helper utility to get the test name for legacy test. Do not use. Prefer to use JUnit's TestInfo whenever possible.
 */
public class TestNameExtension
        implements BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback,
        AfterTestExecutionCallback {
    private static final String START_TIME = "start time";
    private String currentTestName;

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        currentTestName = context.getDisplayName();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        currentTestName = context.getDisplayName();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        currentTestName = context.getDisplayName();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        currentTestName = context.getDisplayName();
    }

    /**
     * Ges the current test name
     *
     * @return the current test name
     */
    public String getCurrentTestName() {
        return currentTestName;
    }
}
