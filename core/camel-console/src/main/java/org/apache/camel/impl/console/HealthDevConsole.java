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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "health", displayName = "Health Check", description = "Health Check Status")
public class HealthDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Exposure level for health check details",
              defaultValue = "default", javaType = "java.lang.String", enums = "default,oneline,full")
    public static final String EXPOSURE_LEVEL = "exposureLevel";

    public record CheckEntry(
            @Metadata(description = "The health check ID") String id,
            @Metadata(description = "The health check group") String group,
            @Metadata(description = "Whether the health check is up") boolean up,
            @Metadata(description = "The health check state") String state,
            @Metadata(description = "Whether the health check is enabled") boolean enabled,
            @Metadata(description = "Whether the health check is a readiness check") boolean readiness,
            @Metadata(description = "Whether the health check is a liveness check") boolean liveness,
            @Metadata(description = "The failure message (only present when not up)") String message,
            @Metadata(description = "The failure stack trace, one entry per line (only present when not up and an error is available)") List<String> stackTrace,
            @Metadata(description = "Additional health check specific details (only present when available)") Map<String, String> details) {
    }

    public record Response(
            @Metadata(description = "Whether the overall health status is up") boolean up,
            @Metadata(description = "Whether the readiness checks are up") boolean ready,
            @Metadata(description = "Whether the liveness checks are up") boolean live,
            @Metadata(description = "The individual health checks") List<CheckEntry> checks) {
    }

    public HealthDevConsole() {
        super("camel", "health", "Health Check", "Health Check Status");
    }

    protected String doCallText(Map<String, Object> options) {
        // only text is supported
        StringBuilder sb = new StringBuilder();

        String exposureLevel = optionString(options, EXPOSURE_LEVEL);
        Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(getCamelContext(), exposureLevel);
        boolean up = results.stream().allMatch(h -> HealthCheck.State.UP.equals(h.getState()));
        sb.append(String.format("Health Check Status: %s", up ? "UP" : "DOWN"));
        sb.append("\n");

        results.forEach(res -> {
            boolean ok = res.getState().equals(HealthCheck.State.UP);
            if (ok) {
                sb.append(String.format("%n    %s: %s", res.getCheck().getId(), res.getState()));
            } else {
                if (res.getMessage().isPresent()) {
                    sb.append(
                            String.format("%n    %s: %s (%s)", res.getCheck().getId(), res.getState(), res.getMessage().get()));
                } else {
                    sb.append(String.format("%n    %s: %s", res.getCheck().getId(), res.getState()));
                }
                if ("full".equals(exposureLevel)) {
                    if (res.getError().isPresent()) {
                        Throwable cause = res.getError().get();
                        final String stackTrace = ExceptionHelper.stackTraceToString(cause);

                        sb.append("\n\n");
                        sb.append(stackTrace);
                        sb.append("\n\n");
                    }
                }
            }
        });

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String exposureLevel = optionString(options, EXPOSURE_LEVEL);
        Collection<HealthCheck.Result> readies = HealthCheckHelper.invokeReadiness(getCamelContext(), exposureLevel);
        Collection<HealthCheck.Result> lives = HealthCheckHelper.invokeLiveness(getCamelContext(), exposureLevel);
        boolean ready = HealthCheckHelper.isResultsUp(readies, true);
        boolean live = HealthCheckHelper.isResultsUp(lives, false);

        List<CheckEntry> checks = new ArrayList<>();
        Stream.concat(readies.stream(), lives.stream()).forEach(res -> {
            boolean ok = res.getState().equals(HealthCheck.State.UP);

            String message = null;
            List<String> stackTrace = null;
            if (!ok) {
                message = res.getMessage().orElse("");

                Throwable cause = res.getError().orElse(null);
                if (cause != null) {
                    final String trace = ExceptionHelper.stackTraceToString(cause);
                    stackTrace = Arrays.asList(trace.split("\n"));
                }
            }

            Map<String, String> details = null;
            if (!res.getDetails().isEmpty()) {
                details = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : res.getDetails().entrySet()) {
                    details.put(entry.getKey(), entry.getValue().toString());
                }
            }

            checks.add(new CheckEntry(
                    res.getCheck().getId(), res.getCheck().getGroup(), ok, res.getState().toString(),
                    res.getCheck().isEnabled(), res.getCheck().isReadiness(), res.getCheck().isLiveness(), message,
                    stackTrace, details));
        });

        Response response = new Response(ready && live, ready, live, checks);
        return JsonRecordSupport.toJsonObject(response);
    }
}
