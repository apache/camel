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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RuntimePropertiesProvider;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.json.JsonRecordSupport;

import static org.apache.camel.util.LocationHelper.locationSummary;

@DevConsole(name = "properties", description = "Displays the properties loaded by Camel")
public class PropertiesDevConsole extends AbstractDevConsole {

    public record PropertyEntry(
            @Metadata(description = "The property key") String key,
            @Metadata(description = "The property value (masked when sensitive)") String value,
            @Metadata(description = "The original unresolved value (only present when resolved via a properties function)") String originalValue,
            @Metadata(description = "The default value (only present when one was used)") String defaultValue,
            @Metadata(description = "The source that provided the value (only present when known)") String source,
            @Metadata(description = "The location the value was loaded from (only present when known)") String location,
            @Metadata(description = "Whether the location is an internal Camel location (only present when location is known)") Boolean internal) {
    }

    public record Response(
            @Metadata(description = "The locations properties are loaded from") List<String> locations,
            @Metadata(description = "The loaded properties (only present when there are any)") List<PropertyEntry> properties) {
    }

    public PropertiesDevConsole() {
        super("camel", "properties", "Properties", "Displays the properties loaded by Camel");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        String loc = String.join(", ", pc.getLocations());
        sb.append(String.format("Properties loaded from locations: %s", loc));
        sb.append("\n");

        // when a runtime provider is present (Spring Boot, Quarkus, etc.) it is the
        // authoritative source — skip pc.loadProperties() to avoid noisy duplicates
        // from runtime-managed config sources (env vars, system properties, etc.)
        Set<RuntimePropertiesProvider> providers
                = getCamelContext().getRegistry().findByType(RuntimePropertiesProvider.class);
        if (!providers.isEmpty()) {
            for (RuntimePropertiesProvider provider : providers) {
                Collection<RuntimePropertiesProvider.Property> runtimeProps = provider.getProperties();
                if (runtimeProps != null && !runtimeProps.isEmpty()) {
                    for (RuntimePropertiesProvider.Property prop : runtimeProps) {
                        if (SensitiveUtils.containsSensitive(prop.key())) {
                            sb.append(String.format("    %s %s = xxxxxx%n", prop.source(), prop.key()));
                        } else {
                            sb.append(String.format("    %s %s = %s%n", prop.source(), prop.key(), prop.value()));
                        }
                    }
                    sb.append("\n");
                }
            }
        } else {
            Properties p = pc.loadProperties();
            OrderedLocationProperties olp = null;
            if (p instanceof OrderedLocationProperties orderedlocationproperties2) {
                olp = orderedlocationproperties2;
            }
            for (var entry : p.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                loc = olp != null ? locationSummary(olp, k) : null;
                if (SensitiveUtils.containsSensitive(k)) {
                    sb.append(String.format("    %s %s = xxxxxx%n", loc, k));
                } else {
                    sb.append(String.format("    %s %s = %s%n", loc, k, v));
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        PropertiesComponent pc = getCamelContext().getPropertiesComponent();

        List<PropertyEntry> entries = new ArrayList<>();

        // when a runtime provider is present (Spring Boot, Quarkus, etc.) it is the
        // authoritative source — skip pc.loadProperties() to avoid noisy duplicates
        // from runtime-managed config sources (env vars, system properties, etc.)
        Set<RuntimePropertiesProvider> providers
                = getCamelContext().getRegistry().findByType(RuntimePropertiesProvider.class);
        if (!providers.isEmpty()) {
            for (RuntimePropertiesProvider provider : providers) {
                Collection<RuntimePropertiesProvider.Property> runtimeProps = provider.getProperties();
                if (runtimeProps != null && !runtimeProps.isEmpty()) {
                    for (RuntimePropertiesProvider.Property prop : runtimeProps) {
                        boolean sensitive = SensitiveUtils.containsSensitive(prop.key());
                        entries.add(new PropertyEntry(
                                prop.key(), sensitive ? "xxxxxx" : String.valueOf(prop.value()), null, null, prop.source(),
                                null, null));
                    }
                }
            }
        } else {
            Properties p = pc.loadProperties();
            OrderedLocationProperties olp = p instanceof OrderedLocationProperties o ? o : null;
            for (var entry : p.entrySet()) {
                entries.add(toPropertyEntry(pc, olp, entry));
            }
        }

        Response response = new Response(pc.getLocations(), entries.isEmpty() ? null : entries);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static PropertyEntry toPropertyEntry(
            PropertiesComponent pc, OrderedLocationProperties olp, Map.Entry<Object, Object> entry) {

        String k = entry.getKey().toString();
        Object v = entry.getValue();
        String loc = olp != null ? olp.getLocation(k) : null;
        String originalValue = null;
        String defaultValue = null;
        String source = null;
        var m = pc.getResolvedValue(k);
        if (m.isPresent()) {
            originalValue = m.get().originalValue();
            defaultValue = m.get().defaultValue();
            source = m.get().source();
            v = m.get().value();
        }
        boolean sensitive = SensitiveUtils.containsSensitive(k);
        String value = sensitive ? "xxxxxx" : String.valueOf(v);
        String originalValueOut = originalValue != null ? (sensitive ? "xxxxxx" : originalValue) : null;
        Boolean internal = loc != null ? isInternal(loc) : null;
        return new PropertyEntry(k, value, originalValueOut, defaultValue, source, loc, internal);
    }

    private static boolean isInternal(String loc) {
        if (loc == null) {
            return false;
        }
        return "initial".equals(loc) || "override".equals(loc);
    }

}
