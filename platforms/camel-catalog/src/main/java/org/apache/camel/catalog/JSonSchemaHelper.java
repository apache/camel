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

import org.json.simple.JsonObject;
import org.json.simple.Jsoner;

public final class JSonSchemaHelper {

    private JSonSchemaHelper() {
    }

    /**
     * Parses the json schema to split it into a list or rows, where each row contains key value pairs with the metadata
     *
     * @param group the group to parse from such as <tt>component</tt>, <tt>componentProperties</tt>, or <tt>properties</tt>.
     * @param json the json
     * @return a list of all the rows, where each row is a set of key value pairs with metadata
     * @throws RuntimeException is thrown if error parsing the json data
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

    public static boolean isComponentLenientProperties(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("lenientProperties")) {
                return "true".equals(row.get("lenientProperties"));
            }
        }
        return false;
    }

    public static boolean isComponentConsumerOnly(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("consumerOnly")) {
                return "true".equals(row.get("consumerOnly"));
            }
        }
        return false;
    }

    public static boolean isComponentProducerOnly(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("producerOnly")) {
                return "true".equals(row.get("producerOnly"));
            }
        }
        return false;
    }

    public static boolean isPropertyConsumerOnly(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String labels = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("label")) {
                labels = row.get("label");
            }
            if (found) {
                return labels != null && labels.contains("consumer");
            }
        }
        return false;
    }

    public static boolean isPropertyProducerOnly(List<Map<String, String>> rows, String name) {
        for (Map<String, String> row : rows) {
            String labels = null;
            boolean found = false;
            if (row.containsKey("name")) {
                found = name.equals(row.get("name"));
            }
            if (row.containsKey("label")) {
                labels = row.get("label");
            }
            if (found) {
                return labels != null && labels.contains("producer");
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
