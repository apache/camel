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
package org.apache.camel.component.keycloak.security;

import java.util.Set;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.processor.DelegateProcessor;
import org.apache.camel.util.ObjectHelper;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakSecurityProcessor extends DelegateProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityProcessor.class);

    private final KeycloakSecurityPolicy policy;

    public KeycloakSecurityProcessor(Processor processor, KeycloakSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        beforeProcess(exchange);
        processNext(exchange);
    }

    protected void beforeProcess(Exchange exchange) throws Exception {
        try {
            String accessToken = getAccessToken(exchange);

            if (accessToken == null) {
                throw new CamelAuthorizationException("Access token not found in exchange", exchange);
            }

            if (!policy.getRequiredRoles().isEmpty()) {
                validateRoles(accessToken, exchange);
            }

        } catch (Exception e) {
            exchange.getIn().setHeader(Exchange.AUTHENTICATION_FAILURE_POLICY_ID,
                    policy.getClass().getSimpleName());
            if (e instanceof CamelAuthorizationException) {
                throw e;
            }
            throw new CamelAuthorizationException("Authorization failed", exchange, e);
        }
    }

    private String getAccessToken(Exchange exchange) {
        // Try to get token from header first
        String token = exchange.getIn().getHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, String.class);

        if (token == null) {
            // Try to get from Authorization header
            String authHeader = exchange.getIn().getHeader("Authorization", String.class);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null) {
            // Try to get from exchange property
            token = exchange.getProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, String.class);
        }

        return token;
    }

    private void validateRoles(String accessToken, Exchange exchange) throws Exception {
        try {
            AccessToken token;
            if (ObjectHelper.isEmpty(policy.getPublicKey())) {
                token = KeycloakSecurityHelper.parseAccessToken(accessToken);
            } else {
                token = KeycloakSecurityHelper.parseAccessToken(accessToken, policy.getPublicKey());
            }
            Set<String> userRoles = KeycloakSecurityHelper.extractRoles(token, policy.getRealm(), policy.getClientId());

            boolean hasRequiredRoles = policy.isAllRolesRequired()
                    ? userRoles.containsAll(policy.getRequiredRoles())
                    : policy.getRequiredRoles().stream().anyMatch(userRoles::contains);

            if (!hasRequiredRoles) {
                String message = String.format("User does not have required roles. Required: %s, User has: %s",
                        policy.getRequiredRoles(), userRoles);
                LOG.debug(message);
                throw new CamelAuthorizationException(message, exchange);
            }

            LOG.debug("Role validation successful for user with roles: {}", userRoles);

        } catch (Exception e) {
            if (e instanceof CamelAuthorizationException) {
                throw e;
            }
            throw new CamelAuthorizationException("Failed to validate roles", exchange, e);
        }
    }

}
