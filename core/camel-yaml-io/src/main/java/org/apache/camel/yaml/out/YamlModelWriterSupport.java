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
package org.apache.camel.yaml.out;

import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.yaml.io.YamlPrinter;

/**
 * Base class for the generated {@link YamlModelWriter}. Provides helper methods for building {@link JsonObject}
 * structures from model definitions.
 */
public abstract class YamlModelWriterSupport {

    protected boolean uriAsParameters;
    protected CamelContext camelContext;

    public void setUriAsParameters(boolean uriAsParameters) {
        this.uriAsParameters = uriAsParameters;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    protected void doWriteAttribute(JsonObject jo, String key, String value, String defaultValue) {
        if (value != null && (defaultValue == null || !defaultValue.equals(value))) {
            jo.put(key, parseValue(value));
        }
    }

    protected void doWriteValue(JsonObject jo, String value) {
        if (value != null && !value.isEmpty()) {
            jo.put("expression", value);
        }
    }

    protected <T> void doWriteChildElement(JsonObject jo, String key, T value, Function<T, JsonObject> writer) {
        if (value != null) {
            JsonObject child = writer.apply(value);
            if (child != null) {
                jo.put(key, child);
            }
        }
    }

    protected <T> void doWriteExpressionRef(JsonObject jo, T value, Function<T, JsonObject> writer) {
        if (value != null) {
            JsonObject result = writer.apply(value);
            if (result != null && !result.isEmpty()) {
                jo.put("expression", result);
            }
        }
    }

    protected void doMoveStepsUnderFrom(JsonObject jo) {
        Object steps = jo.remove("steps");
        if (steps != null) {
            JsonObject from = (JsonObject) jo.get("from");
            if (from != null) {
                from.put("steps", steps);
            }
        }
    }

    protected <T> void doWriteElementRef(JsonObject jo, T value, Function<T, JsonObject> writer) {
        if (value != null) {
            JsonObject result = writer.apply(value);
            if (result != null) {
                jo.putAll(result);
            }
        }
    }

    protected <T> void doWriteElementRefList(JsonObject jo, String key, List<T> list, Function<T, JsonObject> writer) {
        if (list != null && !list.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (T item : list) {
                JsonObject result = writer.apply(item);
                if (result != null) {
                    arr.add(result);
                }
            }
            if (!arr.isEmpty()) {
                if (key != null) {
                    jo.put(key, arr);
                }
            }
        }
    }

    protected <T> void doWriteOutputs(JsonObject jo, List<T> list, Function<T, JsonObject> writer) {
        doWriteElementRefList(jo, "steps", list, writer);
    }

    @SuppressWarnings("unchecked")
    protected <T> void doWriteChildList(
            JsonObject jo, String wrapperKey, String itemKey, List<T> list, Function<T, JsonObject> writer) {
        if (list != null && !list.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (T item : list) {
                if (item instanceof String s) {
                    arr.add(s);
                } else {
                    JsonObject child = writer.apply(item);
                    if (child != null) {
                        arr.add(child);
                    }
                }
            }
            if (!arr.isEmpty()) {
                String key = wrapperKey != null ? wrapperKey : itemKey;
                jo.put(key, arr);
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void doWriteStringList(JsonObject jo, String wrapperKey, String itemKey, List<String> list) {
        if (list != null && !list.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String s : list) {
                if (s != null) {
                    arr.add(s);
                }
            }
            if (!arr.isEmpty()) {
                String key = wrapperKey != null ? wrapperKey : itemKey;
                jo.put(key, arr);
            }
        }
    }

    protected JsonObject wrapNode(String key, JsonObject value) {
        JsonObject wrapper = new JsonObject();
        if (value != null) {
            wrapper.put(key, value);
        }
        return wrapper;
    }

    protected void expandUri(JsonObject jo, String uri) {
        if (uri == null) {
            return;
        }
        if (!uriAsParameters) {
            jo.put("uri", uri);
            return;
        }
        try {
            Map<String, String> params = null;
            RuntimeCamelCatalog catalog
                    = camelContext != null
                            ? camelContext.getCamelContextExtension().getContextPlugin(RuntimeCamelCatalog.class)
                            : null;
            if (catalog != null) {
                params = catalog.endpointProperties(uri);
            }
            if (params == null || params.isEmpty()) {
                Map<String, Object> raw = URISupport.parseQuery(URISupport.extractQuery(uri));
                params = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    params.put(e.getKey(), e.getValue().toString());
                }
                String base = URISupport.stripQuery(uri);
                jo.put("uri", base);
            } else {
                String scheme = uri;
                int idx = scheme.indexOf(':');
                if (idx != -1) {
                    scheme = scheme.substring(0, idx);
                }
                jo.put("uri", scheme);
            }
            if (params != null && !params.isEmpty()) {
                JsonObject p = new JsonObject();
                params.forEach((k, v) -> p.put(k, parseValue(v)));
                jo.put("parameters", p);
            }
        } catch (Exception e) {
            jo.put("uri", uri);
        }
    }

    protected Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // not a long
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // not a double
        }
        return value;
    }

    public String printAsYaml(Collection<?> roots) {
        return YamlPrinter.print(roots);
    }

    protected String toString(Boolean b) {
        return b != null ? b.toString() : null;
    }

    protected String toString(Enum<?> e) {
        return e != null ? e.name() : null;
    }

    protected String toString(Number n) {
        return n != null ? n.toString() : null;
    }

    protected String toString(byte[] b) {
        return b != null ? Base64.getEncoder().encodeToString(b) : null;
    }
}
