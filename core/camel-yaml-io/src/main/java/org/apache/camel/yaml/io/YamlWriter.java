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
package org.apache.camel.yaml.io;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringJoiner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.catalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * YAML writer which uses Jackson to dump to yaml format.
 *
 * Implementation notes:
 *
 * This writer is based on the same principle for the XML writer which parses the Camel routes (model classes) and emit
 * a StAX based events for start/end elements.
 *
 * However since the YAML DSL is not as easy to dump as XML, then we need to enrich with additional metadata from the
 * runtime catalog ({@link EipModel}). We then abuse the {@link EipModel} and store the route details in its metadata.
 * After this we transform from {@link EipModel} to {@link EipNode} to have a List/Map structure that we then transform
 * to JSon, and then from JSon to YAML.
 */
public class YamlWriter extends ServiceSupport implements CamelContextAware {

    private CamelContext camelContext;
    private final Writer writer;
    private final DefaultRuntimeCamelCatalog catalog;
    private final List<EipModel> roots = new ArrayList<>();
    private boolean routesIsRoot;
    private final Stack<EipModel> models = new Stack<>();
    private String expression;
    private boolean uriAsParameters;

    public YamlWriter(Writer writer) {
        this.writer = writer;
        this.catalog = new DefaultRuntimeCamelCatalog();
        this.catalog.setJSonSchemaResolver(new ModelJSonSchemaResolver());
        this.catalog.setCaching(false); // turn cache off as we store state per node
        this.catalog.start();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        if (camelContext != null) {
            DefaultRuntimeCamelCatalog runtime = (DefaultRuntimeCamelCatalog) PluginHelper.getRuntimeCamelCatalog(camelContext);
            if (runtime != null) {
                // use json schema resolver from camel context
                this.catalog.setJSonSchemaResolver(runtime.getJSonSchemaResolver());
            }
        }
    }

    public void setUriAsParameters(boolean uriAsParameters) {
        this.uriAsParameters = uriAsParameters;
    }

    public void startElement(String name) throws IOException {
        if ("routes".equals(name)) {
            routesIsRoot = true;
            return;
        }

        EipModel model = catalog.eipModel(name);
        if (model == null) {
            // not an EIP model
            return;
        }

        EipModel parent = models.isEmpty() ? null : models.peek();
        model.getMetadata().put("_parent", parent);
        models.push(model);
        if (parent == null) {
            // its a root element
            roots.add(model);
        }
    }

    public void startExpressionElement(String name) throws IOException {
        // currently building an expression
        this.expression = name;
    }

    public void endExpressionElement(String name) throws IOException {
        // expression complete, back to normal mode
        this.expression = null;
    }

    public void endElement(String name) throws IOException {
        if ("routes".equals(name)) {
            // we are done
            writer.write(toYaml());
            return;
        }

        EipModel model = catalog.eipModel(name);
        if (model == null) {
            // not an EIP model
            return;
        }

        EipModel last = models.isEmpty() ? null : models.peek();
        if (last != null && isLanguage(last)) {
            if (!models.isEmpty()) {
                models.pop();
            }
            // okay we ended a language which we need to set on a parent EIP
            EipModel parent = models.isEmpty() ? null : models.peek();
            if (parent != null) {
                String key = expressionName(parent, expression);
                if (key != null) {
                    parent.getMetadata().put(key, last);
                }
            }
            return;
        }

        if (last != null) {
            if (!models.isEmpty()) {
                models.pop();
            }
            // is this input/output on the parent
            EipModel parent = models.isEmpty() ? null : models.peek();
            if (parent != null) {
                if ("from".equals(name) && parent.isInput()) {
                    // only set input once
                    parent.getMetadata().put("_input", last);
                } else if ("choice".equals(parent.getName())) {
                    // special for choice/doCatch/doFinally
                    setMetadata(parent, name, last);
                } else if (parent.isOutput()) {
                    List<EipModel> list = (List<EipModel>) parent.getMetadata().get("_output");
                    if (list == null) {
                        list = new ArrayList<>();
                        parent.getMetadata().put("_output", list);
                    }
                    list.add(last);
                } else if ("marshal".equals(parent.getName()) || "unmarshal".equals(parent.getName())) {
                    parent.getMetadata().put("_dataFormatType", last);
                }
            }
        }

        if (models.isEmpty() && !routesIsRoot) {
            // we are done
            writer.write(toYaml());
        }
    }

