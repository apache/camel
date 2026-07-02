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
package org.apache.camel.util;

import java.security.SecureRandom;

/**
 * Provides a shared {@link SecureRandom} instance for use across Camel.
 * <p>
 * {@code SecureRandom} is thread-safe but heavyweight to instantiate — each {@code new SecureRandom()} call gathers OS
 * entropy. Reusing a single instance avoids repeated initialization overhead while maintaining cryptographic security
 * guarantees.
 * <p>
 * This is intended for internal Camel code that needs "give me some randomness" without caring about a specific
 * algorithm or provider. For user-configurable secure random (algorithm, provider), see
 * {@code org.apache.camel.support.jsse.SecureRandomParameters}.
 */
public final class SecureRandomHelper {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureRandomHelper() {
    }

    /**
     * Returns a shared, thread-safe {@link SecureRandom} instance using the platform default algorithm.
     *
     * @return a shared {@link SecureRandom} instance
     */
    public static SecureRandom getSecureRandom() {
        return RANDOM;
    }
}
