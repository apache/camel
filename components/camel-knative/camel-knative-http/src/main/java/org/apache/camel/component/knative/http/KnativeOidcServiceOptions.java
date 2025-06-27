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
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;

/**
 * OpenID connect service options implementation grants access to the OIDC token that should be used to verify client
 * requests.
 */
public class KnativeOidcServiceOptions implements KnativeHttpServiceOptions {
    private static final String PROPERTY_PREFIX = "camel.knative.service.oidc.";

    private CamelContext camelContext;

    private boolean oidcEnabled;

    private String oidcTokenPath;

    private String oidcToken;

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
        if (oidcToken == null || !cacheTokens) {
            try {
                oidcToken = IOHelper
                        .loadText(ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), oidcTokenPath))
                        .trim();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return oidcToken;
    }

    /**
     * Force renew of token by reading again from token path. Token may have been renewed already via volume mount
     * update.
     */
    public String renewOidcToken() {
        oidcToken = null;
        return retrieveOidcToken();
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
