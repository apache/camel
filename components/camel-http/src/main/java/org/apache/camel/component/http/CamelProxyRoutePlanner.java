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
package org.apache.camel.component.http;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * A custom HTTP route planner that extends {@link DefaultProxyRoutePlanner} to support selective proxy bypassing based
 * on hostname patterns. This route planner allows certain hosts to bypass the configured proxy server by matching
 * hostnames against a set of patterns. This is similar to the behavior of the Java system property
 * {@code http.nonProxyHosts}.
 *
 * @see DefaultProxyRoutePlanner
 * @see org.apache.hc.client5.http.routing.HttpRoutePlanner
 **/
public class CamelProxyRoutePlanner extends DefaultProxyRoutePlanner {

    /**
     * Set of exact hostname patterns (without wildcards) that should bypass the proxy. Stored in lowercase for
     * case-insensitive matching.
     */
    private final Set<String> exactMatches;

    /**
     * List of pre-compiled regex patterns for wildcard hostname patterns that should bypass the proxy. Each pattern is
     * compiled with case-insensitive flag for hostname matching.
     */
    private final List<Pattern> wildcardPatterns;

    /**
     * Constructs a new {@code CamelProxyRoutePlanner} with the specified proxy and non-proxy hosts.
     *
     * @param proxy        the proxy host to use for requests that don't match non-proxy patterns. Must not be null.
     * @param noProxyHosts a set of hostname patterns that should bypass the proxy. Can be null or empty to disable
     *                     proxy bypassing. Patterns may contain wildcards (*).
     */
    public CamelProxyRoutePlanner(HttpHost proxy, Set<String> noProxyHosts) {
        super(proxy);
        this.exactMatches = new HashSet<>();
        this.wildcardPatterns = new ArrayList<>();
        compilePatterns(noProxyHosts);
    }

    /**
     * Constructs a new {@code CamelProxyRoutePlanner} with the specified proxy, scheme port resolver, and non-proxy
     * hosts.
     *
     * @param proxy              the proxy host to use for requests that don't match non-proxy patterns. Must not be
     *                           null.
     * @param schemePortResolver the scheme port resolver to use for determining default ports. Can be null to use
     *                           default resolver.
     * @param noProxyHosts       a set of hostname patterns that should bypass the proxy. Can be null or empty to
     *                           disable proxy bypassing. Patterns may contain wildcards (*).
     */
    public CamelProxyRoutePlanner(HttpHost proxy, SchemePortResolver schemePortResolver, Set<String> noProxyHosts) {
        super(proxy, schemePortResolver);
        this.exactMatches = new HashSet<>();
        this.wildcardPatterns = new ArrayList<>();
        compilePatterns(noProxyHosts);
    }

    /**
     * Compiles the noProxyHosts patterns at instantiation time for optimal performance. This method separates exact
     * matches from wildcard patterns: Exact matches (no wildcards) are stored in a HashSet for O(1) lookup Wildcard
     * patterns are compiled to case-insensitive regex Pattern objects
     *
     * @param noProxyHosts the set of hostname patterns to compile. Null or empty sets are handled gracefully.
     */
    private void compilePatterns(Set<String> noProxyHosts) {
        if (noProxyHosts == null || noProxyHosts.isEmpty()) {
            return;
        }

        for (String pattern : noProxyHosts) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }

            if (pattern.contains("*")) {
                // Compile wildcard pattern to regex
                String regex = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*");
                // Case-insensitive matching
                wildcardPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } else {
                // Store exact match in lowercase for fast lookup
                exactMatches.add(pattern.toLowerCase());
            }
        }
    }

    /**
     * Checks if a hostname matches any of the configured non-proxy host patterns. The matching is performed in two
     * stages for optimal performance: Exact match lookup in the HashSet (O(1) operation) Pattern matching against
     * pre-compiled regex patterns (only if no exact match found)
     *
     * @param  hostname the hostname to check. Null hostnames are treated as non-matching.
     * @return          {@code true} if the hostname matches any non-proxy host pattern, {@code false} otherwise
     */
    private boolean matchesNoProxyHost(String hostname) {
        if (hostname == null) {
            return false;
        }

        // First check exact matches (O(1) lookup)
        if (exactMatches.contains(hostname.toLowerCase())) {
            return true;
        }

        // Then check wildcard patterns
        for (Pattern pattern : wildcardPatterns) {
            if (pattern.matcher(hostname).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines the proxy to use for the given target host. This method checks if the target hostname matches any of
     * the configured non-proxy host patterns. If a match is found, the method returns {@code null} to bypass the proxy.
     * Otherwise, it delegates to the parent class to use the configured proxy.
     *
     *
     * @param  target        the target host for the request. Must not be null.
     * @param  context       the HTTP context for the request. Can be null.
     * @return               the proxy to use for this target, or {@code null} to connect directly without a proxy
     * @throws HttpException if an error occurs during proxy determination
     */
    @Override
    protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
        if (exactMatches.isEmpty() && wildcardPatterns.isEmpty()) {
            return super.determineProxy(target, context);
        }
        String targetHost = target.getHostName();

        if (matchesNoProxyHost(targetHost)) {
            return null; // Bypass proxy
        }

        return super.determineProxy(target, context);

    }
}
