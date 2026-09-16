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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a Rego policy hosted by an OPA server against an {@link Exchange} and records the decision on it.
 * <p/>
 * Shared by the {@code opa:} producer and by {@code OpaSecurityPolicy} so that both build the same input document and
 * read the verdict the same way.
 */
public abstract class OpaPolicyEvaluator {

    private static final Logger LOG = LoggerFactory.getLogger(OpaPolicyEvaluator.class);

    private static final String ALL_NAMES = "*";

    /**
     * Headers that carry a caller credential verbatim. They are withheld when {@code includeHeaders} is the wildcard,
     * because OPA's decision logging ships the whole input document - often off the box - and a policy that needs a
     * credential should say so by naming the header. Listing one explicitly still sends it.
     */
    private static final Set<String> CREDENTIAL_HEADERS = credentialHeaders();

    private final String policyPath;
    private final String allowKey;
    private final Set<String> includedHeaders;
    private final Set<String> includedProperties;
    private final boolean includeBody;
    private final boolean failOpen;
    private final AtomicBoolean unreadableVerdictWarned = new AtomicBoolean();

    protected OpaPolicyEvaluator(String policyPath, String allowKey, String includeHeaders,
                                 String includeProperties, boolean includeBody, boolean failOpen) {
        this.policyPath = ObjectHelper.notNull(policyPath, "policyPath");
        this.allowKey = ObjectHelper.isNotEmpty(allowKey) ? allowKey : "allow";
        // headers default to all of them, exchange properties to none: properties are mostly used to carry
        // state between processors, so sending them all would be noise the policy has to wade through
        this.includedHeaders = parseNameFilter(includeHeaders, true);
        this.includedProperties = parseNameFilter(includeProperties, false);
        this.includeBody = includeBody;
        this.failOpen = failOpen;
    }

    /**
     * Evaluates the policy for the given exchange and sets the decision headers on it.
     *
     * @param  exchange                     the exchange to build the OPA input document from
     * @return                              true when the policy allows the exchange to proceed
     * @throws OpaPolicyEvaluationException when the policy could not be evaluated and {@code failOpen} is false
     */
    public boolean evaluate(Exchange exchange) throws OpaPolicyEvaluationException {
        // a verdict the message arrived with is a claim, not evidence. Clear it before deciding anything, so that
        // every way out of this method - allowed, denied, or a failure the route goes on to handle - leaves only
        // what this component decided. Overwriting at the end is not enough: the paths that throw never get there,
        // and a route that handles the exception would resume routing with the sender's own verdict still on it
        clearDecisionHeaders(exchange);
        Object decision;
        try {
            decision = evaluateDecision(buildInput(exchange));
        } catch (InterruptedException e) {
            // not a policy failure but a shutdown, so failOpen must not turn it into an allow: nothing decided
            // that this exchange was permitted. Restore the flag the interruptible wait cleared, then fail closed
            Thread.currentThread().interrupt();
            throw new OpaPolicyEvaluationException(
                    "Interrupted while evaluating policy " + policyPath, exchange, e);
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
                    "Failed to evaluate policy " + policyPath, exchange, e);
        }

