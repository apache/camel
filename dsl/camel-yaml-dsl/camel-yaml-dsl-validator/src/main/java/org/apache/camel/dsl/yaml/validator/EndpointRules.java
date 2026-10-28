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
package org.apache.camel.dsl.yaml.validator;

import java.util.Set;

/**
 * The rules for the endpoints that link the routes of one application: which components a route of the application must
 * consume, which EIPs consume an endpoint and which send to one, and when an endpoint is only known at runtime.
 * <p/>
 * {@code camel validate} (the YAML DSL validator and the checks of Java and XML routes) and {@code camel overview} both
 * report a {@code direct:} or {@code seda:} endpoint that a route sends to and no route consumes; they use these rules,
 * so they agree on the same project (CAMEL-25180).
 */
public final class EndpointRules {

    /**
     * The components whose consumer is a route of the same application: a message sent to one of their endpoints needs
     * a route that consumes it. disruptor-vm is left out, as its consumer can be another application in the same JVM.
     */
    public static final Set<String> CHECKED = Set.of("direct", "seda", "disruptor");

    /** The EIPs that consume their endpoint: a route's input, and the polling ones in the middle of a route. */
    public static final Set<String> CONSUMERS = Set.of("from", "poll", "pollEnrich");

    /**
     * The EIPs (and the error handler option) that send to their endpoint. A toD sends to a fixed endpoint when its uri
     * has no expression; else its endpoint is only known at runtime.
     */
    public static final Set<String> SENDERS = Set.of("to", "toD", "wireTap", "enrich", "deadLetterUri");

    private EndpointRules() {
    }

    /**
     * The endpoint in the form the checks compare, {@code direct:lookup} for {@code direct://lookup?timeout=1000}; null
     * for an endpoint of a component that is not checked, or one only known at runtime (a placeholder, an expression).
     */
    public static String endpoint(String uri) {
        String s = withoutOptions(uri);
        String scheme = scheme(s);
        if (scheme == null || !CHECKED.contains(scheme) || s.length() == scheme.length() || isDynamic(s)) {
            return null;
        }
        String name = s.substring(scheme.length() + 1);
        if (name.startsWith("//")) {
            name = name.substring(2);
        }
        return name.isEmpty() ? null : scheme + ":" + name;
    }

    /** Whether the endpoint's component is one whose consumer is a route of the same application. */
    public static boolean isChecked(String uri) {
        String scheme = scheme(withoutOptions(uri));
        return scheme != null && CHECKED.contains(scheme);
    }

    /**
     * Whether the endpoint could be one of a component that is checked: it is, or its component is only known at
     * runtime ({@code {{endpoint}}}). A route that consumes such an endpoint, written with a placeholder or an
     * expression, could consume any of them.
     */
    public static boolean mayBeChecked(String uri) {
        String scheme = scheme(withoutOptions(uri));
        return scheme == null || isDynamic(scheme) || CHECKED.contains(scheme);
    }

    /** Whether the endpoint is only known at runtime: a property placeholder or an expression. */
    public static boolean isDynamic(String uri) {
        return uri != null && (uri.contains("{{") || uri.contains("${"));
    }

    /** What happens to a message sent to the endpoint when no route consumes it. */
    public static String withoutConsumer(String endpoint) {
        return "direct".equals(scheme(endpoint))
                ? "the exchange fails with No consumers available on endpoint"
                : "nothing fails, the messages are queued and never read";
    }

    private static String withoutOptions(String uri) {
        if (uri == null) {
            return "";
        }
        String s = uri.trim();
        int q = s.indexOf('?');
        return q > 0 ? s.substring(0, q) : s;
    }

    private static String scheme(String uri) {
        if (uri == null) {
            return null;
        }
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : null;
    }
}
