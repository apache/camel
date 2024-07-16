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
package org.apache.camel.component.platform.http.main.authentication;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationConfigEntry;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig.AuthenticationHandlerFactory;
import org.apache.camel.main.HttpServerAuthenticationConfigurationProperties;
import org.apache.camel.main.HttpServerJWTAuthenticationConfigurationProperties;

public class JWTAuthenticationConfigurer implements MainAuthenticationConfigurer {
    @Override
    public void configureAuthentication(
            AuthenticationConfig authenticationConfig,
            HttpServerAuthenticationConfigurationProperties authenticationProperties) {
        HttpServerJWTAuthenticationConfigurationProperties properties = authenticationProperties.getJWT();
        String path = authenticationProperties.getPath() != null && !"".equals(authenticationProperties.getPath())
                ? authenticationProperties.getPath() : "/*";

        AuthenticationConfigEntry entry = new AuthenticationConfigEntry();
        entry.setPath(path);
        entry.setAuthenticationHandlerFactory(new AuthenticationHandlerFactory() {
            @Override
            public <T extends AuthenticationProvider> AuthenticationHandler createAuthenticationHandler(
                    T authenticationProvider) {
                JWTAuth authProvider = (JWTAuth) authenticationProvider;
                return JWTAuthHandler.create(authProvider);
            }
        });
        entry.setAuthenticationProviderFactory(vertx -> JWTAuth.create(
                vertx,
                new JWTAuthOptions(
                        new JsonObject().put("keyStore", new JsonObject()
                                .put("type", properties.getKeystoreType())
                                .put("path", properties.getKeystorePath())
                                .put("password", properties.getKeystorePassword())))));

        authenticationConfig.getEntries().add(entry);
        authenticationConfig.setEnabled(true);
    }
}
