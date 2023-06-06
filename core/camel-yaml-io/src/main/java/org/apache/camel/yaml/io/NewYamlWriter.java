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
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.camel.catalog.impl.DefaultRuntimeCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.EipModel;

public class NewYamlWriter {

    // doTry / doCatch (special)
    // circuitBreaker (special)

    private final Writer writer;
    private final int spaces;
    private final String lineSeparator;
    private final DefaultRuntimeCamelCatalog catalog;
    private final List<EipModel> roots = new ArrayList<>();
    private final Stack<EipModel> models = new Stack<>();
    private String expression;

    /**
     * @param writer not null
     */
    public NewYamlWriter(Writer writer) throws IOException {
        this(writer, 2, null);
    }

    /**
     * @param writer        not null
     * @param spaces        number of spaces to indent
     * @param lineSeparator could be null, but the normal way is valid line separator ("\n" on UNIX).
     */
    public NewYamlWriter(Writer writer, int spaces, String lineSeparator) throws IOException {
        this.writer = writer;
        this.spaces = spaces;
        this.lineSeparator = validateLineSeparator(lineSeparator);
        this.catalog = new DefaultRuntimeCamelCatalog();
        this.catalog.setCaching(false); // turn cache off as we store state per node
        this.catalog.setJSonSchemaResolver(new ModelJSonSchemaResolver());
        this.catalog.start();
    }

    private static String validateLineSeparator(String lineSeparator) {
        String ls = lineSeparator != null ? lineSeparator : System.lineSeparator();
        if (!(ls.equals("\n") || ls.equals("\r") || ls.equals("\r\n"))) {
            throw new IllegalArgumentException("Requested line separator is invalid.");
        }
        return ls;
    }

    public void startElement(String name) throws IOException {
        EipModel model = catalog.eipModel(name);
        if (model != null) {
            EipModel parent = models.isEmpty() ? null : models.peek();
            model.getMetadata().put("_parent", parent);
            models.push(model);
            if (parent == null) {
                // its a root element
                roots.add(model);
            }
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
                }
            }
        }

        if (models.isEmpty()) {
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
            last.getMetadata().put(name, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setMetadata(EipModel model, String name, Object value) {
        // special for choice
        boolean array = isArray(model, name);
        if (array) {
            List list = (List<EipModel>) model.getMetadata().get(name);
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

    protected List<EipNode> transformToNodes(List<EipModel> models) {
        List<EipNode> nodes = new ArrayList<>();
        for (EipModel model : models) {
            EipNode node = asNode(model);
            nodes.add(node);
        }
        return nodes;
    }

    protected EipNode asExpressionNode(EipModel model, String name) {
        EipNode node = new EipNode(name, null, false, true);
        doAsNode(model, node);
        return node;
    }

    protected EipNode asNode(EipModel model) {
        EipNode node = new EipNode(model.getName(), null, false, false);
        doAsNode(model, node);
        return node;
    }

    protected void doAsNode(EipModel model, EipNode node) {
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
                }
            }
        }
    }

    public String toYaml() {
        StringBuilder sb = new StringBuilder();
        for (EipNode node : transformToNodes(roots)) {
            String s = node.dump(0, spaces, lineSeparator, true);
            sb.append(s);
            sb.append(lineSeparator);
        }
        return sb.toString();
    }

}
