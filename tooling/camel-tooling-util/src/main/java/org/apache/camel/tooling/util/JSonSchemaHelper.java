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
package org.apache.camel.tooling.util;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * A helper class for <a href="http://json-schema.org/">JSON schema</a>.
 */
public final class JSonSchemaHelper {

    private JSonSchemaHelper() {
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
                Map<?, ?> row = output.getMap(key);
                if (key.equals(group)) {
                    if (parseProperties) {
                        // flatten each entry in the row with name as they key, and its value as the content (its a map also)
                        for (Object obj : row.entrySet()) {
                            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) obj;
                            Map<String, String> newRow = new LinkedHashMap<>();
                            newRow.put("name", entry.getKey().toString());

                            Map<String, String> newData = transformMap((Map<?, ?>) entry.getValue());
                            newRow.putAll(newData);
                            answer.add(newRow);
                        }
                    } else {
                        // flattern each entry in the row as a list of single Map<key, value> elements
                        Map<?, ?> newData = transformMap(row);
                        for (Object obj : newData.entrySet()) {
                            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) obj;
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

    private static String escapeJson(String value) {
        // need to safe encode \r as \\r so its escaped
        // need to safe encode \n as \\n so its escaped
        // need to safe encode \t as \\t so its escaped
        return value
            .replace("\\r", "\\\\r")
            .replace("\\n", "\\\\n")
            .replace("\\t", "\\\\t");
    }

    private static Map<String, String> transformMap(Map<?, ?> jsonMap) {
        Map<String, String> answer = new LinkedHashMap<>();

        for (Object rowObj : jsonMap.entrySet()) {
            Map.Entry<?, ?> rowEntry = (Map.Entry<?, ?>) rowObj;
            // if its a list type then its an enum, and we need to parse it as a single line separated with comma
            // to be backwards compatible
            Object newValue = rowEntry.getValue();
            if (newValue instanceof List) {
                List<?> list = (List<?>) newValue;
                newValue = list.stream().map(Object::toString)
                        .collect(Collectors.joining(","));
            }
            // ensure value is escaped
            String value = escapeJson(newValue.toString());
            answer.put(rowEntry.getKey().toString(), value);
        }

        return answer;
    }

}
