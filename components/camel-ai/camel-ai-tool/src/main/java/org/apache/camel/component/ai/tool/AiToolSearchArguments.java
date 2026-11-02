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
package org.apache.camel.component.ai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Common argument extraction for the AI tool-search integrations. The caller supplies its existing JSON parser to
 * preserve parser-specific behavior.
 *
 * @since 4.24
 */
public final class AiToolSearchArguments {

    private AiToolSearchArguments() {
    }

    public static List<String> readTerms(
            String argumentsJson, Function<String, Map<String, Object>> argumentReader) {
        Map<String, Object> arguments = readArguments(argumentsJson, argumentReader);
        if (arguments == null || !arguments.containsKey(AiToolSearch.TERMS_ARGUMENT)) {
            return null;
        }
        Object value = arguments.get(AiToolSearch.TERMS_ARGUMENT);
        if (value instanceof List<?> list) {
            List<String> terms = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    terms.add(item.toString());
                }
            }
            return terms;
        }
        if (value instanceof String text) {
            return List.of(text);
        }
        return null;
    }

    public static String readString(
            String argumentsJson, String name, Function<String, Map<String, Object>> argumentReader) {
        Map<String, Object> arguments = readArguments(argumentsJson, argumentReader);
        if (arguments == null || arguments.get(name) == null) {
            return null;
        }
        String text = arguments.get(name).toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Map<String, Object> readArguments(
            String argumentsJson, Function<String, Map<String, Object>> argumentReader) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            return argumentReader.apply(argumentsJson);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
