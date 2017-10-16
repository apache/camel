/**
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
package org.apache.camel.tools.apt.helper;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.simple.JsonObject;
import org.json.simple.Jsoner;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
public final class JsonSchemaHelper {

    private static final String VALID_CHARS = ".-='/\\!&():;";

    private JsonSchemaHelper() {
    }

    public static String toJson(String name, String displayName, String kind, Boolean required, String type, String defaultValue, String description,
                                Boolean deprecated, String deprecationNote, Boolean secret, String group, String label, boolean enumType, Set<String> enums,
                                boolean oneOfType, Set<String> oneOffTypes, boolean asPredicate, String optionalPrefix, String prefix, boolean multiValue) {
        String typeName = JsonSchemaHelper.getType(type, enumType);

        StringBuilder sb = new StringBuilder();
        sb.append(Strings.doubleQuote(name));
        sb.append(": { \"kind\": ");
        sb.append(Strings.doubleQuote(kind));

        // compute a display name if we don't have anything
        if (Strings.isNullOrEmpty(displayName)) {
            displayName = Strings.asTitle(name);
        }
        // we want display name early so its easier to spot
        sb.append(", \"displayName\": ");
        sb.append(Strings.doubleQuote(displayName));

        // we want group early so its easier to spot
        if (!Strings.isNullOrEmpty(group)) {
            sb.append(", \"group\": ");
            sb.append(Strings.doubleQuote(group));
        }

        // we want label early so its easier to spot
        if (!Strings.isNullOrEmpty(label)) {
            sb.append(", \"label\": ");
            sb.append(Strings.doubleQuote(label));
        }

        if (required != null) {
            // boolean type
            sb.append(", \"required\": ");
            sb.append(required.toString());
        }

        sb.append(", \"type\": ");
        if ("enum".equals(typeName)) {
            String actualType = JsonSchemaHelper.getType(type, false);
            sb.append(Strings.doubleQuote(actualType));
            sb.append(", \"javaType\": \"" + type + "\"");
            CollectionStringBuffer enumValues = new CollectionStringBuffer();
            for (Object value : enums) {
                enumValues.append(Strings.doubleQuote(value.toString()));
            }
            sb.append(", \"enum\": [ ");
            sb.append(enumValues.toString());
            sb.append(" ]");
        } else if (oneOfType) {
            sb.append(Strings.doubleQuote(typeName));
            sb.append(", \"javaType\": \"" + type + "\"");
            CollectionStringBuffer oneOfValues = new CollectionStringBuffer();
            for (Object value : oneOffTypes) {
                oneOfValues.append(Strings.doubleQuote(value.toString()));
            }
            sb.append(", \"oneOf\": [ ");
            sb.append(oneOfValues.toString());
            sb.append(" ]");
        } else if ("array".equals(typeName)) {
            sb.append(Strings.doubleQuote("array"));
            sb.append(", \"javaType\": \"" + type + "\"");
        } else {
            sb.append(Strings.doubleQuote(typeName));
            sb.append(", \"javaType\": \"" + type + "\"");
        }

        if (!Strings.isNullOrEmpty(optionalPrefix)) {
            sb.append(", \"optionalPrefix\": ");
            String text = safeDefaultValue(optionalPrefix);
            sb.append(Strings.doubleQuote(text));
        }

        if (!Strings.isNullOrEmpty(prefix)) {
            sb.append(", \"prefix\": ");
            String text = safeDefaultValue(prefix);
            sb.append(Strings.doubleQuote(text));
        }
        if (multiValue) {
            // boolean value
            sb.append(", \"multiValue\": true");
        }

        if (deprecated != null) {
            sb.append(", \"deprecated\": ");
            // boolean value
            sb.append(deprecated.toString());
        }
        if (!Strings.isNullOrEmpty(deprecationNote)) {
            sb.append(", \"deprecationNote\": ");
            sb.append(Strings.doubleQuote(deprecationNote));
        }

        if (secret != null) {
            sb.append(", \"secret\": ");
            // boolean value
            sb.append(secret.toString());
        }

        if (!Strings.isNullOrEmpty(defaultValue)) {
            sb.append(", \"defaultValue\": ");
            String text = safeDefaultValue(defaultValue);
            // the type can either be boolean, integer, number or text based
            if ("boolean".equals(typeName) || "integer".equals(typeName) || "number".equals(typeName)) {
                sb.append(text);
            } else {
                // text should be quoted
                sb.append(Strings.doubleQuote(text));
            }
        }

        // for expressions we want to know if it must be used as predicate or not
        boolean predicate = "expression".equals(kind) || asPredicate;
        if (predicate) {
            sb.append(", \"asPredicate\": ");
            if (asPredicate) {
                sb.append("true");
            } else {
                sb.append("false");
            }
        }

        if (!Strings.isNullOrEmpty(description)) {
            sb.append(", \"description\": ");
            String text = sanitizeDescription(description, false);
            sb.append(Strings.doubleQuote(text));
        }

        sb.append(" }");
        return sb.toString();
    }

    /**
     * Gets the JSon schema type.
     *
     * @param   type the java type
     * @return  the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(String type, boolean enumType) {
        if (enumType) {
            return "enum";
        } else if (type == null) {
            // return generic type for unknown type
            return "object";
        } else if (type.equals(URI.class.getName()) || type.equals(URL.class.getName())) {
            return "string";
        } else if (type.equals(File.class.getName())) {
            return "string";
        } else if (type.equals(Date.class.getName())) {
            return "string";
        } else if (type.startsWith("java.lang.Class")) {
            return "string";
        } else if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) {
            return "array";
        }

        String primitive = getPrimitiveType(type);
        if (primitive != null) {
            return primitive;
        }

        return "object";
    }

    /**
     * Gets the JSon schema primitive type.
     *
     * @param   name the java type
     * @return  the json schema primitive type, or <tt>null</tt> if not a primitive
     */
    public static String getPrimitiveType(String name) {

        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return "string";
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return "array";
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return "array";
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return "array";
        } else if ("java.lang.Character".equals(name) || "Character".equals(name) || "char".equals(name)) {
            return "string";
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name) || "boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name) || "int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name) || "long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name) || "short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name) || "byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name) || "float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name) || "double".equals(name)) {
            return "number";
        }

        return null;
    }

    /**
     * Sanitizes the javadoc to removed invalid characters so it can be used as json description
     *
     * @param javadoc  the javadoc
     * @return the text that is valid as json
     */
    public static String sanitizeDescription(String javadoc, boolean summary) {
        if (Strings.isNullOrEmpty(javadoc)) {
            return null;
        }

        // lets just use what java accepts as identifiers
        StringBuilder sb = new StringBuilder();

        // split into lines
        String[] lines = javadoc.split("\n");

        boolean first = true;
        for (String line : lines) {
            line = line.trim();

            // terminate if we reach @param, @return or @deprecated as we only want the javadoc summary
            if (line.startsWith("@param") || line.startsWith("@return") || line.startsWith("@deprecated")) {
                break;
            }

            // skip lines that are javadoc references
            if (line.startsWith("@")) {
                continue;
            }

            // remove all XML tags
            line = line.replaceAll("<.*?>", "");

            // remove all inlined javadoc links, eg such as {@link org.apache.camel.spi.Registry}
            line = line.replaceAll("\\{\\@\\w+\\s([\\w.]+)\\}", "$1");

            // we are starting from a new line, so add a whitespace
            if (!first) {
                sb.append(' ');
            }

            // create a new line
            StringBuilder cb = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (Character.isJavaIdentifierPart(c) || VALID_CHARS.indexOf(c) != -1) {
                    cb.append(c);
                } else if (Character.isWhitespace(c)) {
                    // always use space as whitespace, also for line feeds etc
                    cb.append(' ');
                }
            }

            // append data
            String s = cb.toString().trim();
            sb.append(s);

            boolean empty = Strings.isNullOrEmpty(s);
            boolean endWithDot = s.endsWith(".");
            boolean haveText = sb.length() > 0;

            if (haveText && summary && (empty || endWithDot)) {
                // if we only want a summary, then skip at first empty line we encounter, or if the sentence ends with a dot
                break;
            }

            first = false;
        }

        // remove double whitespaces, and trim
        String s = sb.toString();
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    /**
     * Parses the json schema to split it into a list or rows, where each row contains key value pairs with the metadata
     *
     * @param group the group to parse from such as <tt>component</tt>, <tt>componentProperties</tt>, or <tt>properties</tt>.
     * @param json the json
     * @return a list of all the rows, where each row is a set of key value pairs with metadata
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> parseJsonSchema(String group, String json, boolean parseProperties) {
        List<Map<String, String>> answer = new ArrayList<>();
        if (json == null) {
            return answer;
        }

        // convert into a List<Map<String, String>> structure which is expected as output from this parser
        try {
            JsonObject output = (JsonObject) Jsoner.deserialize(json);
            for (String key : output.keySet()) {
                Map row = output.getMap(key);
                if (key.equals(group)) {
                    if (parseProperties) {
                        // flattern each entry in the row with name as they key, and its value as the content (its a map also)
                        for (Object obj : row.entrySet()) {
                            Map.Entry entry = (Map.Entry) obj;
                            Map<String, String> newRow = new LinkedHashMap();
                            newRow.put("name", entry.getKey().toString());

                            Map newData = transformMap((Map) entry.getValue());
                            newRow.putAll(newData);
                            answer.add(newRow);
                        }
                    } else {
                        // flattern each entry in the row as a list of single Map<key, value> elements
                        Map newData = transformMap(row);
                        for (Object obj : newData.entrySet()) {
                            Map.Entry entry = (Map.Entry) obj;
                            Map<String, String> newRow = new LinkedHashMap<>();
                            newRow.put(entry.getKey().toString(), entry.getValue().toString());
                            answer.add(newRow);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // wrap parsing exceptions as runtime
            throw new RuntimeException("Cannot parse json", e);
        }

        return answer;
    }

    private static Map<String, String> transformMap(Map jsonMap) {
        Map<String, String> answer = new LinkedHashMap<>();

        for (Object rowObj : jsonMap.entrySet()) {
            Map.Entry rowEntry = (Map.Entry) rowObj;
            // if its a list type then its an enum, and we need to parse it as a single line separated with comma
            // to be backwards compatible
            Object newValue = rowEntry.getValue();
            if (newValue instanceof List) {
                List list = (List) newValue;
                CollectionStringBuffer csb = new CollectionStringBuffer(",");
                for (Object line : list) {
                    csb.append(line);
                }
                newValue = csb.toString();
            }
            // ensure value is escaped
            String value = escapeJson(newValue.toString());
            answer.put(rowEntry.getKey().toString(), value);
        }

        return answer;
    }

    private static String escapeJson(String value) {
        // need to safe encode \r as \\r so its escaped
        // need to safe encode \n as \\n so its escaped
        // need to safe encode \t as \\t so its escaped
        return value
            .replaceAll("\\\\r", "\\\\\\r")
            .replaceAll("\\\\n", "\\\\\\n")
            .replaceAll("\\\\t", "\\\\\\t");
    }

    /**
     * The default value may need to be escaped to be safe for json
     */
    private static String safeDefaultValue(String value) {
        if ("\"".equals(value)) {
            return "\\\"";
        } else if ("\\".equals(value)) {
            return "\\\\";
        } else {
            return value;
        }
    }

}
