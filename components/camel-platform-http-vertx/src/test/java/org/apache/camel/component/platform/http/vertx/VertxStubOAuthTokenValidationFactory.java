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
package org.apache.camel.component.platform.http.vertx;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthTokenValidationConfig;
import org.apache.camel.spi.OAuthTokenValidationFactory;
import org.apache.camel.spi.OAuthTokenValidationResult;
import org.apache.camel.spi.OAuthTokenValidationResult.ErrorCode;

public final class VertxStubOAuthTokenValidationFactory implements OAuthTokenValidationFactory {

    static String lastConfigurationProfileName;
    static String lastToken;
    static boolean validatedWithConfig;

    static void reset() {
        lastConfigurationProfileName = null;
        lastToken = null;
        validatedWithConfig = false;
    }

    @Override
    public OAuthTokenValidationResult validateToken(OAuthTokenValidationConfig config, String token) {
        validatedWithConfig = true;
        lastToken = token;
        return validate(token);
    }

    @Override
    public OAuthTokenValidationResult validateToken(CamelContext context, String profileName, String token) {
        lastConfigurationProfileName = profileName;
        lastToken = token;
        return validate(token);
    }

    @Override
    public void validateConfiguration(OAuthTokenValidationConfig config) {
    }

    @Override
    public void validateConfiguration(CamelContext context, String profileName) {
        lastConfigurationProfileName = profileName;
    }

    private OAuthTokenValidationResult validate(String token) {
        if ("error-token".equals(token)) {
            throw new IllegalStateException("validation infrastructure unavailable");
        }
        if ("valid-token".equals(token)) {
            return OAuthTokenValidationResult.valid(
                    "vertx-user",
                    "https://issuer.example",
                    List.of("camel-api"),
                    List.of("read"),
                    Map.of("scope", "read"),
                    0);
        }
        return OAuthTokenValidationResult.invalid(ErrorCode.INVALID_TOKEN, "invalid token");
    }
}
