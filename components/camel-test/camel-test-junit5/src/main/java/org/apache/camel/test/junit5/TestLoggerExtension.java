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

import java.lang.reflect.Method;

import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import static org.apache.camel.test.junit5.util.ExtensionHelper.testEndFooter;
import static org.apache.camel.test.junit5.util.ExtensionHelper.testStartHeader;

public class TestLoggerExtension
        implements BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String START_TIME = "start time";
    private String currentTestName;

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        final Class<?> testClass = context.getTestClass().orElse(this.getClass());
        StopWatch durationTracker = getStore(context, testClass).remove(START_TIME, StopWatch.class);

        Method testMethod = context.getRequiredTestMethod();
        if (durationTracker != null) {
            testEndFooter(testClass, testMethod.getName(), durationTracker.taken());
        } else {
            /*
             * Extensions may be called twice for Nested methods.
             * As such, we ignore the last one as it's related to the
             * top-most class: https://github.com/junit-team/junit5/issues/2421
             */
            testEndFooter(testClass, testMethod.getName(), 0);
        }

    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        currentTestName = context.getDisplayName();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {

    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        currentTestName = context.getDisplayName();

        final Class<?> testClass = context.getTestClass().orElse(this.getClass());
        testStartHeader(testClass, currentTestName);
        final ExtensionContext.Store store = getStore(context, testClass);

        store.getOrComputeIfAbsent(START_TIME, key -> new StopWatch(), Object.class);
    }

    private ExtensionContext.Store getStore(ExtensionContext context, Class<?> testClass) {
        return context.getStore(Namespace.create(testClass, context.getRequiredTestMethod()));
    }
}
