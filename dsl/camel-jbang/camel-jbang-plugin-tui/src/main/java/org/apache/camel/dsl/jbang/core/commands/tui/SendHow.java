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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.camel.util.json.JsonObject;

/**
 * How a send of a route reaches the route that consumes it, for the jump links of the source editor: call when the
 * sending route waits for the answer, async when it hands the message off (CAMEL-26823).
 */
final class SendHow {

    // a uri that asks for the answer of what it sends to, and the key of a step block that does
    private static final Pattern IN_OUT_PARAMETER = Pattern.compile("[?&]exchangePattern=InOut(&|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_OUT_KEY
            = Pattern.compile("(exchangePattern|pattern):\\s*[\"']?InOut[\"']?\\s*(#.*)?", Pattern.CASE_INSENSITIVE);

    private SendHow() {
    }

    /**
     * How a step reaches the route that consumes the endpoint it sends to, as the sources say it: call when the route
     * waits for the answer (direct and kamelet run the route before it goes on, enrich waits for it, and
     * exchangePattern=InOut asks for it, as a request-reply over a broker does), async when it hands the message off (a
     * wire tap always does). null for a dead letter channel or a pollEnrich, which are no send the route waits on or
     * hands off. A route that consumes an InOut message (from platform-http) waits on its sends too; only the running
     * integration knows that (see {@link #how(String, String, String, Map)}).
     */
    static String of(String keyword, String uri, boolean inOut) {
        if (uri == null || "deadLetterChannel".equals(keyword) || "pollEnrich".equals(keyword)) {
            return null;
        }
        if ("wireTap".equals(keyword)) {
            return "async";
        }
        int colon = uri.indexOf(':');
        String scheme = colon > 0 ? uri.substring(0, colon).toLowerCase(Locale.ROOT) : "";
        if ("enrich".equals(keyword) || "direct".equals(scheme) || "kamelet".equals(scheme) || inOut
                || IN_OUT_PARAMETER.matcher(uri).find()) {
            return "call";
        }
        return "async";
    }

    /** Whether the block of a step (its parameters) asks for the answer: exchangePattern: InOut, or pattern: InOut. */
    static boolean blockSaysInOut(List<String> lines, int start, int indent) {
        if (start < 0) {
            return false;
        }
        for (int j = start + 1; j < lines.size(); j++) {
            String line = lines.get(j);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (SourceTab.lineIndent(line) <= indent) {
                break;
            }
            if (IN_OUT_KEY.matcher(trimmed).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * How the route reaches the other: as the running integration has it when its topology was asked for, else static.
     */
    static String how(String fromRouteId, String toRouteId, String staticHow, Map<String, String> runtime) {
        String known = fromRouteId != null ? runtime.get(fromRouteId + ">" + toRouteId) : null;
        return known != null ? known : staticHow;
    }

    /**
     * How the routes of the selected integration reach each other, by "from>to": call when the route waits for the
     * answer, else async. Only from the topology a tab has asked for already, so the jump links never wait on the
     * integration; empty when there is none.
     */
    static Map<String, String> runtime(MonitorContext ctx) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        JsonObject topology = info != null ? ctx.diagramTopology.cached(info.pid) : null;
        Collection<?> edges = topology != null ? topology.getCollection("edges") : null;
        if (edges == null) {
            return Map.of();
        }
        Map<String, String> answer = new HashMap<>();
        for (Object o : edges) {
            if (o instanceof JsonObject e && !"error".equals(e.getString("kind"))) {
                String from = e.getString("fromRouteId");
                String to = e.getString("toRouteId");
                if (from != null && to != null) {
                    answer.merge(from + ">" + to, Boolean.TRUE.equals(e.get("reply")) ? "call" : "async",
                            (a, b) -> "call".equals(a) || "call".equals(b) ? "call" : a);
                }
            }
        }
        return answer;
    }
}
