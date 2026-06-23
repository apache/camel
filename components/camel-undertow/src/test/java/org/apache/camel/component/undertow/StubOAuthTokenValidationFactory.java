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
package org.apache.camel.component.undertow;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared stub {@link OAuthTokenValidationFactory} for the undertow OAuth profile tests. Only the {@code myprofile}
 * profile is known. The {@code valid-token} Bearer token authenticates as {@code undertow-user}, the
 * {@code error-token} Bearer token simulates a validation infrastructure failure, and any other token is rejected as
 * invalid.
 */
public final class StubOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

    @Override
    public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
        return validate(token);
    }

    @Override
    public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
        assertEquals("myprofile", profileName);
        return validate(token);
    }

    @Override
    public void validateConfiguration(OAuthTokenValidationConfig config) {
    }

    @Override
    public void validateConfiguration(CamelContext context, String profileName) {
        if (!"myprofile".equals(profileName)) {
            throw new IllegalArgumentException("Unknown OAuth profile: " + profileName);
        }
    }

    private OAuthTokenValidationResult validate(String token) {
        if ("valid-token".equals(token)) {
            return OAuthTokenValidationResult.valid(
                    "undertow-user", "https://issuer.example", List.of("camel-api"), List.of("read"),
                    Map.of("tenant", "acme"), 1234567890);
        }
        if ("error-token".equals(token)) {
            throw new IllegalStateException("validator unavailable");
        }
        return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
    }
}
