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

import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Represents a node during dumping model to Yaml DSL.
 */
class EipNode {

    private String name;
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

    public void clearName() {
        this.name = null;
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

    public String dump(int level, int spaces, String lineSeparator, boolean steps) {
        if ("choice".equals(name)) {
            // special for choice EIP
            return dumpChoice(level, spaces, lineSeparator);
        }

        StringBuilder sb = new StringBuilder();
        String n = name;

        if (n != null && steps) {
            n = "- " + name;
        }
        if (n != null) {
            sb.append(padString(spaces, level)).append(n).append(":");
            if (n.startsWith("- ")) {
                level++;
            }
        }
        if (expressions != null) {
            level++;
            for (EipNode child : expressions) {
                if (child.getExpressions().isEmpty()) {
                    sb.append(lineSeparator).append(padString(spaces, level)).append("expression:");
                    level++;
                }
                String text = child.dumpExpression(level, spaces, lineSeparator);
                sb.append("\n");
                sb.append(text);
                if (child.getExpressions().isEmpty()) {
                    level--;
                }
            }
            level--;
        }
        if (text != null) {
            String escaped = escape(spaces, level + 2, lineSeparator, text);
            sb.append(lineSeparator).append(padString(spaces, level + 1)).append("expression: ").append(escaped);
        }
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String escaped = escape(spaces, level + 1, lineSeparator, entry.getValue());
                String line = String.format("%s: %s", entry.getKey(), escaped);
                sb.append(lineSeparator).append(padString(spaces, level + 1)).append(line);
            }
        }
        if (input != null) {
            String text = input.dump(level + 1, spaces, lineSeparator, false);
            sb.append(lineSeparator);
            sb.append(text);
            level++;
        }
        if (outputs != null) {
            level++;
            sb.append("\n").append(padString(spaces, level)).append("steps:");
            level++;
            for (EipNode child : outputs) {
                String text = child.dump(level, spaces, lineSeparator, true);
                sb.append("\n");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    public String dumpExpression(int level, int spaces, String lineSeparator) {
        StringBuilder sb = new StringBuilder();
        String n = name;
        sb.append(padString(spaces, level)).append(n).append(":");
        if (n.startsWith("- ")) {
            level++;
        }
        if (text != null) {
            String escaped = escape(spaces, level + 1, lineSeparator, text);
            sb.append(lineSeparator).append(padString(spaces, level + 1)).append("expression: ").append(escaped);
        }
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String escaped = escape(spaces, level + 1, lineSeparator, entry.getValue());
                String line = String.format("%s: %s", entry.getKey(), escaped);
                sb.append(lineSeparator).append(padString(spaces, level + 1)).append(line);
            }
        }
        if (expressions != null) {
            level++;
            for (EipNode child : expressions) {
                String text = child.dumpExpression(level, spaces, lineSeparator);
                sb.append("\n");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    public String dumpChoice(int level, int spaces, String lineSeparator) {
        StringBuilder sb = new StringBuilder();

        sb.append(padString(spaces, level)).append("- choice").append(":");
        level++;
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String escaped = escape(spaces, level + 1, lineSeparator, entry.getValue());
                String line = String.format("%s: %s", entry.getKey(), escaped);
                sb.append(lineSeparator).append(padString(spaces, level + 1)).append(line);
            }
        }
        // sort so otherwise is last
        if (outputs != null) {
            outputs.sort((o1, o2) -> {
                if ("otherwise".equals(o1.name)) {
                    return 1;
                } else if ("otherwise".equals(o2.name)) {
                    return -1;
                }
                return 0;
            });
            // when
            for (int i = 0; i < outputs.size(); i++) {
                EipNode child = outputs.get(i);
                if (i == 0 || "otherwise".equals(child.name)) {
                    level++;
                    sb.append(lineSeparator).append(padString(spaces, level)).append(child.name).append(":");
                    level++;
                }
                child.clearName();
                String text = child.dump(level, spaces, lineSeparator, true);
                sb.append("\n");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private static String padString(int spaces, int level) {
        return " ".repeat(spaces).repeat(level);
    }

    @Override
    public String toString() {
        return name;
    }

    private static String escape(int spaces, int level, String lineSeparator, Object value) {
        // escapes the yaml
        StringBuilder sb = new StringBuilder();

        String text = value.toString();

        if (text.contains(lineSeparator)) {
            // multi lined values
            String[] lines = text.split(lineSeparator);
            sb.append("|-");
            for (String line : lines) {
                sb.append(lineSeparator).append(padString(spaces, level + 1)).append(line);
            }
        } else if (text.contains("\\") || text.contains("//")) {
            // using slash/backslashes
            sb.append(">-");
            sb.append(lineSeparator).append(padString(spaces, level + 1)).append(text);
        } else if (StringHelper.isSingleQuoted(text)) {
            // single quotes should be escaped to triple
            sb.append("''");
            sb.append(text);
            sb.append("''");
        } else if (StringHelper.isDoubleQuoted(text)) {
            // double quotes should be enclosed by single quote
            sb.append("'");
            // single quotes should be escaped
            text = text.replace("'", "''");
            sb.append(text);
            sb.append("'");
        } else if (valueHasQuotableChar(text)) {
            // should be enclosed by single quote
            sb.append("'");
            // single quotes should be escaped
            text = text.replace("'", "''");
            sb.append(text);
            sb.append("'");
        } else {
            sb.append(text);
        }

        return sb.toString();
    }

    /**
     * As per YAML <a href="https://yaml.org/spec/1.2/spec.html#id2788859">Plain Style</a>unquoted strings are
     * restricted to a reduced charset and must be quoted in case they contain one of the following characters or
     * character combinations.
     */
    private static boolean valueHasQuotableChar(String inputStr) {
        // https://github.com/FasterXML/jackson-dataformats-text/blob/2.16/yaml/src/main/java/com/fasterxml/jackson/dataformat/yaml/util/StringQuotingChecker.java
        final int end = inputStr.length();
        for (int i = 0; i < end; ++i) {
            switch (inputStr.charAt(i)) {
                case '[':
                case ']':
                case '{':
                case '}':
                case ',':
                    return true;
                case '#':
                    // [dataformats-text#201]: limit quoting with MINIMIZE_QUOTES
                    if (i > 0) {
                        char d = inputStr.charAt(i - 1);
                        if (' ' == d || '\t' == d) {
                            return true;
                        }
                    }
                    break;
                case ':':
                    // [dataformats-text#201]: limit quoting with MINIMIZE_QUOTES
                    if (i < (end - 1)) {
                        char d = inputStr.charAt(i + 1);
                        if (' ' == d || '\t' == d) {
                            return true;
                        }
                    }
                    break;
                default:
            }
        }
        return false;
    }

    public JsonObject asJsonObject() {
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
            JsonArray arr = new JsonArray();
            for (EipNode o : outputs) {
                JsonObject r = o.asJsonObject();
                JsonObject wrap = new JsonObject();
                wrap.put(o.getName(), r);
                arr.add(wrap);
            }
            answer.put("steps", arr);
        }

        return answer;
    }

}
