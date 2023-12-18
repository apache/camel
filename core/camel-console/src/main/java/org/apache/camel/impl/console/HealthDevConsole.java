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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("health")
public class HealthDevConsole extends AbstractDevConsole {

    public HealthDevConsole() {
        super("camel", "health", "Health Check", "Health Check Status");
    }

    protected String doCallText(Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        String exposureLevel = (String) options.get("exposureLevel");
        Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(getCamelContext(), exposureLevel);
        boolean up = results.stream().allMatch(h -> HealthCheck.State.UP.equals(h.getState()));
        sb.append(String.format("Health Check Status: %s", up ? "UP" : "DOWN"));
        sb.append("\n");

        results.forEach(res -> {
            boolean ok = res.getState().equals(HealthCheck.State.UP);
            if (ok) {
                sb.append(String.format("\n    %s: %s", res.getCheck().getId(), res.getState()));
            } else {
                if (res.getMessage().isPresent()) {
                    sb.append(
                            String.format("\n    %s: %s (%s)", res.getCheck().getId(), res.getState(), res.getMessage().get()));
                } else {
                    sb.append(String.format("\n    %s: %s", res.getCheck().getId(), res.getState()));
                }
                if ("full".equals(exposureLevel)) {
                    if (res.getError().isPresent()) {
                        Throwable cause = res.getError().get();
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        cause.printStackTrace(pw);
                        sb.append("\n\n");
                        sb.append(sw);
                        sb.append("\n\n");
                    }
                }
            }
        });

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        String exposureLevel = (String) options.get("exposureLevel");
        Collection<HealthCheck.Result> readies = HealthCheckHelper.invokeReadiness(getCamelContext(), exposureLevel);
        Collection<HealthCheck.Result> lives = HealthCheckHelper.invokeLiveness(getCamelContext(), exposureLevel);
        boolean ready = HealthCheckHelper.isResultsUp(readies, true);
        boolean live = HealthCheckHelper.isResultsUp(lives, false);
        root.put("up", ready && live);
        root.put("ready", ready);
        root.put("live", live);

        JsonArray arr = new JsonArray();
        root.put("checks", arr);

        Stream<HealthCheck.Result> checks = Stream.concat(readies.stream(), lives.stream());
        checks.forEach(res -> {
            JsonObject jo = new JsonObject();
            arr.add(jo);

            boolean ok = res.getState().equals(HealthCheck.State.UP);
            jo.put("id", res.getCheck().getId());
            jo.put("group", res.getCheck().getGroup());
            if (ok) {
                jo.put("up", true);
            } else {
                jo.put("up", false);
            }
            jo.put("state", res.getState().toString());
            jo.put("enabled", res.getCheck().isEnabled());
            jo.put("readiness", res.getCheck().isReadiness());
            jo.put("liveness", res.getCheck().isLiveness());

            if (!ok) {
                String msg = res.getMessage().orElse("");
                jo.put("message", msg);

                Throwable cause = res.getError().orElse(null);
                if (cause != null) {
                    JsonArray arr2 = new JsonArray();
                    StringWriter writer = new StringWriter();
                    cause.printStackTrace(new PrintWriter(writer));
                    writer.flush();
                    String trace = writer.toString();
                    jo.put("stackTrace", arr2);
                    Collections.addAll(arr2, trace.split("\n"));
                }
            }

            if (!res.getDetails().isEmpty()) {
                JsonObject details = new JsonObject();
                res.getDetails().forEach((k, v) -> {
                    details.put(k, v.toString());
                });
                jo.put("details", details);
            }
        });

        return root;
    }
}
