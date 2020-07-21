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
package org.apache.camel.tooling.util.srcgen;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Annotation {

    Class<? extends java.lang.annotation.Annotation> type;
    LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public Annotation(Class<? extends java.lang.annotation.Annotation> type) {
        this.type = type;
    }

    public Class<? extends java.lang.annotation.Annotation> getType() {
        return type;
    }
    public Annotation setType(Class<? extends java.lang.annotation.Annotation> type) {
        this.type = type;
        return this;
    }

    public Annotation setStringValue(String name, String value) {
        values.put(name, quote(value));
        return this;
    }

    public Annotation setLiteralValue(String name, String value) {
        values.put(name, value);
        return this;
    }

    public Annotation setLiteralValue(String value) {
        return setLiteralValue("value", value);
    }

    public Annotation setStringArrayValue(String name, String[] values) {
        if (values.length == 1) {
            return setStringValue(name, values[0]);
        } else {
            String value = Stream.of(values)
                    .map(Annotation::quote)
                    .collect(Collectors.joining(", ", "{", "}"));
            return setLiteralValue(value);
        }
    }
    public String getStringValue(String name) {
        String v = values.get(name);
        return v != null ? unquote(v) : null;
    }

    private static String unquote(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        } else {
            return value;
        }
    }

    public static String quote(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                if (sb == null) {
                    sb = new StringBuilder();
                    sb.append("\"");
                    sb.append("\\");
                    sb.append(c);
                }
            } else {
                if (sb != null) {
                    sb.append(c);
                }
            }
        }
        if (sb == null) {
            return "\"" + value + "\"";
        } else {
            sb.append("\"");
            return sb.toString();
        }
    }

}
