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
package org.apache.camel.component.servicenow.auth;

import java.util.concurrent.TimeUnit;

import org.apache.camel.component.servicenow.ServiceNowConfiguration;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.rs.security.oauth2.client.Consumer;
import org.apache.cxf.rs.security.oauth2.client.OAuthClientUtils;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.grants.owner.ResourceOwnerGrant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthToken {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthToken.class);

    private final ServiceNowConfiguration configuration;
    private ClientAccessToken token;
    private String authString;
    private long expireAt;

    public OAuthToken(ServiceNowConfiguration configuration) {
        this.configuration = configuration;
        this.token = null;
        this.authString = null;
        this.expireAt = 0;
    }

    private synchronized void getOrRefreshAccessToken() {
        if (token == null) {
            LOGGER.debug("Generate OAuth token");

            token = OAuthClientUtils.getAccessToken(
                WebClient.create(configuration.getOauthTokenUrl()),
                new Consumer(
                    configuration.getOauthClientId(),
                    configuration.getOauthClientSecret()),
                new ResourceOwnerGrant(
                    configuration.getUserName(),
                    configuration.getPassword()),
                true
            );

            LOGGER.debug("OAuth token expires in {}s", token.getExpiresIn());

            // Set expiration time related info in milliseconds
            token.setIssuedAt(System.currentTimeMillis());
            token.setExpiresIn(TimeUnit.MILLISECONDS.convert(token.getExpiresIn(), TimeUnit.SECONDS));

            authString = token.toString();

            if (token.getExpiresIn() > 0) {
                expireAt = token.getIssuedAt() + token.getExpiresIn();
            }
        } else if (expireAt > 0 && System.currentTimeMillis() >= expireAt) {
            LOGGER.debug("OAuth token is expired, refresh it");

            token = OAuthClientUtils.refreshAccessToken(
                WebClient.create(configuration.getOauthTokenUrl()),
                new Consumer(
                    configuration.getOauthClientId(),
                    configuration.getOauthClientSecret()),
                token,
                null,
                false
            );

            LOGGER.debug("Refreshed OAuth token expires in {}s", token.getExpiresIn());

            // Set expiration time related info in milliseconds
            token.setIssuedAt(System.currentTimeMillis());
            token.setExpiresIn(TimeUnit.MILLISECONDS.convert(token.getExpiresIn(), TimeUnit.SECONDS));

            authString = token.toString();

            if (token.getExpiresIn() > 0) {
                expireAt = token.getIssuedAt() + token.getExpiresIn();
            }
        }
    }

    public ClientAccessToken getClientAccess() {
        getOrRefreshAccessToken();
        return token;
    }

    public String getAuthString() {
        getOrRefreshAccessToken();
        return authString;
    }
}
