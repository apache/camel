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
package org.apache.camel.component.chaos;

import java.util.Collection;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.URISupport;

/**
 * Checks what the target of a rule finds in the context now, for the warning about a rule that matches nothing (often a
 * typo). The warning names some of what is there, so a person or a tool can fix the pattern.
 */
final class ChaosTargets {

    // the most endpoints, routes or steps a warning names
    private static final int NAMED = 5;

    private ChaosTargets() {
    }

    /**
     * Why the rule finds nothing to fire at in the context now, or null when it finds something. A lifecycle rule is
     * not checked: its route was checked when it was added.
     */
    static String check(CamelContext camelContext, ChaosRule rule) {
        if (rule.lifecycle != null) {
            return null;
        }
        return switch (rule.target) {
            case ENDPOINT, COMPONENT -> checkEndpoints(camelContext, rule);
            case ROUTE -> checkRoutes(camelContext, rule);
            case STEP -> checkSteps(camelContext, rule);
        };
    }

    private static String checkEndpoints(CamelContext camelContext, ChaosRule rule) {
        Collection<Endpoint> endpoints = camelContext.getEndpoints();
        boolean any = false;
        boolean polled = false;
        TreeSet<String> uris = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            String uri = ChaosPatterns.endpointUri(endpoint);
            uris.add(withoutQuery(uri));
            if (rule.matchesUri(uri)) {
                any = true;
            }
        }
        if (!any) {
            return rule.target.key + "=" + rule.pattern + " matches no endpoint of the context now, so the rule fires 0"
                   + " times (a toD creates its endpoints when it sends); the endpoints are: " + some(uris);
        }
        if (rule.hooks.equals(Set.of(Hook.POLL))) {
            for (Route route : camelContext.getRoutes()) {
                if (route.getConsumer() instanceof ScheduledPollConsumer
                        && rule.matchesUri(ChaosPatterns.endpointUri(route.getEndpoint()))) {
                    polled = true;
                }
            }
            if (!polled) {
                return "at=poll: no route consumes an endpoint that matches " + rule.target.key + "=" + rule.pattern
                       + " with a scheduled poll (consumers such as Kafka and JMS do not poll on a schedule), so the rule"
                       + " fires 0 times";
            }
        }
        return null;
    }

    private static String checkRoutes(CamelContext camelContext, ChaosRule rule) {
        TreeSet<String> ids = new TreeSet<>();
        for (Route route : camelContext.getRoutes()) {
            if (rule.matchesId(route.getRouteId())) {
                return null;
            }
            ids.add(route.getRouteId());
        }
        return "route=" + rule.pattern + " matches no route of the context now, so the rule fires 0 times; the routes are: "
               + some(ids);
    }

    private static String checkSteps(CamelContext camelContext, ChaosRule rule) {
        TreeSet<String> ids = new TreeSet<>();
        for (Route route : camelContext.getRoutes()) {
            for (Processor processor : route.filter("*")) {
                String id = processor instanceof IdAware idAware ? idAware.getId() : null;
                if (id != null) {
                    if (rule.matchesId(id)) {
                        return null;
                    }
                    ids.add(id);
                }
            }
        }
        return "step=" + rule.pattern + " matches no step of the context now, so the rule fires 0 times (a step id is"
               + " such as to1, or the id set with id(...)); the steps are: " + some(ids);
    }

    // the endpoints are named as Camel shows them elsewhere (a pattern matches them with or without the // after the
    // scheme), and without their query, which can hold secrets
    private static String withoutQuery(String uri) {
        int query = uri.indexOf('?');
        return URISupport.sanitizeUri(query > 0 ? uri.substring(0, query) : uri);
    }

    private static String some(TreeSet<String> names) {
        if (names.isEmpty()) {
            return "none";
        }
        StringJoiner sj = new StringJoiner(", ");
        names.stream().limit(NAMED).forEach(sj::add);
        int more = names.size() - NAMED;
        return more > 0 ? sj + " and " + more + " more" : sj.toString();
    }
}
