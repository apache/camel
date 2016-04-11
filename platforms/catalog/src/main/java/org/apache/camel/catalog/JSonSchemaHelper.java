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
package org.apache.camel.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JSonSchemaHelper {

    private static final Pattern PATTERN = Pattern.compile("\"(.+?)\"|\\[(.+)\\]");
    private static final String QUOT = "&quot;";

    private JSonSchemaHelper() {
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
                    if (value == null) {
                        value = matcher.group(2);
                        // its an enum so strip out " and trim spaces after comma
                        value = value.replaceAll("\"", "");
                        value = value.replaceAll(", ", ",");
                    }
                    if (value != null) {
                        value = value.trim();
                        // decode
                        value = value.replaceAll(QUOT, "\"");
                        value = decodeJson(value);
                    }
                    row.put(key, value);
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

    public static boolean isComponentLenientProperties(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("lenientProperties")) {
                return "true".equals(row.get("lenientProperties"));
            }
        }
        return false;
    }

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

    public static String getPropertyKind(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String kind = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("kind")) {
                kind = row.get("kind");
            }
            if (found) {
                return kind;
            }
        }
        return null;
    }

    public static boolean isPropertyBoolean(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String type = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("type")) {
                type = row.get("type");
            }
            if (found) {
                return "boolean".equals(type);
            }
        }
        return false;
    }

    public static boolean isPropertyInteger(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String type = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("type")) {
                type = row.get("type");
            }
            if (found) {
                return "integer".equals(type);
            }
        }
        return false;
    }

    public static boolean isPropertyNumber(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String type = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("type")) {
                type = row.get("type");
            }
            if (found) {
                return "number".equals(type);
            }
        }
        return false;
    }

    public static boolean isPropertyObject(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String type = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("type")) {
                type = row.get("type");
            }
            if (found) {
                return "object".equals(type);
            }
        }
        return false;
    }

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

    public static String stripOptionalPrefixFromName(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String optionalPrefix = null;
            boolean found = false;
            if (row.containsKey("optionalPrefix")) {
                optionalPrefix = row.get("optionalPrefix");
            }
            if (row.containsKey("name")) {
                if (optionalPrefix != null && name.startsWith(optionalPrefix)) {
                    name = name.substring(optionalPrefix.length());
                    // try again
                    return stripOptionalPrefixFromName(rows, name);
                } else {
                    found = name.equals(row.get("name"));
                }
            }
            if (found) {
                return name;
            }
        }
        return name;
    }

    public static String getPropertyEnum(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String enums = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("enum")) {
                enums = row.get("enum");
            }
            if (found) {
                return enums;
            }
        }
        return null;
    }

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

    public static String getPropertyNameFromNameWithPrefix(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String propertyName = null;
            boolean found = false;
            if (row.containsKey("name")) {
                propertyName = row.get("name");
            }
            if (row.containsKey("prefix")) {
                String preifx = row.get("prefix");
                found = name.startsWith(preifx);
            }
            if (found) {
                return propertyName;
            }
        }
        return null;
    }

    public static Map<String, String> getRow(List<Map<String, String>> rows, String key) {
        for (Map<String, String> row : rows) {
            if (key.equals(row.get("name"))) {
                return row;
            }
        }
        return null;
    }

    public static Set<String> getNames(List<Map<String, String>> rows) {
        Set<String> answer = new LinkedHashSet<String>();
        for (Map<String, String> row : rows) {
            if (row.containsKey("name")) {
                answer.add(row.get("name"));
            }
        }
        return answer;
    }

}
