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

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "bean", description = "Displays Java beans from the registry")
public class BeanDevConsole extends AbstractDevConsole {

    public BeanDevConsole() {
        super("camel", "bean", "Bean", "Displays Java beans from the registry");
    }

    /**
     * Filters the beans matching by name
     */
    public static final String FILTER = "filter";

    /**
     * Whether to include bean properties
     */
    public static final String PROPERTIES = "properties";

    /**
     * Whether to include null values
     */
    public static final String NULLS = "nulls";

    /**
     * Whether to include internal Camel beans
     */
    public static final String INTERNAL = "internal";

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = (String) options.get(FILTER);
        boolean properties = "true".equals(options.getOrDefault(PROPERTIES, "true").toString());
        boolean nulls = "true".equals(options.getOrDefault(NULLS, "true").toString());
        boolean internal = "true".equals(options.getOrDefault(INTERNAL, "true").toString());

        StringBuilder sb = new StringBuilder();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        try {
            Map<String, Object> beans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
            Stream<String> keys = beans.keySet().stream().filter(r -> accept(r, filter)).sorted(String::compareToIgnoreCase);
            keys.forEach(k -> {
                Object bean = beans.get(k);
                if (shouldIncludeBean(bean, internal)) {
                    sb.append(String.format("    %s (class: %s)%n", k, bean.getClass().getName()));
                    if (properties) {
                        appendBeanPropertiesText(sb, bi, bean, nulls);
                    }
                }
                sb.append("\n");
            });
        } catch (Exception e) {
            // ignore
        }

        return sb.toString();
    }

    private void appendBeanPropertiesText(StringBuilder sb, BeanIntrospection bi, Object bean, boolean nulls) {
        Map<String, Object> values = new TreeMap<>();
        try {
            bi.getProperties(bean, values, null);
        } catch (Throwable e) {
            // ignore
        }
        values.forEach((pk, pv) -> appendPropertyText(sb, pk, pv, nulls));
    }

    private void appendPropertyText(StringBuilder sb, String pk, Object pv, boolean nulls) {
        if (pv == null) {
            if (nulls) {
                sb.append(String.format("        %s = null%n", pk));
            }
        } else {
            String t = pv.getClass().getName();
            sb.append(String.format("        %s (%s) = %s%n", pk, t, pv));
        }
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        String filter = (String) options.get(FILTER);
        boolean properties = "true".equals(options.getOrDefault(PROPERTIES, "true").toString());
        boolean nulls = "true".equals(options.getOrDefault(NULLS, "true").toString());
        boolean internal = "true".equals(options.getOrDefault(INTERNAL, "true").toString());

        JsonObject root = new JsonObject();
        JsonObject jo = new JsonObject();
        root.put("beans", jo);

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        try {
            Map<String, Object> beans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
            Stream<String> keys = beans.keySet().stream().filter(r -> accept(r, filter)).sorted(String::compareToIgnoreCase);
            keys.forEach(k -> {
                Object bean = beans.get(k);
                if (shouldIncludeBean(bean, internal)) {
                    JsonObject jb = buildBeanJson(k, bean, bi, properties, nulls);
                    jo.put(k, jb);
                }
            });
        } catch (Exception e) {
            // ignore
        }

        return root;
    }

    private JsonObject buildBeanJson(String name, Object bean, BeanIntrospection bi, boolean properties, boolean nulls) {
        JsonObject jb = new JsonObject();
        jb.put("name", name);
        jb.put("type", bean.getClass().getName());

        if (!properties) {
            return jb;
        }

        Map<String, Object> values = new TreeMap<>();
        try {
            bi.getProperties(bean, values, null);
        } catch (Throwable e) {
            // ignore
        }

        if (!values.isEmpty()) {
            JsonArray arr = new JsonArray();
            values.forEach((pk, pv) -> addPropertyToArray(arr, pk, pv, nulls));
            jb.put("properties", arr);
        }

        return jb;
    }

    private void addPropertyToArray(JsonArray arr, String pk, Object pv, boolean nulls) {
        Object value = resolvePropertyValue(pv);
        if (value == null && !nulls) {
            return;
        }

        JsonObject jp = new JsonObject();
        jp.put("name", pk);
        if (pv != null) {
            jp.put("type", pv.getClass().getName());
        }
        jp.put("value", value);
        arr.add(jp);
    }

    private Object resolvePropertyValue(Object pv) {
        if (pv == null) {
            return null;
        }
        Object serialized = Jsoner.trySerialize(pv);
        if (serialized == null) {
            // cannot serialize so escape
            return Jsoner.escape(pv.toString());
        }
        // okay so use the value as-is
        return pv;
    }

    private static boolean shouldIncludeBean(Object bean, boolean internal) {
        if (bean == null) {
            return false;
        }
        return internal || !bean.getClass().getName().startsWith("org.apache.camel.");
    }

    private static boolean accept(String name, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(name, filter);
    }

}
