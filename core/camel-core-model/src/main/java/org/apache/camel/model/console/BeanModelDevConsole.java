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
package org.apache.camel.model.console;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "bean-model", description = "Displays beans from the DSL model")
public class BeanModelDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Filters the beans matching by name",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    @Metadata(label = "query", description = "Whether to include bean properties", defaultValue = "true",
              javaType = "java.lang.Boolean")
    public static final String PROPERTIES = "properties";

    @Metadata(label = "query", description = "Whether to include null values", defaultValue = "true",
              javaType = "java.lang.Boolean")
    public static final String NULLS = "nulls";

    public record PropertyEntry(
            @Metadata(description = "The property name") String name,
            @Metadata(description = "The property value type (only present when the value is known)") String type,
            @Metadata(description = "The property value") Object value) {
    }

    public record BeanEntry(
            @Metadata(description = "The bean name") String name,
            @Metadata(description = "The bean type") String type,
            @Metadata(description = "The init method name (only present when configured)") String initMethod,
            @Metadata(description = "The destroy method name (only present when configured)") String destroyMethod,
            @Metadata(description = "The builder class name (only present when configured)") String builderClass,
            @Metadata(description = "The builder method name (only present when configured)") String builderMethod,
            @Metadata(description = "The factory bean name (only present when configured)") String factoryBean,
            @Metadata(description = "The factory method name (only present when configured)") String factoryMethod,
            @Metadata(description = "The bean properties as declared in the DSL model (only present when there are any)") List<PropertyEntry> modelProperties,
            @Metadata(description = "The bean properties resolved from the running bean instance (only present when there are any)") List<PropertyEntry> properties) {
    }

    public record Response(@Metadata(description = "The beans keyed by bean name") Map<String, BeanEntry> beans) {
    }

    public BeanModelDevConsole() {
        super("camel", "bean-model", "Bean Model", "Displays beans from the DSL model");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        boolean properties = optionBoolean(options, PROPERTIES, true);
        boolean nulls = optionBoolean(options, NULLS, true);

        StringBuilder sb = new StringBuilder(256);

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        Model model = getCamelContext().getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            for (BeanFactoryDefinition<?> b : model.getCustomBeans()) {
                String name = b.getName();
                if (!accept(name, filter)) {
                    continue;
                }

                Map<String, Object> values = new TreeMap<>();
                Object target = getCamelContext().getRegistry().lookupByName(name);
                if (target != null && properties) {
                    try {
                        bi.getProperties(target, values, null);
                    } catch (Throwable e) {
                        // ignore
                    }
                }
                sb.append(String.format("    %s (%s)%n", b.getName(), b.getType()));
                if (properties && b.getProperties() != null) {
                    b.getProperties().forEach((k, v) -> {
                        Object rv = values.get(k);
                        String type;
                        if (rv == null) {
                            if (nulls) {
                                sb.append(String.format("        %s = null%n", k));

                            }
                        } else {
                            type = rv.getClass().getName();
                            sb.append(String.format("        %s = %s (type:%s)%n", k, rv, type));
                        }
                    });
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        boolean properties = optionBoolean(options, PROPERTIES, true);
        boolean nulls = optionBoolean(options, NULLS, true);

        Map<String, BeanEntry> beans = new LinkedHashMap<>();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        Model model = getCamelContext().getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            for (BeanFactoryDefinition<?> b : model.getCustomBeans()) {
                String name = b.getName();
                if (!accept(name, filter)) {
                    continue;
                }

                Map<String, Object> values = new TreeMap<>();
                Object target = getCamelContext().getRegistry().lookupByName(name);
                if (target != null && properties) {
                    try {
                        bi.getProperties(target, values, null);
                    } catch (Throwable e) {
                        // ignore
                    }
                }

                List<PropertyEntry> modelProperties = null;
                List<PropertyEntry> resolvedProperties = null;
                if (b.getProperties() != null) {
                    List<PropertyEntry> arr = new ArrayList<>();
                    b.getProperties().forEach((k, v) -> {
                        Object rv = values.get(k);
                        String type = rv != null ? rv.getClass().getName() : null;
                        boolean accept = v != null || nulls;
                        if (accept) {
                            arr.add(new PropertyEntry(k, type, v));
                        }
                    });
                    modelProperties = arr.isEmpty() ? null : arr;

                    List<PropertyEntry> arr2 = new ArrayList<>();
                    b.getProperties().forEach((k, v) -> {
                        Object rv = values.get(k);
                        Object value = rv;
                        String type = rv != null ? rv.getClass().getName() : null;
                        if (type != null) {
                            value = Jsoner.trySerialize(rv);
                            if (value == null) {
                                // cannot serialize so escape
                                value = Jsoner.escape(rv.toString());
                            } else {
                                // okay so use the value as-s
                                value = rv;
                            }
                        }
                        boolean accept = value != null || nulls;
                        if (accept) {
                            arr2.add(new PropertyEntry(k, type, value));
                        }
                    });
                    resolvedProperties = arr2.isEmpty() ? null : arr2;
                }

                beans.put(b.getName(), new BeanEntry(
                        b.getName(), b.getType(), b.getInitMethod(), b.getDestroyMethod(), b.getBuilderClass(),
                        b.getBuilderMethod(), b.getFactoryBean(), b.getFactoryMethod(), modelProperties,
                        resolvedProperties));
            }
        }

        Response response = new Response(beans);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static boolean accept(String name, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        return PatternHelper.matchPattern(name, filter);
    }

}