    public void writeText(String name, String text) throws IOException {
        EipModel last = models.isEmpty() ? null : models.peek();
        if (last != null) {
            // special as writeText can be used for list of string values
            setMetadata(last, name, text);
        }
    }

    public void writeValue(String value) throws IOException {
        EipModel last = models.isEmpty() ? null : models.peek();
        if (last != null) {
            String key = valueName(last);
            if (key != null) {
                last.getMetadata().put(key, value);
            }
        }
    }

    public void addAttribute(String name, Object value) throws IOException {
        EipModel last = models.isEmpty() ? null : models.peek();
        if (last != null) {
            // uri should be expanded into more human-readable with parameters
            if (uriAsParameters && "uri".equals(name) && value != null) {
                try {
                    String base = StringHelper.before(value.toString(), ":");
                    if (base != null) {
                        Map parameters = catalog.endpointProperties(value.toString());
                        if (!parameters.isEmpty()) {
                            prepareParameters(parameters);
                            last.getMetadata().put("uri", base);
                            last.getMetadata().put("parameters", parameters);
                            return;
                        }
                    }
                } catch (Exception e) {
                    // ignore will attempt without catalog
                }
                try {
                    String base = URISupport.stripQuery(value.toString());
                    String query = URISupport.extractQuery(value.toString());
                    if (base != null && query != null) {
                        Map parameters = URISupport.parseQuery(query);
                        if (!parameters.isEmpty()) {
                            prepareParameters(parameters);
                            last.getMetadata().put("uri", base);
                            last.getMetadata().put("parameters", parameters);
                            return;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }

            last.getMetadata().put(name, value);
        }
    }

    private static void prepareParameters(Map<String, Object> parameters) {
        // convert "true" / "false" to boolean values
        parameters.forEach((k, v) -> {
            if ("true".equals(v) || "false".equals(v)) {
                Object s = Boolean.valueOf(v.toString());
                parameters.replace(k, s);
            }
        });
    }

    private EipNode asExpressionNode(EipModel model, String name) {
        EipNode node = new EipNode(name, null, false, true);
        doAsNode(model, node);
        return node;
    }

    private EipNode asNode(EipModel model) {
        EipNode node = new EipNode(model.getName(), null, false, false);
        doAsNode(model, node);
        return node;
    }

    private void doAsNode(EipModel model, EipNode node) {
        for (Map.Entry<String, Object> entry : model.getMetadata().entrySet()) {
            String key = entry.getKey();
            if ("_input".equals(key)) {
                EipModel m = (EipModel) entry.getValue();
                node.setInput(asNode(m));
            } else if ("_output".equals(key)) {
                List<EipModel> list = (List) entry.getValue();
                for (EipModel m : list) {
                    node.addOutput(asNode(m));
                }
            } else if ("choice".equals(node.getName()) && "otherwise".equals(key)) {
                EipModel other = (EipModel) entry.getValue();
                node.addOutput(asNode(other));
            } else if ("choice".equals(node.getName()) && "when".equals(key)) {
                Object v = entry.getValue();
                if (v instanceof List) {
                    // can be a list in choice
                    List<EipModel> list = (List) v;
                    for (EipModel m : list) {
                        node.addOutput(asNode(m));
                    }
                } else {
                    node.addOutput(asNode((EipModel) v));
                }
            } else if (("marshal".equals(node.getName()) || "unmarshal".equals(node.getName()))
                    && "_dataFormatType".equals(key)) {
                EipModel other = (EipModel) entry.getValue();
                node.addOutput(asNode(other));
            } else {
                boolean skip = key.startsWith("_") || key.equals("customId");
                if (skip) {
                    continue;
                }
                String exp = null;
                if (!isLanguage(model)) {
                    // special for expressions that are a property where we need to use expression name as key
                    exp = expressionName(model, key);
                }
                Object v = entry.getValue();
                if (v instanceof EipModel) {
                    EipModel m = (EipModel) entry.getValue();
                    if (exp == null || "expression".equals(exp)) {
                        v = asExpressionNode(m, m.getName());
                    } else {
                        v = asExpressionNode(m, exp);
                    }
                }
                if (exp != null && v instanceof EipNode) {
                    node.addExpression((EipNode) v);
                } else {
                    node.addProperty(key, v);
                    if ("expression".equals(key)) {
                        node.addProperty("language", model.getName());
                    }
                }
            }
        }
    }

    public String toYaml() {
        try {
            // model to json
            JsonArray arr = transformToJson(roots);
            // load into jackson
            JsonNode jsonNodeTree = new ObjectMapper().readTree(arr.toJson());
            // map to yaml via jackson
            YAMLMapper mapper = new YAMLMapper();
            mapper.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
            mapper.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
            mapper.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
            String jsonAsYaml = mapper.writeValueAsString(jsonNodeTree);
            // strip leading yaml indent of 2 spaces (because INDENT_ARRAYS_WITH_INDICATOR is enabled)
            StringJoiner sj = new StringJoiner("\n");
            for (String line : jsonAsYaml.split("\n")) {
                if (line.startsWith("  ")) {
                    line = line.substring(2);
                }
                sj.add(line);
            }
            sj.add(""); // end with empty line
            jsonAsYaml = sj.toString();
            return jsonAsYaml;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonArray transformToJson(List<EipModel> models) {
        JsonArray arr = new JsonArray();
        for (EipModel model : models) {
            JsonObject jo = asJSonNode(model);
            arr.add(jo);
        }
        return arr;
    }

    private JsonObject asJSonNode(EipModel model) {
        JsonObject answer = new JsonObject();
        JsonObject jo = new JsonObject();
        answer.put(model.getName(), jo);

        for (Map.Entry<String, Object> entry : model.getMetadata().entrySet()) {
            String key = entry.getKey();
            boolean skip = key.equals("customId");
            if (skip) {
                continue;
            }
            Object value = entry.getValue();
            if (value != null) {
                if (value instanceof Collection<?>) {
                    Collection<?> col = (Collection<?>) value;
                    List<Object> list = new ArrayList<>();
                    for (Object v : col) {
                        Object r = v;
                        if (r instanceof EipModel) {
                            EipNode en = asNode((EipModel) r);
                            value = en.asJsonObject();
                            JsonObject wrap = new JsonObject();
                            wrap.put(en.getName(), value);
                            r = wrap;
                        }
                        list.add(r);
                    }
                    if ("_output".equals(key)) {
                        key = "steps";
                    }
                    // special with "from" where outputs needs to be embedded
                    if (jo.containsKey("from")) {
                        jo = jo.getMap("from");
                    }
                    jo.put(key, list);
                } else {
                    if (value instanceof EipModel) {
                        EipNode r = asNode((EipModel) value);
                        value = r.asJsonObject();
                        jo.put(r.getName(), value);
                    } else {
                        jo.put(key, value);
                    }
                }
            }
        }

        return answer;
    }

    @SuppressWarnings("unchecked")
    private static void setMetadata(EipModel model, String name, Object value) {
        // special for choice
        boolean array = isArray(model, name);
        if (array) {
            List<Object> list = (List<Object>) model.getMetadata().get(name);
            if (list == null) {
                list = new ArrayList<>();
                model.getMetadata().put(name, list);
            }
            list.add(value);
        } else {
            model.getMetadata().put(name, value);
        }
    }

    private static String valueName(EipModel model) {
        return model.getOptions().stream()
                .filter(o -> "value".equals(o.getKind()))
                .map(BaseOptionModel::getName)
                .findFirst().orElse(null);
    }

    private static String expressionName(EipModel model, String name) {
        return model.getOptions().stream()
                .filter(o -> "expression".equals(o.getKind()))
                .map(BaseOptionModel::getName)
                .filter(oName -> name == null || oName.equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private static boolean isArray(EipModel model, String name) {
        return model.getOptions().stream()
                .filter(o -> o.getName().equalsIgnoreCase(name))
                .map(o -> "array".equals(o.getType()))
                .findFirst().orElse(false);
    }

    private static boolean isLanguage(EipModel model) {
        return model.getJavaType().startsWith("org.apache.camel.model.language");
    }

}
