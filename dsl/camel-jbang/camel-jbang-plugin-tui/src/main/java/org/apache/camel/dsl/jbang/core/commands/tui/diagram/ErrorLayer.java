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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.List;
import java.util.Set;

/**
 * The error handling of a topology, drawn in a frame of its own below the routes: the routes reached only when a
 * failure is handled, and the error paths into them.
 *
 * @param frameTopY where the frame starts, in layout units (below the lowest route of the happy path)
 * @param routeIds  the routes in the frame: reached only on error
 * @param paths     the error paths, into the routes of the frame and between routes of the happy path
 * @param channelsY the layout y of the first channel row, one row per route of the frame that a route above sends to
 */
public record ErrorLayer(int frameTopY, Set<String> routeIds, List<ErrorPath> paths, int channelsY) {

    /**
     * An error path: the route sends to the target only when it handles a failure.
     *
     * @param via      what sends: errorHandler (a dead letter channel) or onException
     * @param handling what happens to the failure: handled, continued, notHandled, or null when a predicate decides
     */
    public record ErrorPath(String fromRouteId, String toRouteId, String via, String handling) {

        /** How the path reads in the frame: "dead letter, handled", "onException, not handled", ... */
        public String label() {
            String how = "errorHandler".equals(via) ? "dead letter" : via;
            String what = handling == null
                    ? "decided at runtime"
                    : switch (handling) {
                        case "handled" -> "handled";
                        case "continued" -> "continued";
                        case "notHandled" -> "not handled, goes on to the caller";
                        default -> handling;
                    };
            return how + ", " + what;
        }
    }
}
