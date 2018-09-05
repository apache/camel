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
package org.apache.camel.component.aws.xray.json;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;

public final class JsonParser {

    private JsonParser() {

    }

    public static JsonStructure parse(final String jsonString) {
        String json = jsonString.replaceAll("\n", "");

        Stack<JsonStructure> stack = new Stack<>();

        JsonStructure ret = null;
        List<String> doNotIncludeSymbols = Arrays.asList(",", ":", "\"");
        StringBuilder curToken = new StringBuilder();
        String keyName = null;
        boolean inWord =  false;
        for (char c : json.toCharArray()) {
            // CHECKSTYLE:OFF
            // fallthrough is intended here and as this is only a helper class for tests (as the previously used
            // org.json classes are incompatible with Apache 2.0 license) formatting rules shouldn't be that strict IMO
            // Note that the fall-through was the only rant checkstyle generated, so everything else should follow these
            // guidelines
            switch (c) {
            case '{':
                if (!inWord) {
                    // JsonObject begin
                    JsonObject newNode = new JsonObject();
                    addJson(newNode, keyName, stack);
                    keyName = null;
                    stack.push(newNode);
                    break;
                }
            case '}':
                if (!inWord) {
                    // JsonObject end
                    if (!stack.isEmpty()) {
                        ret = stack.pop();
                    }
                    if (keyName != null) {
                        if (ret instanceof JsonObject) {
                            ((JsonObject) ret).addElement(sanitizeKey(keyName), sanitizeData(curToken.toString()));
                            keyName = null;
                            curToken.delete(0, curToken.length());
                        }
                    }
                    break;
                }
            case '[':
                if (!inWord) {
                    // JsonArray start
                    JsonArray newArray = new JsonArray();
                    addJson(newArray, keyName, stack);
                    keyName = null;
                    stack.push(newArray);
                    break;
                }
            case ']':
                if (!inWord) {
                    // JsonArray end
                    if (!stack.isEmpty()) {
                        ret = stack.pop();
                    }
                    break;
                }
            case ':':
                if (!inWord) {
                    // Element start
                    keyName = curToken.toString();
                    curToken.delete(0, curToken.length());
                    break;
                }
            case ',':
                if (!inWord) {
                    // Element separator
                    if (keyName != null) {
                        JsonObject jsonObj = (JsonObject) stack.peek();
                        jsonObj.addElement(sanitizeKey(keyName), sanitizeData(curToken.toString()));
                    }
                    curToken.delete(0, curToken.length());
                    keyName = null;
                    break;
                }
            default:
                if (('"' == c && curToken.length() == 0)
                        || ('"' == c && curToken.length() > 0 && curToken.charAt(curToken.length() - 1) != '\\')) {
                    inWord = !inWord;
                }
                if (!inWord && !doNotIncludeSymbols.contains("" + c)) {
                    curToken.append(c);
                } else if ('"' != c || (curToken.length() > 0 && curToken.charAt(curToken.length() - 1) == '\\')) {
                    curToken.append(c);
                }
            }
            // CHECKSTYLE:ON
        }
        return ret;
    }

    private static void addJson(JsonStructure element, String key, Stack<JsonStructure> stack) {
        if (!stack.isEmpty()) {
            JsonStructure json = stack.peek();
            if (json instanceof JsonObject && key != null) {
                ((JsonObject)json).addElement(sanitizeKey(key), element);
            } else if (json instanceof JsonArray) {
                ((JsonArray)json).add(element);
            }
        }
    }

    private static String sanitizeKey(String key) {
        return key.trim();
    }

    private static Object sanitizeData(String data) {
        data = data.trim();
        if (data.toLowerCase().equals("true") || data.toLowerCase().equals("false")) {
            return Boolean.valueOf(data);
        }
        if (data.contains(".") && StringUtils.countMatches(data, ".") == 1 && data.matches("[0-9\\.]+")) {
            return Double.valueOf(data);
        } else if (data.matches("[0-9]+")) {
            try {
                return Integer.valueOf(data);
            } catch (NumberFormatException nfEx) {
                return Long.valueOf(data);
            }
        }
        return data;
    }
}
