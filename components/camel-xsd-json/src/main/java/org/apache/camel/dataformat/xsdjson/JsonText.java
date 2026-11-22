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
package org.apache.camel.dataformat.xsdjson;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.util.json.Jsoner;

/**
 * Writes the JSON tree of {@link XmlToJson} as JSON text. An xs:decimal is written as plain text (1.50, never 1.5E+0),
 * so it keeps its exact value and scale.
 */
final class JsonText {

    private JsonText() {
    }

    static String write(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, value, pretty, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, boolean pretty, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(Jsoner.escape(s)).append('"');
        } else if (value instanceof BigDecimal d) {
            sb.append(d.toPlainString());
        } else if (value instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append('"').append(d.isNaN() ? "NaN" : d > 0 ? "INF" : "-INF").append('"');
            } else {
                sb.append(d);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                newline(sb, pretty, indent + 1);
                sb.append('"').append(Jsoner.escape(String.valueOf(e.getKey()))).append('"').append(pretty ? ": " : ":");
                write(sb, e.getValue(), pretty, indent + 1);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            newline(sb, pretty, indent);
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                newline(sb, pretty, indent + 1);
                write(sb, it.next(), pretty, indent + 1);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            newline(sb, pretty, indent);
            sb.append(']');
        } else {
            sb.append('"').append(Jsoner.escape(value.toString())).append('"');
        }
    }

    private static void newline(StringBuilder sb, boolean pretty, int indent) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(indent));
        }
    }
}
