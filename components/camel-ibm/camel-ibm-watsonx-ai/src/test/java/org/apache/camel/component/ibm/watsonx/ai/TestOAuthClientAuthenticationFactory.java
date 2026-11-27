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
package org.apache.camel.component.ibm.watsonx.ai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.spi.OAuthClientConfig;

/**
 * The OAuth client factory of the tests, in the place of camel-oauth (see
 * META-INF/services/org/apache/camel/oauth-client-authentication-factory): a test decides which token a profile gives.
 */
public class TestOAuthClientAuthenticationFactory implements OAuthClientAuthenticationFactory {

    private static final Function<String, String> NO_TOKEN = profile -> {
        throw new IllegalStateException("No OAuth token for the profile " + profile);
    };

    static volatile Function<String, String> tokens = NO_TOKEN;
    static final List<String> PROFILES = new CopyOnWriteArrayList<>();

    /**
     * Sets the tokens the profiles give.
     */
    public static void tokens(Function<String, String> tokens) {
        TestOAuthClientAuthenticationFactory.tokens = tokens;
    }

    /**
     * No profile gives a token.
     */
    public static void reset() {
        tokens = NO_TOKEN;
        PROFILES.clear();
    }

    @Override
    public String resolveToken(OAuthClientConfig config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String resolveToken(CamelContext context, String profileName) {
        PROFILES.add(profileName);
        return tokens.apply(profileName);
    }

    @Override
    public String resolveToken(CamelContext context) {
        throw new UnsupportedOperationException();
    }
}
