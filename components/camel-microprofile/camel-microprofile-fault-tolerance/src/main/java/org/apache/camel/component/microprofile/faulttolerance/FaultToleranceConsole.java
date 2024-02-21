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
package org.apache.camel.component.microprofile.faulttolerance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("fault-tolerance")
public class FaultToleranceConsole extends AbstractDevConsole {

    public FaultToleranceConsole() {
        super("camel", "fault-tolerance", "MicroProfile Fault Tolerance Circuit Breaker",
              "Display circuit breaker information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<FaultToleranceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof FaultToleranceProcessor) {
                    cbs.add((FaultToleranceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(FaultToleranceProcessor::getId));

        for (FaultToleranceProcessor cb : cbs) {
            String id = cb.getId();
            String rid = cb.getRouteId();
            String state = cb.getCircuitBreakerState();
            sb.append(String.format("    %s/%s: %s\n", rid, id, state));
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<FaultToleranceProcessor> cbs = new ArrayList<>();
        for (Route route : getCamelContext().getRoutes()) {
            List<Processor> list = route.filter("*");
            for (Processor p : list) {
                if (p instanceof FaultToleranceProcessor) {
                    cbs.add((FaultToleranceProcessor) p);
                }
            }
        }
        // sort by ids
        cbs.sort(Comparator.comparing(FaultToleranceProcessor::getId));

        final List<JsonObject> list = new ArrayList<>();
        for (FaultToleranceProcessor cb : cbs) {
            JsonObject jo = new JsonObject();
            jo.put("id", cb.getId());
            jo.put("routeId", cb.getRouteId());
            jo.put("state", cb.getCircuitBreakerState());
            list.add(jo);
        }
        root.put("circuitBreakers", list);

        return root;
    }
}
