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
package org.apache.camel.dsl.jbang.core.commands.process;

/**
 * The values of a listing in JSON mode: a number is a JSON number, also when the listing keeps it as text for its
 * table, and a value that is not there is <tt>null</tt> (the table shows it as {@code -} or blank).
 */
final class JsonValues {

    private JsonValues() {
    }

    /**
     * A pid, which the listings keep as text.
     */
    static Long pid(String pid) {
        return number(pid);
    }

    /**
     * A whole number kept as text, such as {@code 42} or {@code +5}, or <tt>null</tt> when there is none.
     */
    static Long number(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim().startsWith("+") ? value.trim().substring(1) : value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A decimal number kept as text, such as {@code 0.25}, or <tt>null</tt> when there is none.
     */
    static Double decimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A human readable duration or time since, such as {@code 5m3s}, or <tt>null</tt> when there is none.
     */
    static String text(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? null : value;
    }
}
