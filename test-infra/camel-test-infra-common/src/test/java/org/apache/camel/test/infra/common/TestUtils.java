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
package org.apache.camel.test.infra.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * Test utilities
 */
public final class TestUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TestUtils.class);

    private TestUtils() {
    }

    /**
     * Wait for a given condition to be true or the retry amount (30) to expire
     *
     * @param resourceCheck
     * @param payload
     * @param <T>
     */
    public static <T> boolean waitFor(Predicate<T> resourceCheck, T payload) {
        boolean state = false;
        int retries = 30;
        int waitTime = 1000;
        do {
            try {
                state = resourceCheck.test(payload);

                if (!state) {
                    LOG.debug("The resource is not yet available. Waiting {} seconds before retrying",
                            TimeUnit.MILLISECONDS.toSeconds(waitTime));
                    retries--;
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException e) {
                break;
            }

        } while (!state && retries > 0);

        return state;
    }

    /**
     * Wait for a given condition to be true or the retry amount (30) to expire
     *
     * @param resourceCheck
     */
    public static boolean waitFor(BooleanSupplier resourceCheck) {
        boolean state = false;
        int retries = 30;
        int waitTime = 1000;
        do {
            try {
                state = resourceCheck.getAsBoolean();

                if (!state) {
                    LOG.debug("The resource is not yet available. Waiting {} seconds before retrying",
                            TimeUnit.MILLISECONDS.toSeconds(waitTime));
                    retries--;
                    Thread.sleep(waitTime);
                }
            } catch (InterruptedException e) {
                break;
            }
        } while (!state && retries > 0);

        return state;
    }

    /**
     * Gets a random number within range
     *
     * @param  min
     * @param  max
     * @return
     */
    public static int randomWithRange(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    /**
     * Prepend imageName with configured hub.image.name.prefix if any is configured in testcontainers
     *
     * @param  imageName
     * @return           a String composed of hub.image.name.prefix as configured in testcontainers + imageName
     */
    public static String prependHubImageNamePrefixIfNeeded(String imageName) {
        return TestcontainersConfiguration.getInstance().getEnvVarOrProperty("hub.image.name.prefix", "") + imageName;
    }
}
