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

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;

/**
 * Utility class for the test services
 */
public final class TestServiceUtil {
    private static final Logger LOG = LoggerFactory.getLogger(TestServiceUtil.class);
    private static final int MAX_RETRIES = Integer.getInteger("camel.test.infra.container.retries", 3);
    private static final long BASE_DELAY_MS = Long.getLong("camel.test.infra.container.retry.delay.ms", 5000);

    private TestServiceUtil() {

    }

    /**
     * Try to initialize the service with retry for transient container errors. Retries on
     * {@link ContainerFetchException} (image pull failures) and {@link ContainerLaunchException} (container start
     * failures) with exponential backoff and jitter. Non-container exceptions fail immediately. Retry count and delay
     * are configurable via system properties {@code camel.test.infra.container.retries} (default 3) and
     * {@code camel.test.infra.container.retry.delay.ms} (default 5000).
     *
     * @param  service          the service to initialize
     * @param  extensionContext JUnit's extension context
     * @throws Exception        exception thrown while initializing (if any)
     */
    public static void tryInitialize(TestService service, ExtensionContext extensionContext) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                service.initialize();
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES && isRetryableContainerException(e)) {
                    long jitter = ThreadLocalRandom.current().nextLong(0, BASE_DELAY_MS / 2);
                    long delay = BASE_DELAY_MS * attempt + jitter;
                    LOG.warn("Service {} initialization failed (attempt {}/{}), retrying in {}ms: {}",
                            service.getClass().getSimpleName(), attempt, MAX_RETRIES, delay, e.getMessage());
                    Thread.sleep(delay);
                } else {
                    logAndRethrow(service, extensionContext, e);
                }
            }
        }
    }

    private static boolean isRetryableContainerException(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ContainerFetchException || t instanceof ContainerLaunchException) {
                return true;
            }
        }
        return false;
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
        if (extensionContext != null) {
            final Object testInstance = extensionContext.getTestInstance().orElse(null);

            if (testInstance != null) {
                LOG.error("Failed to initialize service {} for test {} on ({})", service.getClass().getSimpleName(),
                        extensionContext.getDisplayName(), testInstance.getClass().getName());
            } else {
                LOG.error("Failed to initialize service {} for test {}", service.getClass().getSimpleName(),
                        extensionContext.getDisplayName());
            }
        } else {
            LOG.error("Failed to initialize service {}", service.getClass().getSimpleName());
        }

        throw exception;
    }
}
