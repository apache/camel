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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers that turn raw YAML schema / properties validation messages into the inline error map shown in the editor
 * gutter.
 */
final class SourceValidationSupport {

    private SourceValidationSupport() {
    }

    /** Package-private (rather than private) so tests can exercise it directly. */
    static boolean isEmptyValueLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed.isEmpty();
        }
        return trimmed.substring(colon + 1).trim().isEmpty();
    }

    static Map<Integer, String> buildInlineErrors(List<String> errors, String content) {
        Map<Integer, String> result = new java.util.LinkedHashMap<>();
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile("^Line (\\d+): (.*)");
        for (String error : errors) {
            java.util.regex.Matcher m = linePattern.matcher(error);
            if (m.matches()) {
                int lineNum = Integer.parseInt(m.group(1)) - 1;
                result.putIfAbsent(lineNum, m.group(2));
            }
        }
        return result;
    }

    static String cleanValidationMessage(String msg) {
        // strip FQCN prefix like "com.fasterxml...MarkedYAMLException: "
        int colonSpace = msg.indexOf(": ");
        if (colonSpace > 0) {
            String prefix = msg.substring(0, colonSpace);
            if (prefix.contains(".") && !prefix.contains(" ")) {
                msg = msg.substring(colonSpace + 2);
            }
        }
        // strip "at [Source: (StringReader); line: N, column: N]"
        int atSource = msg.indexOf("at [Source:");
        if (atSource > 0) {
            msg = msg.substring(0, atSource).stripTrailing();
        }
        // strip "in 'reader', " prefix from snakeyaml messages
        msg = msg.replace("in 'reader', ", "");
        return msg;
    }

    /** Validates a properties file line by line with the given validator, reporting {@code Line n: message}. */
    static List<String> validatePropertiesLines(String content, java.util.function.Function<String, String> lineValidator) {
        List<String> msgs = new java.util.ArrayList<>();
        if (content == null) {
            return msgs;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || !line.contains("=")) {
                continue;
            }
            String error = lineValidator.apply(lines[i]);
            if (error != null) {
                msgs.add("Line " + (i + 1) + ": " + error);
            }
        }
        return msgs;
    }

    /** Formats YAML DSL schema errors the way the editor shows them: {@code node: message}. */
    static List<String> formatSchemaErrors(List<com.networknt.schema.Error> errors) {
        List<String> msgs = new java.util.ArrayList<>();
        if (errors == null) {
            return msgs;
        }
        for (com.networknt.schema.Error error : errors) {
            String msg = error.getMessage();
            if (msg == null) {
                continue;
            }
            String loc = error.getInstanceLocation() != null ? error.getInstanceLocation().toString() : null;
            String node = extractNodeName(loc);
            String clean = cleanValidationMessage(msg);
            msgs.add(node != null ? node + ": " + clean : clean);
        }
        return msgs;
    }

    static String extractNodeName(String instanceLocation) {
        if (instanceLocation == null || instanceLocation.isEmpty()) {
            return null;
        }
        int slash = instanceLocation.lastIndexOf('/');
        String last = slash >= 0 ? instanceLocation.substring(slash + 1) : instanceLocation;
        if (last.isEmpty()) {
            return null;
        }
        // skip pure numeric segments (array indices)
        try {
            Integer.parseInt(last);
            return null;
        } catch (NumberFormatException e) {
            return last;
        }
    }
}
