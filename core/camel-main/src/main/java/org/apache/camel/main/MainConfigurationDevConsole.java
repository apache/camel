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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesResolvedValue;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.json.JsonRecordSupport;

import static org.apache.camel.util.LocationHelper.locationSummary;

@DevConsole(name = "main-configuration", displayName = "Main Configuration",
            description = "Display Camel startup configuration")
public class MainConfigurationDevConsole extends AbstractDevConsole {

    public record ConfigurationEntry(
            @Metadata(description = "The configuration key") String key,
            @Metadata(description = "The configuration value (masked as xxxxxx when sensitive)") Object value,
            @Metadata(description = "The default value (only present when known)") Object defaultValue,
            @Metadata(description = "The original, unresolved value (only present when known and not sensitive)") Object originalValue,
            @Metadata(description = "The source that provided the value (only present when known)") String source,
            @Metadata(description = "The location the value came from (only present when known)") String location,
            @Metadata(description = "Whether the location is internal (only present when the location is known)") Boolean internal) {
    }

    public record Response(
            @Metadata(description = "The startup configurations (only present when there are any)") List<ConfigurationEntry> configurations) {
    }

    private final OrderedLocationProperties startupConfiguration = new OrderedLocationProperties();

    public MainConfigurationDevConsole() {
        super("camel", "main-configuration", "Main Configuration", "Display Camel startup configuration");
    }

    public void addStartupConfiguration(OrderedLocationProperties startupConfiguration) {
        this.startupConfiguration.putAll(startupConfiguration);
    }

    public void addStartupConfiguration(String loc, Object key, Object value) {
        startupConfiguration.put(loc, key, value);
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        if (!startupConfiguration.isEmpty()) {
            sb.append("Camel Main Configuration:\n");
            for (var entry : startupConfiguration.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                String loc = locationSummary(startupConfiguration, k);
                if (MainHelper.containsSensitive(getCamelContext(), k, v)) {
                    sb.append(String.format("    %s %s = xxxxxx%n", loc, k));
                } else {
                    sb.append(String.format("    %s %s = %s%n", loc, k, v));
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        PropertiesComponent pc = getCamelContext().getPropertiesComponent();

        List<ConfigurationEntry> configurations = null;
        if (!startupConfiguration.isEmpty()) {
            configurations = new ArrayList<>();
            for (var entry : startupConfiguration.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                String loc = startupConfiguration.getLocation(k);
                Object defaultValue = startupConfiguration.getDefaultValue(k);

                boolean sensitive = MainHelper.containsSensitive(getCamelContext(), k, v);
                Object value = sensitive ? "xxxxxx" : v;

                Object originalValue = null;
                String source = null;
                Optional<PropertiesResolvedValue> resolved = pc.getResolvedValue(k);
                if (resolved.isPresent()) {
                    PropertiesResolvedValue r = resolved.get();
                    String ov = r.originalValue();
                    if (ov != null) {
                        originalValue = sensitive ? "xxxxxx" : ov;
                    }
                    source = r.source();
                }

                Boolean internal = loc != null ? isInternal(loc) : null;

                configurations.add(new ConfigurationEntry(k, value, defaultValue, originalValue, source, loc, internal));
            }
        }

        Response response = new Response(configurations);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static boolean isInternal(String loc) {
        if (loc == null) {
            return false;
        }
        return "initial".equals(loc) || "override".equals(loc);
    }

}
