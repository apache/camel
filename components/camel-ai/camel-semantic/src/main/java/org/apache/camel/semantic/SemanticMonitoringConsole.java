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
package org.apache.camel.semantic;

import java.util.List;
import java.util.Map;

import org.apache.camel.semantic.internal.SemanticMonitoringService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

/** Shared locally cached metrics and producer-supplied monitoring information. Reads never run an expert. */
@DevConsole(name = "semantic-monitoring", displayName = "Semantic Monitoring",
            description = "Read current semantic measurements")
public class SemanticMonitoringConsole extends AbstractDevConsole {
    @Metadata(label = "query", description = "Producer name to inspect", javaType = "java.lang.String")
    public static final String SOURCE = "source";

    /** A transient read of current measurements and collection health. */
    public record Response(
            @Metadata(description = "Current producer-supplied measurements") List<Map<String, Object>> snapshots,
            @Metadata(description = "Scope of the returned measurements") String scope,
            @Metadata(description = "Source sampling health") Map<String, Object> sources,
            @Metadata(description = "Sources beyond the registration limit") Integer omittedSources,
            @Metadata(description = "How to interpret freshness") String freshness,
            @Metadata(description = "Source sampling failures") Long sourceErrors,
            @Metadata(description = "Series evicted from the current-value cache") Long evicted,
            @Metadata(description = "Management registration failures") Long managementErrors,
            @Metadata(description = "Maximum cached series count") Integer capacity,
            @Metadata(description = "Sampling cadence in seconds") Integer sampleIntervalSeconds,
            @Metadata(description = "Last sampling time when available") String lastSample,
            @Metadata(description = "Query validation error when present") String error) {
    }

    public SemanticMonitoringConsole() {
        super("camel", "semantic-monitoring", "Semantic Monitoring", "Read current semantic measurements");
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        try {
            String source = optionString(options, SOURCE);
            if (options.keySet().stream().anyMatch(key -> !SOURCE.equals(key))
                    || source != null && (source.isBlank() || source.length() > 256)) {
                throw new IllegalArgumentException("Invalid monitoring query");
            }
            return new JsonObject(SemanticMonitoringService.get(getCamelContext()).snapshot(source));
        } catch (IllegalArgumentException invalid) {
            return new JsonObject(Map.of("error", "monitoring_query_invalid"));
        }
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return doCallJson(options).toJson();
    }
}
