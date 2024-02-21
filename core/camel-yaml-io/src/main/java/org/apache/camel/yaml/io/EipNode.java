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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Represents a node during dumping model to Yaml DSL.
 */
class EipNode {

    private final String name;
    private final EipNode parent;
    private final boolean output;
    private final boolean expression;
    private EipNode input;
    private List<EipNode> outputs;
    private List<EipNode> expressions;
    private String text;
    private Map<String, Object> properties;

    public EipNode(String name, EipNode parent, boolean output, boolean expression) {
        this.name = name;
        this.parent = parent;
        this.output = output;
        this.expression = expression;
    }

    public String getName() {
        return name;
    }

    public EipNode getParent() {
        return parent;
    }

    public EipNode getInput() {
        return input;
    }

    public void setInput(EipNode input) {
        this.input = input;
    }

    public boolean isOutput() {
        return output;
    }

    public boolean isExpression() {
        return expression;
    }

    public List<EipNode> getOutputs() {
        if (outputs == null) {
            return Collections.emptyList();
        }
        return outputs;
    }

    public void addOutput(EipNode output) {
        if (outputs == null) {
            outputs = new ArrayList<>();
        }
        outputs.add(output);
    }

    public List<EipNode> getExpressions() {
        if (expressions == null) {
            return Collections.emptyList();
        }
        return expressions;
    }

    public void addExpression(EipNode output) {
        if (expressions == null) {
            expressions = new ArrayList<>();
        }
        expressions.add(output);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            return Collections.emptyMap();
        }
        return properties;
    }

    public void addProperty(String key, Object value) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(key, value);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Converts this node to JSon
     */
    JsonObject asJsonObject() {
        JsonObject answer = new JsonObject();

        if (properties != null) {
            answer.putAll(properties);
        }

        if (expressions != null) {
            for (EipNode o : expressions) {
                String key = o.getName();
                String language = (String) o.getProperties().remove("language");
                JsonObject r = o.asJsonObject();
                if (!key.equals(o.getName())) {
                    // need to wrap if sub element such as aggregate with correlationExpression
                    JsonObject wrap = new JsonObject();
                    wrap.put(o.getName(), r);
                    answer.put(key, wrap);
                } else if (language != null && !key.equals(language)) {
                    JsonObject wrap = new JsonObject();
                    wrap.put(language, r);
                    answer.put(key, wrap);
                } else {
                    answer.put(key, r);
                }
            }
        }
        if (outputs != null) {
            // sort so otherwise is last
            outputs.sort((o1, o2) -> {
                if ("otherwise".equals(o1.name)) {
                    return 1;
                } else if ("otherwise".equals(o2.name)) {
                    return -1;
                }
                return 0;
            });
            if (("marshal".equals(name) || "unmarshal".equals(name)) && outputs.size() == 1) {
                EipNode o = outputs.get(0);
                JsonObject jo = o.asJsonObject();
                answer.put(o.getName(), jo);
            } else {
                JsonArray arr = new JsonArray();
                for (EipNode o : outputs) {
                    JsonObject r = o.asJsonObject();
                    JsonObject wrap = new JsonObject();
                    wrap.put(o.getName(), r);
                    arr.add(wrap);
                }
                answer.put("steps", arr);
            }
        }

        return answer;
    }

}
