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
package org.apache.camel.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@DevConsole(name = "bean-model", description = "Displays beans from the DSL model")
public class BeanModelDevConsole extends AbstractDevConsole {

    public BeanModelDevConsole() {
        super("camel", "bean-model", "Bean Model", "Displays beans from the DSL model");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        Model model = getCamelContext().getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            for (BeanFactoryDefinition<?> b : model.getCustomBeans()) {
                Map<String, Object> values = new HashMap<>();
                Object target = getCamelContext().getRegistry().lookupByName(b.getName());
                if (target != null) {
                    try {
                        bi.getProperties(target, values, null);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                sb.append(String.format("    %s (%s)%n", b.getName(), b.getType()));
                if (b.getProperties() != null) {
                    b.getProperties().forEach((k, v) -> {
                        Object rv = values.get(k);
                        String type;
                        if (rv == null) {
                            rv = "null";
                            type = "null";
                        } else {
                            type = rv.getClass().getName();
                        }
                        sb.append(String.format("        %s = %s (type:%s)%n", k, rv, type));
                    });
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        JsonObject jo = new JsonObject();
        root.put("beans", jo);

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(getCamelContext());
        Model model = getCamelContext().getCamelContextExtension().getContextPlugin(Model.class);
        if (model != null) {
            for (BeanFactoryDefinition<?> b : model.getCustomBeans()) {
                Map<String, Object> values = new HashMap<>();
                Object target = getCamelContext().getRegistry().lookupByName(b.getName());
                if (target != null) {
                    try {
                        bi.getProperties(target, values, null);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                JsonObject jb = new JsonObject();
                jo.put(b.getName(), jb);
                jb.put("name", b.getName());
                jb.put("type", b.getType());
                if (b.getInitMethod() != null) {
                    jb.put("initMethod", b.getInitMethod());
                }
                if (b.getDestroyMethod() != null) {
                    jb.put("destroyMethod", b.getDestroyMethod());
                }
                if (b.getBuilderClass() != null) {
                    jb.put("builderClass", b.getBuilderClass());
                }
                if (b.getBuilderMethod() != null) {
                    jb.put("builderMethod", b.getBuilderMethod());
                }
                if (b.getFactoryBean() != null) {
                    jb.put("factoryBean", b.getFactoryBean());
                }
                if (b.getFactoryMethod() != null) {
                    jb.put("factoryMethod", b.getFactoryMethod());
                }
                if (b.getProperties() != null) {
                    JsonArray arr = new JsonArray();
                    b.getProperties().forEach((k, v) -> {
                        Object rv = values.get(k);
                        String type = rv != null ? rv.getClass().getName() : null;
                        JsonObject jp = new JsonObject();
                        jp.put("name", k);
                        if (type != null) {
                            jp.put("type", type);
                        }
                        jp.put("value", v);
                        arr.add(jp);
                    });
                    jb.put("modelProperties", arr);
                    JsonArray arr2 = new JsonArray();
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
                        JsonObject jp = new JsonObject();
                        jp.put("name", k);
                        if (type != null) {
                            jp.put("type", type);
                        }
                        jp.put("value", value);
                        arr2.add(jp);
                    });
                    jb.put("properties", arr2);
                }
            }
        }
        return root;
    }

}
