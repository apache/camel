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

import java.util.Map;

/**
 * The question the AI panel is opened with to explain a circuit breaker (Shift+F8 on the Circuit Breaker tab,
 * CAMEL-25362): its state and counters, its configuration, where it is in the source, and the last error of its route.
 * An open breaker is often the breaker doing its job, so the AI is asked to explain and suggest, not to change the
 * files on its own. It is put in the input of the panel, for the user to send with Enter or change first.
 */
final class CircuitBreakerAiPrompt {

    private CircuitBreakerAiPrompt() {
    }

    /** Whether a breaker has something to explain: it is not closed, or calls failed, timed out or were rejected. */
    static boolean hasTrouble(CircuitBreakerInfo cb) {
        return cb.state != null && !"closed".equalsIgnoreCase(cb.state)
                || cb.failedCalls > 0 || cb.timedOutCalls > 0 || cb.notPermittedCalls > 0
                || cb.bulkheadRejectedCalls > 0;
    }

    static String of(IntegrationInfo info, CircuitBreakerInfo cb) {
        StringBuilder sb = new StringBuilder();
        String name = info.name != null ? info.name : info.pid;
        sb.append("Explain the circuit breaker ").append(cb.id != null ? cb.id : "")
                .append(" in route ").append(cb.routeId != null ? cb.routeId : "")
                .append(" of the running integration ").append(name).append(": it is ")
                .append(cb.state != null ? cb.state : "in an unknown state");
        if (cb.component != null) {
            sb.append(" (").append(cb.component).append(')');
        }
        sb.append(".\n");
        sb.append("Its sliding window: ").append(cb.successfulCalls).append(" successful, ").append(cb.failedCalls)
                .append(" failed");
        if (cb.timedOutCalls > 0) {
            sb.append(" (").append(cb.timedOutCalls).append(" of all calls timed out)");
        }
        if (cb.failureRate >= 0) {
            sb.append(", failure rate ").append(String.format("%.0f%%", cb.failureRate));
        }
        sb.append(".\n");
        if (cb.notPermittedCalls > 0 || cb.fallbackCalls > 0 || cb.bulkheadRejectedCalls > 0) {
            sb.append("Since it started: ").append(cb.notPermittedCalls).append(" calls rejected while open, ")
                    .append(cb.fallbackCalls).append(" answered by the fallback");
            if (cb.bulkheadRejectedCalls > 0) {
                sb.append(", ").append(cb.bulkheadRejectedCalls).append(" rejected by a full bulkhead");
            }
            sb.append(".\n");
        }
        if (cb.total > 0) {
            sb.append("Calls in total: ").append(cb.total).append(", failed ").append(cb.totalFailed)
                    .append("; processing time mean ").append(ChaosTab.shortTime(cb.meanTime)).append(", max ")
                    .append(ChaosTab.shortTime(cb.maxTime)).append(".\n");
        }
        if (!cb.configuration.isEmpty()) {
            StringBuilder conf = new StringBuilder();
            for (Map.Entry<String, Object> e : cb.configuration.entrySet()) {
                if (e.getValue() != null) {
                    conf.append(conf.isEmpty() ? "" : ", ").append(e.getKey()).append('=').append(e.getValue());
                }
            }
            sb.append("Its configuration: ").append(conf).append('\n');
        }
        String source = sourceOf(info, cb);
        if (source != null) {
            sb.append("It is at ").append(source).append(".\n");
        }
        ErrorInfo last = lastError(info, cb.routeId);
        if (last != null) {
            sb.append("The last error of the route: ").append(RuntimeFailures.failureOf(last)).append('\n');
        }
        sb.append("Tell whether the breaker did its job (the call it protects failed or was slow, and why, from the")
                .append(" integration's errors and log) or whether its configuration does not fit the traffic, and")
                .append(" what the callers got meanwhile. Then suggest what to change, if anything. tui_get_table on")
                .append(" the Circuit Breaker tab has the counters. Make no changes to the files unless I agree.");
        return sb.toString();
    }

    /** The source location (file:line) of the breaker's processor, else of its route; null when not known. */
    static String sourceOf(IntegrationInfo info, CircuitBreakerInfo cb) {
        for (RouteInfo r : info.routes) {
            if (cb.routeId == null || !cb.routeId.equals(r.routeId)) {
                continue;
            }
            for (ProcessorInfo p : r.processors) {
                if (cb.id != null && cb.id.equals(p.id) && p.source != null) {
                    return p.source;
                }
            }
            return r.source;
        }
        return null;
    }

    /** The newest error of the route in the error registry; null when there is none. */
    private static ErrorInfo lastError(IntegrationInfo info, String routeId) {
        ErrorInfo last = null;
        for (ErrorInfo e : info.errors) {
            if (routeId != null && routeId.equals(e.routeId) && (last == null || e.timestamp > last.timestamp)) {
                last = e;
            }
        }
        return last;
    }
}
