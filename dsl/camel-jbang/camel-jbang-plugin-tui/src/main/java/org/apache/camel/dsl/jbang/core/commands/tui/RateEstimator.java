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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The message rate of an integration that does not report one. The app measures its throughput with the load statistics
 * timer, which a Quarkus or Spring Boot app in the prod profile (or an older Camel) does not run: it then reports no
 * rate, or 0.00, while its total keeps growing. The rate is then measured here, from how much the total grew over the
 * last seconds of polls.
 */
class RateEstimator {

    static final long WINDOW_MS = 10_000;

    private record Sample(long time, long total) {
    }

    private final Map<String, Deque<Sample>> samples = new HashMap<>();

    /** Fills the rate of the integration and its routes when the app reported none. */
    void fill(IntegrationInfo info, long now) {
        info.throughput = estimate(info.pid, info.throughput, info.exchangesTotal, now);
        for (RouteInfo route : info.routes) {
            route.throughput = estimate(info.pid + "/" + route.routeId, route.throughput, route.total, now);
        }
    }

    /** Forgets the integrations that are gone. */
    void retain(Set<String> pids) {
        samples.keySet().removeIf(key -> !pids.contains(key.contains("/") ? key.substring(0, key.indexOf('/')) : key));
    }

    String estimate(String key, String reported, long total, long now) {
        Deque<Sample> window = samples.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (!window.isEmpty() && total < window.peekLast().total()) {
            // the statistics were reset (or the app restarted): measure again from here
            window.clear();
        }
        window.addLast(new Sample(now, total));
        while (window.size() > 2 && now - window.peekFirst().time() > WINDOW_MS) {
            window.removeFirst();
        }
        if (hasRate(reported)) {
            return reported;
        }
        Sample first = window.peekFirst();
        long elapsed = now - first.time();
        if (elapsed <= 0 || total <= first.total()) {
            return reported;
        }
        double perSecond = (total - first.total()) * 1000.0 / elapsed;
        return String.format(Locale.US, "%.2f", perSecond);
    }

    private static boolean hasRate(String reported) {
        if (reported == null || reported.isBlank()) {
            return false;
        }
        try {
            return Double.parseDouble(reported) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
