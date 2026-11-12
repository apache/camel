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

package org.apache.camel.component.knative.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;

/**
 * OpenID connect service options implementation grants access to the OIDC token that should be used to verify client
 * requests.
 * <p>
 * The consumer accepts a request when its <code>Authorization</code> header is <code>Bearer</code> followed by the
 * token mounted at the token path. The token is compared with the mounted one, it is not validated as an OIDC token
 * (signature, issuer, audience or expiry).
 */
public class KnativeOidcServiceOptions implements KnativeHttpServiceOptions {
    private static final String PROPERTY_PREFIX = "camel.knative.service.oidc.";

    private CamelContext camelContext;

    private boolean oidcEnabled;

    private String oidcTokenPath;

    private volatile String oidcToken;

    private boolean cacheTokens = true;

    public KnativeOidcServiceOptions() {
    }

    public KnativeOidcServiceOptions(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions(camelContext);
    }

    public void configureOptions() {
        if (camelContext != null) {
            configureOptions(camelContext);
        }
    }

    /**
     * Configures this web client options instance based on properties and environment variables resolved with the given
     * Camel context.
     */
    public void configureOptions(CamelContext camelContext) {
        PropertiesComponent propertiesComponent = camelContext.getPropertiesComponent();

        boolean oidcEnabled = Boolean.parseBoolean(
                propertiesComponent.resolveProperty(PROPERTY_PREFIX + "enabled").orElse("false"));
        setOidcEnabled(oidcEnabled);

        if (oidcEnabled) {
            Optional<String> oidcTokenPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "token.path");
            oidcTokenPath.ifPresent(token -> this.oidcTokenPath = token);

            boolean cacheTokens = Boolean.parseBoolean(
                    propertiesComponent.resolveProperty(PROPERTY_PREFIX + "cache.tokens").orElse("true"));
            setCacheTokens(cacheTokens);
        }
    }

    /**
     * Read OIDC token from given path. Caches the token for future usage.
     */
    public String retrieveOidcToken() {
        String token = oidcToken;
        if (token == null || !cacheTokens) {
            token = loadOidcToken();
            oidcToken = token;
        }

        return token;
    }

    /**
     * Force renew of token by reading again from token path. Token may have been renewed already via volume mount
     * update.
     */
    public String renewOidcToken() {
        String token = loadOidcToken();
        oidcToken = token;
        return token;
    }

    /**
     * Whether the given value of the <code>Authorization</code> header is <code>Bearer</code> followed by the token at
     * the token path. The comparison takes the same time wherever the first difference is. When the cached token does
     * not match, the token is read again, as Knative may have renewed the mounted token since it was read: the renewed
     * token is then accepted, and the token it replaces is no longer accepted.
     *
     * @param  authorization the value of the <code>Authorization</code> header of the request, may be null
     * @return               true when the request carries the token at the token path
     */
    public boolean isAuthorized(String authorization) {
        if (authorization == null) {
            return false;
        }
        if (matches(authorization, retrieveOidcToken())) {
            return true;
        }
        // without the cache the token was just read
        return cacheTokens && matches(authorization, renewOidcToken());
    }

    private static boolean matches(String authorization, String token) {
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, authorization.getBytes(StandardCharsets.UTF_8));
    }

    private String loadOidcToken() {
        try {
            return IOHelper
                    .loadText(ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), oidcTokenPath))
                    .trim();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setOidcEnabled(boolean oidcEnabled) {
        this.oidcEnabled = oidcEnabled;
    }

    public boolean isOidcEnabled() {
        return oidcEnabled;
    }

    public void setCacheTokens(boolean cacheTokens) {
        this.cacheTokens = cacheTokens;
    }

    public boolean isCacheTokens() {
        return cacheTokens;
    }

    public void setOidcTokenPath(String oidcTokenPath) {
        this.oidcTokenPath = oidcTokenPath;
    }

    public String getOidcTokenPath() {
        return oidcTokenPath;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
