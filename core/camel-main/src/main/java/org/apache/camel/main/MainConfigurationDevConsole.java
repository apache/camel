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

import java.util.Map;

import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.util.LocationHelper.locationSummary;

@DevConsole(name = "main-configuration", displayName = "Main Configuration",
            description = "Display Camel startup configuration")
public class MainConfigurationDevConsole extends AbstractDevConsole {

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
                if (MainHelper.containsSensitive(k, v)) {
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

        JsonObject root = new JsonObject();
        if (!startupConfiguration.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (var entry : startupConfiguration.entrySet()) {
                String k = entry.getKey().toString();
                Object v = entry.getValue();
                String loc = startupConfiguration.getLocation(k);
                Object defaultValue = startupConfiguration.getDefaultValue(k);

                JsonObject jo = new JsonObject();
                jo.put("key", k);
                jo.put("value", v);
                if (defaultValue != null) {
                    jo.put("defaultValue", defaultValue);
                }
                // enrich if present
                pc.getResolvedValue(k).ifPresent(r -> {
                    String ov = r.originalValue();
                    if (ov != null) {
                        jo.put("originalValue", ov);
                    }
                    String src = r.source();
                    if (src != null) {
                        jo.put("source", src);
                    }
                });
                if (loc != null) {
                    jo.put("location", loc);
                    jo.put("internal", isInternal(loc));
                }
                arr.add(jo);
            }
            root.put("configurations", arr);
        }

        return root;
    }

    private static boolean isInternal(String loc) {
        if (loc == null) {
            return false;
        }
        return "initial".equals(loc) || "override".equals(loc);
    }

}
