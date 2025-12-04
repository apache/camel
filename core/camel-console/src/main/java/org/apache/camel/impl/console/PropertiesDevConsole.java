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

import static org.apache.camel.util.LocationHelper.locationSummary;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "properties", description = "Displays the properties loaded by Camel")
public class PropertiesDevConsole extends AbstractDevConsole {

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

        Properties p = pc.loadProperties();
        OrderedLocationProperties olp = null;
        if (p instanceof OrderedLocationProperties) {
            olp = (OrderedLocationProperties) p;
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

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        PropertiesComponent pc = getCamelContext().getPropertiesComponent();
        root.put("locations", pc.getLocations());

        JsonArray arr = new JsonArray();
        Properties p = pc.loadProperties();
        OrderedLocationProperties olp = null;
        if (p instanceof OrderedLocationProperties) {
            olp = (OrderedLocationProperties) p;
        }
        for (var entry : p.entrySet()) {
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
            JsonObject jo = new JsonObject();
            jo.put("key", k);
            jo.put("value", v);
            if (originalValue != null) {
                jo.put("originalValue", originalValue);
            }
            if (defaultValue != null) {
                jo.put("defaultValue", defaultValue);
            }
            if (source != null) {
                jo.put("source", source);
            }
            if (loc != null) {
                jo.put("location", loc);
                jo.put("internal", isInternal(loc));
            }
            arr.add(jo);
        }
        if (!arr.isEmpty()) {
            root.put("properties", arr);
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
