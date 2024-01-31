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
package org.apache.camel.component.platform.http.vertx.auth;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.properties.PropertyFileAuthentication;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;

public class AuthenticationConfig {
    public static final String DEFAULT_VERTX_PROPERTIES_FILE = "camel-platform-http-vertx-auth.properties";
    private boolean authenticationEnabled;
    private final List<AuthenticationConfigEntry> entries;

    public AuthenticationConfig() {
        AuthenticationConfigEntry defaultAuthConfig = new AuthenticationConfigEntry();
        defaultAuthConfig.setPath("/*");
        defaultAuthConfig.setAuthenticationProviderFactory(
                vertx -> PropertyFileAuthentication.create(vertx, DEFAULT_VERTX_PROPERTIES_FILE));
        defaultAuthConfig.setAuthenticationHandlerFactory(BasicAuthHandler::create);
        this.entries = new ArrayList<>();
        this.entries.add(defaultAuthConfig);
    }

    public AuthenticationConfig(List<AuthenticationConfigEntry> authenticationConfigEntries) {
        this.entries = authenticationConfigEntries;
    }

    public List<AuthenticationConfigEntry> getEntries() {
        return entries;
    }

    public boolean isEnabled() {
        return authenticationEnabled;
    }

    public void setEnabled(boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
    }

    public interface AuthenticationProviderFactory {
        AuthenticationProvider createAuthenticationProvider(Vertx vertx);
    }

    public interface AuthenticationHandlerFactory {
        AuthenticationHandler createAuthenticationHandler(AuthenticationProvider authenticationProvider);
    }

    public static class AuthenticationConfigEntry {
        private String path;
        private AuthenticationProviderFactory authenticationProviderFactory;
        private AuthenticationHandlerFactory authenticationHandlerFactory;

        public Handler<RoutingContext> createAuthenticationHandler(Vertx vertx) {
            AuthenticationProvider provider = authenticationProviderFactory.createAuthenticationProvider(vertx);
            return authenticationHandlerFactory.createAuthenticationHandler(provider);
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public AuthenticationProviderFactory getAuthenticationProviderFactory() {
            return authenticationProviderFactory;
        }

        public void setAuthenticationProviderFactory(AuthenticationProviderFactory authenticationProviderFactory) {
            this.authenticationProviderFactory = authenticationProviderFactory;
        }

        public AuthenticationHandlerFactory getAuthenticationHandlerFactory() {
            return authenticationHandlerFactory;
        }

        public void setAuthenticationHandlerFactory(AuthenticationHandlerFactory authenticationHandlerFactory) {
            this.authenticationHandlerFactory = authenticationHandlerFactory;
        }
    }

}
