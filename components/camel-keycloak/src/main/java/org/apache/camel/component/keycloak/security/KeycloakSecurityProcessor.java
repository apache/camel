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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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

            if (!policy.getRequiredRolesAsList().isEmpty()) {
                validateRoles(accessToken, exchange);
            }

            if (!policy.getRequiredPermissionsAsList().isEmpty()) {
                validatePermissions(accessToken, exchange);
            }

        } catch (Exception e) {
            exchange.getIn()
                    .setHeader(
                            Exchange.AUTHENTICATION_FAILURE_POLICY_ID,
                            policy.getClass().getSimpleName());
            if (e instanceof CamelAuthorizationException) {
                throw e;
            }
            throw new CamelAuthorizationException("Authorization failed", exchange, e);
        }
    }

    private String getAccessToken(Exchange exchange) throws Exception {
        // Get token from exchange property (application-controlled, TRUSTED)
        String propertyToken = exchange.getProperty(KeycloakSecurityConstants.ACCESS_TOKEN_PROPERTY, String.class);
        String headerToken = null;

        // Get token from headers only if allowed by policy
        if (policy.isAllowTokenFromHeader()) {
            headerToken = exchange.getIn().getHeader(KeycloakSecurityConstants.ACCESS_TOKEN_HEADER, String.class);

            if (headerToken == null) {
                // Try to get from Authorization header
                String authHeader = exchange.getIn().getHeader("Authorization", String.class);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    headerToken = authHeader.substring(7);
                }
            }
        }

        // Determine which token to use based on policy
        String token;
        boolean isHeaderSource = false;

        if (policy.isPreferPropertyOverHeader()) {
            // SECURE DEFAULT: Property (trusted) takes precedence over header (untrusted)
            if (propertyToken != null) {
                token = propertyToken;
                LOG.debug("Using token from exchange property (preferred source)");
            } else if (headerToken != null) {
                token = headerToken;
                isHeaderSource = true;
                LOG.warn(
                        "Using token from HTTP header - this may be a security risk. "
                                + "Consider setting tokens via exchange properties instead to prevent token injection attacks.");
            } else {
                token = null;
            }
        } else {
            // LEGACY MODE (LESS SECURE): Header takes precedence - maintains backward compatibility
            if (headerToken != null) {
                token = headerToken;
                isHeaderSource = true;
                LOG.warn("Token from header takes precedence over property - this may allow token override attacks. "
                        + "Consider setting preferPropertyOverHeader=true for better security.");
            } else if (propertyToken != null) {
                token = propertyToken;
                LOG.debug("Using token from exchange property");
            } else {
                token = null;
            }
        }

        // Validate token binding if enabled and token came from headers
        if (token != null && policy.isValidateTokenBinding() && isHeaderSource) {
            validateTokenBinding(exchange, token, propertyToken);
        }

        return token;
    }

    /**
     * Validates that a token from headers matches the session-bound token to prevent session fixation attacks. This
     * method performs three levels of validation: 1. Token exact match - header token must match property token if both
     * exist 2. Subject validation - token subject (user ID) must match stored subject 3. Thumbprint validation - token
     * integrity check via SHA-256 hash
     *
     * @param  exchange                    the current exchange
     * @param  headerToken                 token retrieved from HTTP headers
     * @param  propertyToken               token stored in exchange properties (session-bound)
     * @throws CamelAuthorizationException if token binding validation fails
     */
    private void validateTokenBinding(Exchange exchange, String headerToken, String propertyToken) throws Exception {

        // Level 1: Exact token match validation
        if (propertyToken != null && !propertyToken.equals(headerToken)) {
            LOG.error(
                    "SECURITY: Token binding validation failed - header token does not match session-bound property token");
            throw new CamelAuthorizationException(
                    "Token mismatch detected - possible session fixation or token injection attack", exchange);
        }

        // Level 2: Subject (user ID) validation
        String storedSubject = exchange.getProperty(KeycloakSecurityConstants.TOKEN_SUBJECT_PROPERTY, String.class);
        if (storedSubject != null) {
            try {
                // Parse token to extract subject (without full validation - just for binding check)
                AccessToken accessToken = KeycloakSecurityHelper.parseAccessToken(headerToken);
                String currentSubject = accessToken.getSubject();

                if (!storedSubject.equals(currentSubject)) {
                    LOG.error(
                            "SECURITY: Token subject mismatch - expected user '{}' but token is for user '{}'",
                            storedSubject,
                            currentSubject);
                    throw new CamelAuthorizationException(
                            "Token subject mismatch - token does not belong to current session user", exchange);
                }

                LOG.debug("Token subject validation successful - subject '{}' matches session", storedSubject);
            } catch (Exception e) {
                if (e instanceof CamelAuthorizationException) {
                    throw e;
                }
                LOG.error("SECURITY: Failed to validate token subject binding", e);
                throw new CamelAuthorizationException(
                        "Token binding validation failed - unable to parse token", exchange, e);
            }
        }

        // Level 3: Token thumbprint (integrity) validation
        String storedThumbprint =
                exchange.getProperty(KeycloakSecurityConstants.TOKEN_THUMBPRINT_PROPERTY, String.class);
        if (storedThumbprint != null) {
            String currentThumbprint = calculateTokenThumbprint(headerToken);
            if (!storedThumbprint.equals(currentThumbprint)) {
                LOG.error(
                        "SECURITY: Token thumbprint mismatch - token has been tampered with, replaced, or belongs to different session");
                throw new CamelAuthorizationException(
                        "Token integrity check failed - possible token tampering or replacement attack", exchange);
            }
            LOG.debug("Token thumbprint validation successful - token integrity verified");
        }
    }

    /**
     * Calculates a SHA-256 thumbprint of the token for integrity validation. This provides a cryptographic fingerprint
     * that can detect if a token has been tampered with or replaced.
     *
     * @param  token                 the access token string
     * @return                       Base64-encoded SHA-256 hash of the token
     * @throws IllegalStateException if SHA-256 algorithm is not available (should never happen)
     */
    private String calculateTokenThumbprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available - this should never happen", e);
        }
    }

    private void validateRoles(String accessToken, Exchange exchange) throws Exception {
        try {
            Set<String> userRoles;

            // Use token introspection if enabled
            if (policy.isUseTokenIntrospection() && policy.getTokenIntrospector() != null) {
                KeycloakTokenIntrospector.IntrospectionResult introspectionResult =
                        KeycloakSecurityHelper.introspectToken(accessToken, policy.getTokenIntrospector());

                // Check if token is active
                if (!introspectionResult.isActive()) {
                    throw new CamelAuthorizationException("Token is not active (may be revoked or expired)", exchange);
                }

                userRoles = KeycloakSecurityHelper.extractRolesFromIntrospection(
                        introspectionResult, policy.getRealm(), policy.getClientId());
            } else {
                // Use local JWT parsing
                AccessToken token;
                if (ObjectHelper.isEmpty(policy.getPublicKey())) {
                    token = KeycloakSecurityHelper.parseAccessToken(accessToken);
                } else {
                    token = KeycloakSecurityHelper.parseAccessToken(accessToken, policy.getPublicKey());
                }
                userRoles = KeycloakSecurityHelper.extractRoles(token, policy.getRealm(), policy.getClientId());
            }

            boolean hasRequiredRoles = policy.isAllRolesRequired()
                    ? userRoles.containsAll(policy.getRequiredRolesAsList())
                    : policy.getRequiredRolesAsList().stream().anyMatch(userRoles::contains);

            if (!hasRequiredRoles) {
                String message = String.format(
                        "User does not have required roles. Required: %s, User has: %s",
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

    private void validatePermissions(String accessToken, Exchange exchange) throws Exception {
        try {
            Set<String> userPermissions;

            // Use token introspection if enabled
            if (policy.isUseTokenIntrospection() && policy.getTokenIntrospector() != null) {
                KeycloakTokenIntrospector.IntrospectionResult introspectionResult =
                        KeycloakSecurityHelper.introspectToken(accessToken, policy.getTokenIntrospector());

                // Check if token is active
                if (!introspectionResult.isActive()) {
                    throw new CamelAuthorizationException("Token is not active (may be revoked or expired)", exchange);
                }

                userPermissions = KeycloakSecurityHelper.extractPermissionsFromIntrospection(introspectionResult);
            } else {
                // Use local JWT parsing
                AccessToken token;
                if (ObjectHelper.isEmpty(policy.getPublicKey())) {
                    token = KeycloakSecurityHelper.parseAccessToken(accessToken);
                } else {
                    token = KeycloakSecurityHelper.parseAccessToken(accessToken, policy.getPublicKey());
                }
                userPermissions = KeycloakSecurityHelper.extractPermissions(token);
            }

            boolean hasRequiredPermissions = policy.isAllPermissionsRequired()
                    ? userPermissions.containsAll(policy.getRequiredPermissionsAsList())
                    : policy.getRequiredPermissionsAsList().stream().anyMatch(userPermissions::contains);

            if (!hasRequiredPermissions) {
                String message = String.format(
                        "User does not have required permissions. Required: %s, User has: %s",
                        policy.getRequiredPermissions(), userPermissions);
                LOG.debug(message);
                throw new CamelAuthorizationException(message, exchange);
            }

            LOG.debug("Permission validation successful for user with permissions: {}", userPermissions);

        } catch (Exception e) {
            if (e instanceof CamelAuthorizationException) {
                throw e;
            }
            throw new CamelAuthorizationException("Failed to validate permissions", exchange, e);
        }
    }
}
