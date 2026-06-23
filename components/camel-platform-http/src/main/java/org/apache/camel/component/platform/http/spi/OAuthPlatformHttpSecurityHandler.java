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
package org.apache.camel.component.platform.http.spi;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;
import org.apache.camel.spi.OAuthTokenValidationFactory;

/**
 * Platform HTTP security handler that validates incoming OAuth 2.0 Bearer tokens with a Camel OAuth profile.
 *
 * @since 4.21
 */
public final class OAuthPlatformHttpSecurityHandler implements PlatformHttpSecurityHandler {

    private final OAuthHttpSecuritySupport securitySupport;

    public OAuthPlatformHttpSecurityHandler(String oauthProfile) {
        this(oauthProfile, null);
    }

    public OAuthPlatformHttpSecurityHandler(String oauthProfile, OAuthTokenValidationFactory validationFactory) {
        this.securitySupport = new OAuthHttpSecuritySupport(oauthProfile, validationFactory);
    }

    public OAuthPlatformHttpSecurityHandler(OAuthHttpSecuritySupport securitySupport) {
        this.securitySupport = Objects.requireNonNull(securitySupport, "securitySupport");
    }

    /**
     * Gets the OAuth profile used to validate incoming Bearer tokens.
     */
    public String getOauthProfile() {
        return securitySupport.getOauthProfile();
    }

    @Override
    public Processor wrapProcessor(PlatformHttpEndpoint endpoint, Processor processor) {
        return exchange -> {
            if (authenticate(endpoint, exchange)) {
                processor.process(exchange);
            }
        };
    }

    @Override
    public boolean authenticate(PlatformHttpEndpoint endpoint, Exchange exchange) {
        return securitySupport.authenticate(exchange);
    }
}
