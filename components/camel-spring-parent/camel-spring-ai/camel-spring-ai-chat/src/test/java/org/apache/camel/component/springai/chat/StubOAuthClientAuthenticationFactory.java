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
package org.apache.camel.component.springai.chat;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.spi.OAuthClientConfig;

/**
 * Stands in for camel-oauth in tests: returns a fixed token per profile, without calling a token endpoint. Registered
 * in {@code META-INF/services/org/apache/camel/oauth-client-authentication-factory}.
 */
public class StubOAuthClientAuthenticationFactory implements OAuthClientAuthenticationFactory {

    static String tokenFor(String profileName) {
        return "token-for-" + profileName;
    }

    @Override
    public String resolveToken(OAuthClientConfig config) {
        throw new UnsupportedOperationException("Only named profiles are supported by this stub");
    }

    @Override
    public String resolveToken(CamelContext context, String profileName) {
        return tokenFor(profileName);
    }

    @Override
    public String resolveToken(CamelContext context) {
        throw new UnsupportedOperationException("Only named profiles are supported by this stub");
    }
}
