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
package org.apache.camel.util;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
public final class JsonSchemaHelper {

    // 0 = text, 1 = enum, 2 = boolean, 3 = integer or number
    private static final Pattern PATTERN = Pattern.compile("\"(.+?)\"|\\[(.+)\\]|(true|false)|(-?\\d+\\.?\\d*)");
    private static final String QUOT = "&quot;";

    private JsonSchemaHelper() {
    }

    /**
     * Gets the JSon schema type.
     *
     * @param type the java type
     * @return the json schema type, is never null, but returns <tt>object</tt> as the generic type
     */
    public static String getType(Class<?> type) {
        if (type.isEnum()) {
            return "enum";
        } else if (type.isArray()) {
            return "array";
        }
        if (type.isAssignableFrom(URI.class) || type.isAssignableFrom(URL.class)) {
            return "string";
        }

        String primitive = getPrimitiveType(type.getCanonicalName());
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
     * Parses the json schema to split it into a list or rows, where each row contains key value pairs with the metadata
     *
     * @param group the group to parse from such as <tt>component</tt>, <tt>componentProperties</tt>, or <tt>properties</tt>.
     * @param json the json
     * @return a list of all the rows, where each row is a set of key value pairs with metadata
     */
    public static List<Map<String, String>> parseJsonSchema(String group, String json, boolean parseProperties) {
        List<Map<String, String>> answer = new ArrayList<Map<String, String>>();
        if (json == null) {
            return answer;
        }

        boolean found = false;

        // parse line by line
        String[] lines = json.split("\n");
        for (String line : lines) {
            // we need to find the group first
            if (!found) {
                String s = line.trim();
                found = s.startsWith("\"" + group + "\":") && s.endsWith("{");
                continue;
            }

            // we should stop when we end the group
            if (line.equals("  },") || line.equals("  }")) {
                break;
            }

            // need to safe encode \" so we can parse the line
            line = line.replaceAll("\"\\\\\"\"", '"' + QUOT + '"');

            Map<String, String> row = new LinkedHashMap<String, String>();
            Matcher matcher = PATTERN.matcher(line);

            String key;
            if (parseProperties) {
                // when parsing properties the first key is given as name, so the first parsed token is the value of the name
                key = "name";
            } else {
                key = null;
            }
            while (matcher.find()) {
                if (key == null) {
                    key = matcher.group(1);
                } else {
                    String value = matcher.group(1);
                    if (value != null) {
                        // its text based
                        value = value.trim();
                        // decode
                        value = value.replaceAll(QUOT, "\"");
                        value = decodeJson(value);
                    }
                    if (value == null) {
                        // not text then its maybe an enum?
                        value = matcher.group(2);
                        if (value != null) {
                            // its an enum so strip out " and trim spaces after comma
                            value = value.replaceAll("\"", "");
                            value = value.replaceAll(", ", ",");
                            value = value.trim();
                        }
                    }
                    if (value == null) {
                        // not text then its maybe a boolean?
                        value = matcher.group(3);
                    }
                    if (value == null) {
                        // not text then its maybe a integer?
                        value = matcher.group(4);
                    }
                    if (value != null) {
                        row.put(key, value);
                    }
                    // reset
                    key = null;
                }
            }
            if (!row.isEmpty()) {
                answer.add(row);
            }
        }

        return answer;
    }

    private static String decodeJson(String value) {
        // json encodes a \ as \\ so we need to decode from \\ back to \
        if ("\\\\".equals(value)) {
            value = "\\";
        }
        return value;
    }

    /**
     * Is the property required
     *
     * @param rows the rows of properties
     * @param name name of the property
     * @return <tt>true</tt> if required, or <tt>false</tt> if not
     */
    public static boolean isPropertyRequired(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            boolean required = false;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("required")) {
                required = "true".equals(row.get("required"));
            }
            if (found) {
                return required;
            }
        }
        return false;
    }

    /**
     * Gets the default value of the property
     *
     * @param rows the rows of properties
     * @param name name of the property
     * @return the default value or <tt>null</tt> if no default value exists
     */
    public static String getPropertyDefaultValue(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String defaultValue = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("defaultValue")) {
                defaultValue = row.get("defaultValue");
            }
            if (found) {
                return defaultValue;
            }
        }
        return null;
    }

    /**
     * Is the property multi valued
     *
     * @param rows the rows of properties
     * @param name name of the property
     * @return <tt>true</tt> if multi valued, or <tt>false</tt> if not
     */
    public static boolean isPropertyMultiValue(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            boolean multiValue = false;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("multiValue")) {
                multiValue = "true".equals(row.get("multiValue"));
            }
            if (found) {
                return multiValue;
            }
        }
        return false;
    }

    /**
     * Gets the prefix value of the property
     *
     * @param rows the rows of properties
     * @param name name of the property
     * @return the prefix value or <tt>null</tt> if no prefix value exists
     */
    public static String getPropertyPrefix(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String prefix = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("prefix")) {
                prefix = row.get("prefix");
            }
            if (found) {
                return prefix;
            }
        }
        return null;
    }

}