        boolean allowed = isAllowed(decision);
        setDecisionHeaders(exchange, decision, allowed);
        return allowed;
    }

    /**
     * Evaluates the policy for the given input document and returns the raw decision.
     * <p/>
     * The only thing an engine has to supply. Everything that decides what a route sees - how the input document is
     * built, how the verdict is read out of the decision, what the headers say, and what happens when no verdict can be
     * reached - lives in this class, so a policy behaves identically whichever engine evaluated it.
     *
     * @param  input     the input document
     * @return           the decision document, already unwrapped to plain JSON types
     * @throws Exception when no decision could be reached; the caller turns this into a fail-closed error, or an allow
     *                   when {@code failOpen} is set
     */
    protected abstract Object evaluateDecision(Map<String, Object> input) throws Exception;

    protected String getPolicyPath() {
        return policyPath;
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
            if (isDecisionHeader(name) || !isIncluded(includedHeaders, name) || isWithheldCredential(name)) {
                continue;
            }
            Object value = toJsonSafe(exchange, entry.getValue());
            if (value != null) {
                headers.put(name, value);
            }
        }
        input.put("headers", headers);
        if (includesAnything(includedProperties)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : exchange.getProperties().entrySet()) {
                if (!isIncluded(includedProperties, entry.getKey())) {
                    continue;
                }
                Object value = toJsonSafe(exchange, entry.getValue());
                if (value != null) {
                    properties.put(entry.getKey(), value);
                }
            }
            // only expose "properties" when something was actually collected, so a Rego policy doing
            // has(input, "properties") is not misled into seeing an (empty) identity that is not there
            if (!properties.isEmpty()) {
                input.put("properties", properties);
            }
        }
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
        if (readVerdict(decision) instanceof Boolean b) {
            return b;
        }
        // a decision document we cannot read a verdict from is a configuration problem, not a routine deny, and the
        // route cannot tell the two apart from the verdict header alone - so say so at WARN. Once only: a policy
        // written as deny[msg] without a `default allow := false` reaches this on every legitimate deny, which on a
        // busy route would be a log flood carrying a whole decision document per message.
        if (unreadableVerdictWarned.compareAndSet(false, true)) {
            LOG.warn("Policy {} returned a decision with no boolean '{}' verdict, denying. Check that allowKey"
                     + " matches the shape the policy returns; the raw document is on the {} header. Logged once"
                     + " per evaluator - later occurrences are at DEBUG.",
                    policyPath, allowKey, OpaConstants.DECISION);
        }
        LOG.debug("Policy {} returned no boolean '{}' verdict, denying. Decision: {}", policyPath, allowKey, decision);
        return false;
    }

    /**
     * Reads {@code allowKey} out of the decision document, walking a dotted path so a verdict nested inside the result
     * - {@code allowKey=result.allow} against <code>{"result": {"allow": true}}</code> - can be reached. A key with no
     * dot is looked up directly, exactly as before.
     */
    private Object readVerdict(Object decision) {
        if (!(decision instanceof Map<?, ?> top)) {
            return null;
        }
        // a top-level entry under the whole key wins over walking it as a path, so an allowKey that itself
        // contains a dot resolves exactly as it did before dotted paths were understood
        Object direct = top.get(allowKey);
        if (direct != null) {
            return direct;
        }
        Object current = decision;
        for (String segment : allowKey.split("\\.", -1)) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static void clearDecisionHeaders(Exchange exchange) {
        Message message = exchange.getMessage();
        message.removeHeader(OpaConstants.DECISION_ALLOW);
        message.removeHeader(OpaConstants.DECISION);
        message.removeHeader(OpaConstants.POLICY_PATH);
    }

    private void setDecisionHeaders(Exchange exchange, Object decision, boolean allowed) {
        // set unconditionally so that a verdict claimed by an inbound message is always replaced
        exchange.getMessage().setHeader(OpaConstants.DECISION_ALLOW, allowed);
        exchange.getMessage().setHeader(OpaConstants.DECISION, decision);
        exchange.getMessage().setHeader(OpaConstants.POLICY_PATH, policyPath);
    }

    private static boolean isIncluded(Set<String> filter, String name) {
        return filter == null || filter.contains(name);
    }

    private static boolean includesAnything(Set<String> filter) {
        return filter == null || !filter.isEmpty();
    }

    /**
     * A credential header is only sent when the configuration names it, never through the wildcard.
     */
    private boolean isWithheldCredential(String name) {
        return includedHeaders == null && CREDENTIAL_HEADERS.contains(name);
    }

    private static Set<String> credentialHeaders() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.add("Authorization");
        names.add("Proxy-Authorization");
        names.add("Cookie");
        names.add("Set-Cookie");
        return Collections.unmodifiableSet(names);
    }

    private static boolean isDecisionHeader(String name) {
        return OpaConstants.DECISION_ALLOW.equalsIgnoreCase(name)
                || OpaConstants.DECISION.equalsIgnoreCase(name)
                || OpaConstants.POLICY_PATH.equalsIgnoreCase(name);
    }

    /**
     * Parses a comma-separated name filter.
     *
     * @param  names         the configured value, or {@code *} for everything
     * @param  emptyMeansAll whether leaving the option unset includes every name or none of them
     * @return               the names to include, or null when every name is included
     */
    private static Set<String> parseNameFilter(String names, boolean emptyMeansAll) {
        if (ObjectHelper.isEmpty(names)) {
            return emptyMeansAll ? null : Set.of();
        }
        if (ALL_NAMES.equals(names.trim())) {
            return null;
        }
        Set<String> filter = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : names.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                filter.add(trimmed);
            }
        }
        return filter;
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
