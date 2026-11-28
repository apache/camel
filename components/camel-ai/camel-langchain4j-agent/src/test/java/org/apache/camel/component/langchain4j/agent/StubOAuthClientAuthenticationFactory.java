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
package org.apache.camel.component.langchain4j.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.spi.OAuthClientConfig;

/**
 * Stands in for camel-oauth in tests: returns a fixed token per profile, without calling a token endpoint. Registered
 * in {@code META-INF/services/org/apache/camel/oauth-client-authentication-factory}.
 *
 * <p>
 * Tests that need rotating tokens can call {@link #registerRotating(String, String...)} before the test and
 * {@link #reset(String)} after.
 */
public class StubOAuthClientAuthenticationFactory implements OAuthClientAuthenticationFactory {

    private static final Map<String, Supplier<String>> TOKEN_SUPPLIERS = new ConcurrentHashMap<>();

    /**
     * Registers a rotating token sequence for the given profile. Successive calls to
     * {@link #resolveToken(CamelContext, String)} return the tokens in order; the last token is repeated.
     */
    static void registerRotating(String profileName, String... tokens) {
        AtomicInteger idx = new AtomicInteger(0);
        TOKEN_SUPPLIERS.put(profileName, () -> tokens[Math.min(idx.getAndIncrement(), tokens.length - 1)]);
    }

    /**
     * Registers a fixed token that can be changed later with another call. Use this when the exact number of requests
     * per session is unknown and each session must carry a specific token.
     */
    static void registerFixed(String profileName, String token) {
        TOKEN_SUPPLIERS.put(profileName, () -> token);
    }

    /**
     * Removes any rotating token supplier for the given profile, reverting to the default fixed-token behavior.
     */
    static void reset(String profileName) {
        TOKEN_SUPPLIERS.remove(profileName);
    }

    static String tokenFor(String profileName) {
        return "token-for-" + profileName;
    }

    @Override
    public String resolveToken(OAuthClientConfig config) {
        throw new UnsupportedOperationException("Only named profiles are supported by this stub");
    }

    @Override
    public String resolveToken(CamelContext context, String profileName) {
        Supplier<String> supplier = TOKEN_SUPPLIERS.get(profileName);
        return supplier != null ? supplier.get() : tokenFor(profileName);
    }

    @Override
    public String resolveToken(CamelContext context) {
        throw new UnsupportedOperationException("Only named profiles are supported by this stub");
    }
}
