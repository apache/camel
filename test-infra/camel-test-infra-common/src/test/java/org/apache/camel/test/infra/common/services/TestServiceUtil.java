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
 * Utility class for the test services
 */
public final class TestServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TestServiceUtil.class);

    private TestServiceUtil() {

    }

    /**
     * Try to initialize the service, logging failures if they happen
     *
     * @param  service          the service to initialize
     * @param  extensionContext JUnit's extension context
     * @throws Exception        exception thrown while initializing (if any)
     */
    public static void tryInitialize(TestService service, ExtensionContext extensionContext) throws Exception {
        try {
            service.initialize();
        } catch (Exception e) {
            logAndRethrow(service, extensionContext, e);
        }
    }

    /**
     * Try to shut down the service, logging failures if they happen
     *
     * @param  service          the service to initialize
     * @param  extensionContext JUnit's extension context
     * @throws Exception        exception thrown while initializing (if any)
     */
    public static void tryShutdown(TestService service, ExtensionContext extensionContext) throws Exception {
        try {
            service.shutdown();
        } catch (Exception e) {
            logAndRethrow(service, extensionContext, e);
        }
    }

    /**
     * Log and exception, including the test information, and then rethrow the passed exception
     *
     * @param  extensionContext JUnit's extension context
     * @param  exception        the exception that caused the service to fail to initialize
     * @throws Exception        rethrows the exception after logging it
     */
    public static void logAndRethrow(TestService service, ExtensionContext extensionContext, Exception exception)
            throws Exception {
        final Object testInstance = extensionContext.getTestInstance().orElse(null);

        if (testInstance != null) {
            LOG.error("Failed to initialize service {} for test {} on ({})", service.getClass().getSimpleName(),
                    extensionContext.getDisplayName(), testInstance.getClass().getName());
        } else {
            LOG.error("Failed to initialize service {} for test {}", service.getClass().getSimpleName(),
                    extensionContext.getDisplayName());
        }

        throw exception;
    }
}
