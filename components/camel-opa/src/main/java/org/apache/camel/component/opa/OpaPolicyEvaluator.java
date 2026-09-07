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
package org.apache.camel.component.opa;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.styra.opa.OPAClient;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a Rego policy hosted by an OPA server against an {@link Exchange} and records the decision on it.
 * <p/>
 * Shared by the {@code opa:} producer and by {@code OpaSecurityPolicy} so that both build the same input document and
 * read the verdict the same way.
 */
public class OpaPolicyEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(OpaPolicyEvaluator.class);

    private static final String ALL_HEADERS = "*";

    private final OPAClient client;
    private final String policyPath;
    private final String allowKey;
    private final Set<String> includedHeaders;
    private final boolean includeBody;
    private final boolean failOpen;

    public OpaPolicyEvaluator(OPAClient client, String policyPath, String allowKey, String includeHeaders,
                              boolean includeBody, boolean failOpen) {
        this.client = ObjectHelper.notNull(client, "client");
        this.policyPath = ObjectHelper.notNull(policyPath, "policyPath");
        this.allowKey = ObjectHelper.isNotEmpty(allowKey) ? allowKey : "allow";
        this.includedHeaders = parseIncludedHeaders(includeHeaders);
        this.includeBody = includeBody;
        this.failOpen = failOpen;
    }

    /**
     * Creates a client for an OPA server, optionally authenticating with a bearer token.
     *
     * @param serverUrl   base URL of the OPA server, without the /v1/data suffix
     * @param bearerToken token for OPA API authentication, or null when OPA does not require one
     */
    public static OPAClient createClient(String serverUrl, String bearerToken) {
        if (ObjectHelper.isNotEmpty(bearerToken)) {
            return new OPAClient(serverUrl, Map.of("Authorization", "Bearer " + bearerToken));
        }
        return new OPAClient(serverUrl);
    }

    /**
     * Evaluates the policy for the given exchange and sets the decision headers on it.
     *
     * @param  exchange                     the exchange to build the OPA input document from
     * @return                              true when the policy allows the exchange to proceed
     * @throws OpaPolicyEvaluationException when the policy could not be evaluated and {@code failOpen} is false
     */
    public boolean evaluate(Exchange exchange) throws OpaPolicyEvaluationException {
        Object decision;
        try {
            decision = client.evaluate(policyPath, buildInput(exchange), Object.class);
        } catch (Exception e) {
            // any failure to reach a verdict is handled the same way, whether it comes from the OPA server
            // (OPAException) or from building and serializing the input document; fail-closed must not depend
            // on which layer gave up
            if (failOpen) {
                LOG.warn("Policy {} could not be evaluated, allowing the exchange to proceed because failOpen is"
                         + " enabled. Reason: {}",
                        policyPath, e.getMessage());
                setDecisionHeaders(exchange, null, true);
                return true;
            }
            throw new OpaPolicyEvaluationException(
                    "Failed to evaluate policy " + policyPath + " at the OPA server", exchange, e);
        }

        boolean allowed = isAllowed(decision);
        setDecisionHeaders(exchange, decision, allowed);
        return allowed;
    }

    /**
     * Builds the {@code input} document handed to OPA.
     */
    protected Map<String, Object> buildInput(Exchange exchange) {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : exchange.getMessage().getHeaders().entrySet()) {
            String name = entry.getKey();
            // never feed our own decision headers back in: a policy must not be able to read a verdict
            // that an inbound message claimed for itself
            if (isDecisionHeader(name) || !isIncluded(name)) {
                continue;
            }
            Object value = toJsonSafe(exchange, entry.getValue());
            if (value != null) {
                headers.put(name, value);
            }
        }
        input.put("headers", headers);
        if (includeBody) {
            input.put("body", toJsonSafe(exchange, exchange.getMessage().getBody()));
        }
        input.put("exchangeId", exchange.getExchangeId());
        if (exchange.getFromRouteId() != null) {
            input.put("routeId", exchange.getFromRouteId());
        }
        return input;
    }

    /**
     * Reads the allow/deny verdict out of the decision document.
     * <p/>
     * Only a JSON boolean counts as an allow, either as the whole document or as the {@code allowKey} entry of an
     * object. Anything else cannot be read as a verdict and is treated as a deny, with the raw document still available
     * on the exchange so the route can inspect it.
     */
    protected boolean isAllowed(Object decision) {
        if (decision instanceof Boolean b) {
            return b;
        }
        if (decision instanceof Map<?, ?> map && map.get(allowKey) instanceof Boolean b) {
            return b;
        }
        LOG.debug("Policy {} returned a decision with no boolean '{}' verdict, denying. Decision: {}",
                policyPath, allowKey, decision);
        return false;
    }

    private void setDecisionHeaders(Exchange exchange, Object decision, boolean allowed) {
        // set unconditionally so that a verdict claimed by an inbound message is always replaced
        exchange.getMessage().setHeader(OpaConstants.DECISION_ALLOW, allowed);
        exchange.getMessage().setHeader(OpaConstants.DECISION, decision);
        exchange.getMessage().setHeader(OpaConstants.POLICY_PATH, policyPath);
    }

    private boolean isIncluded(String name) {
        return includedHeaders == null || includedHeaders.contains(name);
    }

    private static boolean isDecisionHeader(String name) {
        return OpaConstants.DECISION_ALLOW.equalsIgnoreCase(name)
                || OpaConstants.DECISION.equalsIgnoreCase(name)
                || OpaConstants.POLICY_PATH.equalsIgnoreCase(name);
    }

    /**
     * @return the header names to include, or null when every header is included
     */
    private static Set<String> parseIncludedHeaders(String includeHeaders) {
        if (ObjectHelper.isEmpty(includeHeaders) || ALL_HEADERS.equals(includeHeaders.trim())) {
            return null;
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : includeHeaders.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    /**
     * Converts a value into something the JSON serializer of the OPA SDK can handle. Anything that is not already a
     * JSON-native type is converted to its string representation, and dropped when it cannot be converted.
     * <p/>
     * The conversion is shallow: a {@link Map} or {@link List} is passed through as-is, so any non-JSON-native value
     * nested inside it is left for the SDK serializer to render. Policies that read nested structures should not assume
     * the same string conversion applies at depth.
     */
    private static Object toJsonSafe(Exchange exchange, Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Map || value instanceof List) {
            return value;
        }
        return exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, value);
    }
}
