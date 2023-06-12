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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Route;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("route-circuit-breaker")
public class RouteCircuitBreakerDevConsole extends AbstractDevConsole {

    public RouteCircuitBreakerDevConsole() {
        super("camel", "route-circuit-breaker", "Route Circuit Breaker", "Display circuit breaker information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        for (Route route : getCamelContext().getRoutes()) {
            for (RoutePolicy rp : route.getRoutePolicyList()) {
                if (rp instanceof ThrottlingExceptionRoutePolicy cb) {
                    String rid = route.getRouteId();
                    String state = cb.getStateAsString();
                    int sc = cb.getSuccess();
                    int fc = cb.getFailures();
                    String lastFailure = cb.getLastFailure() > 0 ? TimeUtils.printSince(cb.getLastFailure()) : "n/a";
                    sb.append(String.format("    %s: %s (success: %d failure: %d last-failure: %s)\n", rid, state, sc, fc,
                            lastFailure));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        final List<JsonObject> list = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            for (RoutePolicy rp : route.getRoutePolicyList()) {
                if (rp instanceof ThrottlingExceptionRoutePolicy cb) {
                    JsonObject jo = new JsonObject();
                    jo.put("routeId", route.getRouteId());
                    jo.put("state", cb.getStateAsString());
                    jo.put("successfulCalls", cb.getSuccess());
                    jo.put("failedCalls", cb.getFailures());
                    list.add(jo);
                }
            }
        }
        root.put("circuitBreakers", list);

        return root;
    }
}
