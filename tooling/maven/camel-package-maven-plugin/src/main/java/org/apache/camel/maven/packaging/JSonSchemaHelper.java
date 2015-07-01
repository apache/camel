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
package org.apache.camel.maven.packaging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

}
