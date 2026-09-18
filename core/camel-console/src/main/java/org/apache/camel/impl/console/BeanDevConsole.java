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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "bean", description = "Displays Java beans from the registry")
public class BeanDevConsole extends AbstractDevConsole {

    public record PropertyEntry(
            @Metadata(description = "The property name") String name,
            @Metadata(description = "The property type (only present when the value is not null)") String type,
            @Metadata(description = "The property value") Object value) {
    }

    public record BeanEntry(
            @Metadata(description = "The bean name") String name,
            @Metadata(description = "The bean class type") String type,
            @Metadata(description = "The bean properties (only present when properties were requested and the bean has some)") List<PropertyEntry> properties) {
    }

    public record Response(@Metadata(description = "The beans, keyed by bean name") Map<String, BeanEntry> beans) {
    }

    public BeanDevConsole() {
        super("camel", "bean", "Bean", "Displays Java beans from the registry");
    }

    @Metadata(label = "query", description = "Filters the beans matching by name", javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Whether to include bean properties", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String PROPERTIES = "properties";

    @Metadata(label = "query", description = "Whether to include null values", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String NULLS = "nulls";

    @Metadata(label = "query", description = "Whether to include internal Camel beans", javaType = "java.lang.Boolean",
              defaultValue = "true")
    public static final String INTERNAL = "internal";

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        boolean properties = optionBoolean(options, PROPERTIES, true);
        boolean nulls = optionBoolean(options, NULLS, true);
        boolean internal = optionBoolean(options, INTERNAL, true);

        StringBuilder sb = new StringBuilder();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        try {
            Map<String, Object> beans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
            Stream<String> keys = beans.keySet().stream().filter(r -> accept(r, filter)).sorted(String::compareToIgnoreCase);
            keys.forEach(k -> {
                Object bean = beans.get(k);
                if (bean != null) {
                    boolean include = internal || !bean.getClass().getName().startsWith("org.apache.camel.");
                    if (include) {
                        sb.append(String.format("    %s (class: %s)%n", k, bean.getClass().getName()));

                        Map<String, Object> values = new TreeMap<>();
                        if (properties) {
                            try {
                                bi.getProperties(bean, values, null);
                            } catch (Throwable e) {
                                // ignore
                            }
                            values.forEach((pk, pv) -> {
                                if (pv == null) {
                                    if (nulls) {
                                        sb.append(String.format("        %s = null%n", pk));
                                    }
                                } else {
                                    String t = pv.getClass().getName();
                                    sb.append(String.format("        %s (%s) = %s%n", pk, t, pv));
                                }
                            });
                        }
                    }
                }
                sb.append("\n");
            });
        } catch (Exception e) {
            // ignore
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        boolean properties = optionBoolean(options, PROPERTIES, true);
        boolean nulls = optionBoolean(options, NULLS, true);
        boolean internal = optionBoolean(options, INTERNAL, true);

        Map<String, BeanEntry> beans = new LinkedHashMap<>();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        try {
            Map<String, Object> registryBeans = getCamelContext().getRegistry().findByTypeWithName(Object.class);
            Stream<String> keys
                    = registryBeans.keySet().stream().filter(r -> accept(r, filter)).sorted(String::compareToIgnoreCase);
            keys.forEach(k -> {
                Object bean = registryBeans.get(k);
                if (bean != null) {
                    boolean include = internal || !bean.getClass().getName().startsWith("org.apache.camel.");
                    if (include) {
                        Map<String, Object> values = new TreeMap<>();
                        if (properties) {
                            try {
                                bi.getProperties(bean, values, null);
                            } catch (Throwable e) {
                                // ignore
                            }
                        }

                        List<PropertyEntry> props = null;
                        if (!values.isEmpty()) {
                            props = new ArrayList<>();
                            for (Map.Entry<String, Object> entry : values.entrySet()) {
                                String pk = entry.getKey();
                                Object pv = entry.getValue();
                                Object value = pv;
                                String type = pv != null ? pv.getClass().getName() : null;
                                if (type != null) {
                                    value = Jsoner.trySerialize(pv);
                                    if (value == null) {
                                        // cannot serialize so escape
                                        value = Jsoner.escape(pv.toString());
                                    } else {
                                        // okay so use the value as-s
                                        value = pv;
                                    }
                                }
                                boolean accept = value != null || nulls;
                                if (accept) {
                                    props.add(new PropertyEntry(pk, type, value));
                                }
                            }
                        }

                        beans.put(k, new BeanEntry(k, bean.getClass().getName(), props));
                    }
                }
            });
        } catch (Exception e) {
            // ignore
        }

        return JsonRecordSupport.toJsonObject(new Response(beans));
    }

    private static boolean accept(String name, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(name, filter);
    }

}
