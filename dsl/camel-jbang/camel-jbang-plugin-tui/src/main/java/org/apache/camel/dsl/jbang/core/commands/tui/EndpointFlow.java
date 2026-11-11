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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The messages that come into an integration and go out of it, for the flow of the Endpoints tab and its rate chart. An
 * endpoint one route of the integration sends to and another consumes (direct, seda) is a hand-off between its routes:
 * counted as an out of the one and an in of the other, it would count every message twice and say it left.
 */
final class EndpointFlow {

    private EndpointFlow() {
    }

    /** The endpoints the integration both sends to and consumes, by their URI without options. */
    static Set<String> handOffs(IntegrationInfo info) {
        Set<String> in = new HashSet<>();
        Set<String> out = new HashSet<>();
        for (EndpointInfo ep : info.endpoints) {
            if (ep.uri == null) {
                continue;
            }
            if ("in".equals(ep.direction)) {
                in.add(DiagramTopology.endpointKey(ep.uri));
            } else if ("out".equals(ep.direction)) {
                out.add(DiagramTopology.endpointKey(ep.uri));
            }
        }
        in.retainAll(out);
        return in;
    }

    /** The hits of the endpoints of the direction the filter takes, without the hand-offs between its routes. */
    static long total(IntegrationInfo info, String direction, Predicate<EndpointInfo> filter) {
        return flow(info, direction).filter(filter).mapToLong(ep -> ep.hits).sum();
    }

    /**
     * The largest mean body size of the endpoints of the direction, without the hand-offs between its routes, as the
     * counts of the flow (CAMEL-26864: the avg body chart said out 7 B where the flow said 0 out).
     */
    static long meanBodySize(IntegrationInfo info, String direction) {
        return flow(info, direction).filter(ep -> ep.meanBodySize >= 0).mapToLong(ep -> ep.meanBodySize).max()
                .orElse(0);
    }

    private static Stream<EndpointInfo> flow(IntegrationInfo info, String direction) {
        Set<String> handOffs = handOffs(info);
        return info.endpoints.stream()
                .filter(ep -> direction.equals(ep.direction))
                .filter(ep -> ep.uri == null || !handOffs.contains(DiagramTopology.endpointKey(ep.uri)));
    }
}
