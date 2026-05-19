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
package org.apache.camel.component.a2a.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.A2AConfiguration;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.SecurityRequirement;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authentication coordinator for the A2A component. Delegates to {@link A2ASecuritySchemeHandler} implementations based
 * on the agent card's declared security schemes and security requirements.
 */
public class A2AAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(A2AAuthHandler.class);

    private final A2AConfiguration configuration;
    private final Map<String, A2ASecuritySchemeHandler> handlers;

    public A2AAuthHandler(A2AConfiguration configuration, Map<String, A2ASecuritySchemeHandler> handlers) {
        this.configuration = configuration;
        this.handlers = handlers;
    }

    /**
     * Apply authentication headers to a producer exchange. Selects the appropriate scheme handler based on the resolved
     * card's security schemes and configuration hints.
     */
    public void applyProducerAuth(Exchange exchange, CamelContext context) {
        applyProducerAuth(exchange, context, null);
    }

    /**
     * Apply authentication headers to a producer exchange using the given card's security schemes.
     */
    public void applyProducerAuth(Exchange exchange, CamelContext context, AgentCard card) {
        resolveProducerAuth(exchange, context, card, false);
    }

    /**
     * Resolves producer credentials without leaving secrets on the route exchange.
     */
    public ProducerAuth resolveProducerAuth(Exchange exchange, CamelContext context, AgentCard card) {
        return resolveProducerAuth(exchange, context, card, true);
    }

    private ProducerAuth resolveProducerAuth(
            Exchange exchange, CamelContext context, AgentCard card, boolean restoreHeaders) {
        List<SecurityRequirement> requirements = resolveRequirements(card);
        if (requirements.isEmpty()) {
            LOG.debug("No A2A security requirements found — request will be unauthenticated");
            return ProducerAuth.empty();
        }

        RuntimeException lastFailure = null;
        for (SecurityRequirement requirement : requirements) {
            Map<String, Object> originalHeaders = snapshotHeaders(exchange);
            try {
                ProducerAuth auth = applyProducerRequirement(exchange, context, card, requirement);
                if (restoreHeaders) {
                    restoreHeaders(exchange, originalHeaders);
                }
                return auth;
            } catch (RuntimeException e) {
                lastFailure = e;
                restoreHeaders(exchange, originalHeaders);
            }
        }

        throw new RuntimeCamelException(
                "No A2A security requirement could be satisfied"
                                        + (lastFailure != null ? ": " + lastFailure.getMessage() : ""),
                lastFailure);
    }

    /**
     * Validate credentials from an incoming consumer request. Returns a user profile on success, or null if
     * {@code validateAuth} is disabled. Throws {@link SecurityException} if validation fails.
     */
    public A2AUserProfile validateConsumerAuth(Exchange exchange, AgentCard card) {
        if (!configuration.isValidateAuth()) {
            return null;
        }

        List<SecurityRequirement> requirements = resolveRequirements(card);
        if (requirements.isEmpty()) {
            throw new SecurityException(
                    "validateAuth is enabled but agent card declares no security requirements — rejecting request (fail closed)");
        }

        SecurityException lastFailure = null;
        for (SecurityRequirement requirement : requirements) {
            try {
                return validateRequirement(exchange, card, requirement);
            } catch (SecurityException e) {
                lastFailure = e;
            }
        }

        throw lastFailure != null
                ? lastFailure
                : new SecurityException("No security requirement could be satisfied");
    }

    private ProducerAuth applyProducerRequirement(
            Exchange exchange, CamelContext context, AgentCard card, SecurityRequirement requirement) {
        Map<String, List<String>> requirementSchemes = requirement.asScopeMap();
        if (requirementSchemes.isEmpty()) {
            return ProducerAuth.empty();
        }

        Map<String, Object> initialHeaders = snapshotHeaders(exchange);
        ProducerAuth auth = new ProducerAuth();
        for (String schemeName : requirementSchemes.keySet()) {
            SecurityScheme scheme = resolveScheme(card, schemeName);
            A2ASecuritySchemeHandler handler = resolveHandler(schemeName, scheme);
            Map<String, Object> beforeSchemeHeaders = snapshotHeaders(exchange);
            Set<String> credentialHeaderNames = credentialHeaderNames(scheme);
            boolean satisfied = false;

            if (isQueryApiKey(scheme)) {
                String apiKey = configuration.getApiKey();
                if (apiKey != null) {
                    auth.addQueryParameter(resolveApiKeyName(scheme), apiKey);
                    satisfied = true;
                }
            } else {
                handler.applyCredentials(exchange, scheme, configuration, context);
            }

            copyHeaders(exchange, auth, credentialHeaderNames);
            satisfied = satisfied || hasAnyHeader(exchange, credentialHeaderNames)
                    || hasHeaderMutations(exchange, beforeSchemeHeaders);
            if (!satisfied) {
                throw new RuntimeCamelException("No producer credentials available for A2A security scheme " + schemeName);
            }
        }

        copyMutatedStringHeaders(exchange, auth, initialHeaders);
        return auth;
    }

    private A2AUserProfile validateRequirement(Exchange exchange, AgentCard card, SecurityRequirement requirement) {
        Map<String, List<String>> requirementSchemes = requirement.asScopeMap();
        if (requirementSchemes.isEmpty()) {
            throw new SecurityException("Empty security requirement cannot satisfy validateAuth=true");
        }

        A2AUserProfile selectedProfile = null;
        for (Map.Entry<String, List<String>> entry : requirementSchemes.entrySet()) {
            String schemeName = entry.getKey();
            SecurityScheme scheme = resolveScheme(card, schemeName);
            A2ASecuritySchemeHandler handler = resolveHandler(schemeName, scheme);
            A2AUserProfile profile = handler.validateCredentials(exchange, scheme, configuration);
            validateScopes(schemeName, entry.getValue(), profile);
            selectedProfile = selectProfile(selectedProfile, profile);
        }
        return selectedProfile;
    }

    private SecurityScheme resolveScheme(AgentCard card, String schemeName) {
        if (card == null || card.getSecuritySchemes() == null || !card.getSecuritySchemes().containsKey(schemeName)) {
            throw new SecurityException("A2A security requirement references missing scheme: " + schemeName);
        }
        SecurityScheme scheme = card.getSecuritySchemes().get(schemeName);
        scheme.validate();
        return scheme;
    }

    private A2ASecuritySchemeHandler resolveHandler(String schemeName, SecurityScheme scheme) {
        A2ASecuritySchemeHandler handler = handlers.get(scheme.getType());
        if (handler == null) {
            throw new SecurityException(
                    "No security scheme handler available for A2A scheme " + schemeName + " of type " + scheme.getType());
        }
        return handler;
    }

    private static void validateScopes(String schemeName, List<String> requiredScopes, A2AUserProfile profile) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return;
        }
        if (profile == null || profile.getScopes().isEmpty()) {
            throw new SecurityException(
                    "A2A security scheme " + schemeName + " requires scopes that cannot be verified");
        }
        if (!profile.getScopes().containsAll(requiredScopes)) {
            throw new SecurityException(
                    "A2A security scheme " + schemeName + " is missing required scopes: " + requiredScopes);
        }
    }

    private static A2AUserProfile selectProfile(A2AUserProfile current, A2AUserProfile candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate != null && candidate.ownerKey() != null && current.ownerKey() == null) {
            return candidate;
        }
        return current;
    }

    /**
     * Resolves and prioritizes security schemes from the card, using config hints to determine order. If
     * {@code oauthProfile} is configured, OAuth2/OIDC schemes come first. If {@code bearerToken} is configured, HTTP
     * bearer schemes come first. If {@code apiKey} is configured, API key schemes come first.
     */
    List<Map.Entry<String, SecurityScheme>> resolveSchemes(AgentCard card) {
        if (card == null || card.getSecuritySchemes() == null || card.getSecuritySchemes().isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, SecurityScheme>> all = new ArrayList<>(card.getSecuritySchemes().entrySet());
        Set<String> preferredTypes = resolvePreferredSchemeTypes();
        if (!preferredTypes.isEmpty()) {
            all.sort((a, b) -> {
                boolean aMatch = preferredTypes.contains(a.getValue().getType());
                boolean bMatch = preferredTypes.contains(b.getValue().getType());
                if (aMatch == bMatch) {
                    return 0;
                }
                return aMatch ? -1 : 1;
            });
        }
        return all;
    }

    private List<SecurityRequirement> resolveRequirements(AgentCard card) {
        if (card == null || card.getSecuritySchemes() == null || card.getSecuritySchemes().isEmpty()) {
            return Collections.emptyList();
        }
        if (card.getSecurityRequirements() != null && !card.getSecurityRequirements().isEmpty()) {
            return card.getSecurityRequirements();
        }

        List<SecurityRequirement> requirements = new ArrayList<>();
        for (Map.Entry<String, SecurityScheme> entry : resolveSchemes(card)) {
            requirements.add(SecurityRequirement.of(entry.getKey(), List.of()));
        }
        return requirements;
    }

    private Set<String> resolvePreferredSchemeTypes() {
        if (configuration.getOauthProfile() != null) {
            return Set.of("oauth2", "openIdConnect");
        }
        if (configuration.getBearerToken() != null) {
            return Set.of("http");
        }
        if (configuration.getApiKey() != null) {
            return Set.of("apiKey");
        }
        return Set.of();
    }

    private Set<String> credentialHeaderNames(SecurityScheme scheme) {
        Set<String> headers = new LinkedHashSet<>();
        switch (scheme.getType()) {
            case "apiKey" -> {
                String location = resolveApiKeyLocation(scheme);
                if ("header".equals(location)) {
                    headers.add(resolveApiKeyName(scheme));
                } else if ("cookie".equals(location)) {
                    headers.add("Cookie");
                }
            }
            case "http", "oauth2", "openIdConnect" -> headers.add("Authorization");
            default -> {
            }
        }
        return headers;
    }

    private boolean isQueryApiKey(SecurityScheme scheme) {
        return "apiKey".equals(scheme.getType()) && "query".equals(resolveApiKeyLocation(scheme));
    }

    private String resolveApiKeyLocation(SecurityScheme scheme) {
        String location = scheme.getLocation();
        return location != null ? location : "header";
    }

    private String resolveApiKeyName(SecurityScheme scheme) {
        String name = scheme.getName();
        return name != null && !name.isBlank() ? name : configuration.getApiKeyHeader();
    }

    private static Map<String, Object> snapshotHeaders(Exchange exchange) {
        return new LinkedHashMap<>(exchange.getMessage().getHeaders());
    }

    private static void restoreHeaders(Exchange exchange, Map<String, Object> headers) {
        exchange.getMessage().getHeaders().clear();
        exchange.getMessage().getHeaders().putAll(headers);
    }

    private static void copyHeaders(Exchange exchange, ProducerAuth auth, Set<String> headerNames) {
        for (String headerName : headerNames) {
            String value = exchange.getMessage().getHeader(headerName, String.class);
            if (value != null) {
                auth.addHeader(headerName, value);
            }
        }
    }

    private static void copyMutatedStringHeaders(
            Exchange exchange, ProducerAuth auth, Map<String, Object> originalHeaders) {
        for (Map.Entry<String, Object> entry : exchange.getMessage().getHeaders().entrySet()) {
            if (entry.getValue() instanceof String value
                    && !Objects.equals(originalHeaders.get(entry.getKey()), entry.getValue())) {
                auth.addHeader(entry.getKey(), value);
            }
        }
    }

    private static boolean hasAnyHeader(Exchange exchange, Set<String> headerNames) {
        for (String headerName : headerNames) {
            if (exchange.getMessage().getHeader(headerName) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasHeaderMutations(Exchange exchange, Map<String, Object> originalHeaders) {
        Map<String, Object> currentHeaders = exchange.getMessage().getHeaders();
        if (!currentHeaders.keySet().equals(originalHeaders.keySet())) {
            return true;
        }
        for (Map.Entry<String, Object> entry : currentHeaders.entrySet()) {
            if (!Objects.equals(originalHeaders.get(entry.getKey()), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static final class ProducerAuth {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final Map<String, String> queryParameters = new LinkedHashMap<>();

        private static ProducerAuth empty() {
            return new ProducerAuth();
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }

        public Map<String, String> getQueryParameters() {
            return Collections.unmodifiableMap(queryParameters);
        }

        private void addHeader(String name, String value) {
            headers.put(name, value);
        }

        private void addQueryParameter(String name, String value) {
            queryParameters.put(name, value);
        }
    }
}
