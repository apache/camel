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

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
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

    /**
     * The verbs of a rest as the YAML DSL has them: a list per kind (get, post, ...), not one list of all verbs.
     */
    protected <T extends VerbDefinition> void doWriteVerbs(JsonObject jo, List<T> verbs, Function<T, JsonObject> writer) {
        if (verbs != null) {
            for (T verb : verbs) {
                JsonObject child = writer.apply(verb);
                if (child != null) {
                    JsonArray arr = (JsonArray) jo.computeIfAbsent(verb.asVerb(), k -> new JsonArray());
                    arr.add(child);
                }
            }
        }
    }

    /**
     * The properties or constructor arguments of a bean as the YAML DSL has them: a map, with a nested map for nested
     * properties.
     */
    protected void doWriteBeanMap(JsonObject jo, String key, Map<?, ?> map) {
        if (map != null && !map.isEmpty()) {
            jo.put(key, beanMap(map));
        }
    }

    private static JsonObject beanMap(Map<?, ?> map) {
        JsonObject answer = new JsonObject();
        map.forEach((k, v) -> answer.put(String.valueOf(k), v instanceof Map<?, ?> m ? beanMap(m) : v));
        return answer;
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
        if (!uriAsParameters || questionMarkInPlaceholder(uri)) {
            // an optional placeholder before the query (https://host/{{?path}}?a=b): its ? is not where the query
            // starts, and the uri cannot be split into parameters without breaking it, so it is kept as written
            jo.put("uri", uri);
            return;
        }
        try {
            Map<String, String> params = null;
            RuntimeCamelCatalog catalog
                    = camelContext != null
                            ? camelContext.getCamelContextExtension().getContextPlugin(RuntimeCamelCatalog.class)
                            : null;
            int colon = uri.indexOf(':');
            int question = uri.indexOf('?');
            boolean schemeAndQuery = colon < 0 || question >= 0 && question < colon;
            if (catalog != null && !schemeAndQuery) {
                // scheme?a=b (as a uri built from parameters is): the query as written, the catalog would mangle a
                // value such as {{share}}/{{directory}} (normalizing a normalized Kamelet changed it)
                params = catalog.endpointProperties(uri);
                if (params != null && !params.isEmpty() && !uri.startsWith("kamelet:")
                        && (pathPartsDifferFromSyntax(catalog, uri) || !rebuildsThePath(catalog, uri, params)
                                || !samePlaceholders(uri, params))) {
                    // the catalog parsed the path into options that would mean something else: fewer path parts than
                    // the syntax has (azure-storage-blob:{{accountName}} for accountName/containerName: the one part went
                    // to containerName, the component reads it as accountName), or options it cannot write back as the
                    // same path (pulsar:{{type}}/{{tenant}}/{{ns}}/{{topic}} for persistence://tenant/namespace/topic)
                    jo.put("uri", uri);
                    return;
                }
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
                if ("kamelet".equals(scheme) && params.get("templateId") != null) {
                    // the Kamelet is named in the uri (kamelet:log-sink, kamelet:source), not as a templateId
                    // parameter: that is how Kamelets are written and read
                    params = new LinkedHashMap<>(params);
                    String path = params.remove("templateId");
                    String routeId = params.remove("routeId");
                    jo.put("uri", "kamelet:" + path + (routeId != null ? "/" + routeId : ""));
                } else {
                    jo.put("uri", scheme);
                }
            }
            if (params != null && !params.isEmpty()) {
                String written = (String) jo.get("uri");
                if (catalog != null && written != null && !written.startsWith("kamelet:")) {
                    int c = written.indexOf(':');
                    params = asCamelBuildsThem(catalog, c > 0 ? written.substring(0, c) : written, params);
                }
                JsonObject p = new JsonObject();
                params.forEach((k, v) -> p.put(k, parseValue(v)));
                jo.put("parameters", p);
            }
        } catch (Exception e) {
            jo.put("uri", uri);
        }
    }

    /**
     * Whether the uri has another number of path parts than the syntax of its component has path options, when the
     * syntax has more than one. Fewer: which option a part is cannot be told from the uri alone. More: an option would
     * get two parts (share: {{shareName}}/{{directoryName}} on azure-files://account/share), which the uri built from
     * the options breaks.
     */
    static boolean pathPartsDifferFromSyntax(RuntimeCamelCatalog catalog, String uri) {
        try {
            int colon = uri.indexOf(':');
            if (colon < 0) {
                return false;
            }
            String syntax = syntax(catalog, uri.substring(0, colon));
            if (syntax == null || syntax.indexOf(':') < 0) {
                return false;
            }
            int options = pathParts(syntax.substring(syntax.indexOf(':') + 1));
            if (options < 2) {
                return false;
            }
            return pathParts(path(uri).substring(colon + 1)) != options;
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject componentSchema(RuntimeCamelCatalog catalog, String scheme) {
        try {
            String json = catalog.componentJSonSchema(scheme);
            return json != null ? (JsonObject) Jsoner.deserialize(json) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String syntax(RuntimeCamelCatalog catalog, String scheme) {
        JsonObject schema = componentSchema(catalog, scheme);
        Object component = schema != null ? schema.get("component") : null;
        return component instanceof JsonObject c ? c.getString("syntax") : null;
    }

    /**
     * The options as Camel builds the uri from them: the path options first in the order of the syntax, then the others
     * sorted, with a secret option in RAW() as the YAML DSL wraps it. So a normalized file normalizes to itself.
     */
    static Map<String, String> asCamelBuildsThem(RuntimeCamelCatalog catalog, String scheme, Map<String, String> params) {
        JsonObject schema = componentSchema(catalog, scheme);
        Object properties = schema != null ? schema.get("properties") : null;
        if (!(properties instanceof JsonObject props)) {
            return params;
        }
        Map<String, String> answer = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : props.entrySet()) {
            if (e.getValue() instanceof JsonObject o && "path".equals(o.getString("kind")) && params.containsKey(e.getKey())) {
                answer.put(e.getKey(), params.get(e.getKey()));
            }
        }
        new TreeMap<>(params).forEach(answer::putIfAbsent);
        for (Map.Entry<String, String> e : answer.entrySet()) {
            Object option = props.get(e.getKey());
            String v = e.getValue();
            if (option instanceof JsonObject o && Boolean.TRUE.equals(o.getBoolean("secret")) && v != null
                    && !v.startsWith("#") && !v.startsWith("RAW(")) {
                e.setValue("RAW(" + v + ")");
            }
        }
        return answer;
    }

    private static int pathParts(String path) {
        int n = 0;
        for (String part : path.replaceFirst("^/+", "").split("[:/]")) {
            if (!part.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Whether the catalog builds the uri back from the options to the same path: only then do the options mean what the
     * uri says.
     */
    static boolean rebuildsThePath(RuntimeCamelCatalog catalog, String uri, Map<String, String> params) {
        try {
            String scheme = uri.substring(0, uri.indexOf(':'));
            String rebuilt = catalog.asEndpointUri(scheme, params, false);
            return rebuilt != null && path(rebuilt).equals(path(uri));
        } catch (Exception e) {
            return false;
        }
    }

    private static String path(String uri) {
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^{}]*}}");

    /** Whether the options have the same {{placeholders}} as the uri: a parse that broke one is not used. */
    static boolean samePlaceholders(String uri, Map<String, String> params) {
        List<String> inUri = placeholders(uri);
        List<String> inParams = new ArrayList<>();
        params.values().forEach(v -> inParams.addAll(placeholders(v)));
        Collections.sort(inUri);
        Collections.sort(inParams);
        return inUri.equals(inParams);
    }

    private static List<String> placeholders(String text) {
        List<String> answer = new ArrayList<>();
        if (text != null) {
            Matcher m = PLACEHOLDER.matcher(text);
            while (m.find()) {
                answer.add(m.group());
            }
        }
        return answer;
    }

    /** Whether the first ? of the uri is inside a {{...}} property placeholder, such as {{?name}}. */
    static boolean questionMarkInPlaceholder(String uri) {
        int idx = uri.indexOf('?');
        if (idx < 0) {
            return false;
        }
        return uri.lastIndexOf("{{", idx) > uri.lastIndexOf("}}", idx);
    }

    protected Object parseValue(String value) {
        if (value == null) {
            return null;
        }
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        // only a number when it is written as the number (such as 007, +5 or 1e3 are text, which is quoted)
        try {
            long l = Long.parseLong(value);
            if (Long.toString(l).equals(value)) {
                return l;
            }
        } catch (NumberFormatException e) {
            // not a long
        }
        try {
            double d = Double.parseDouble(value);
            if (Double.toString(d).equals(value)) {
                return d;
            }
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
