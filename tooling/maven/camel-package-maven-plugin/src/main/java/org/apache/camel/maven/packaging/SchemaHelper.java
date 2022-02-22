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
package org.apache.camel.maven.packaging;

import java.util.Locale;

public final class SchemaHelper {

    private SchemaHelper() {
    }

    /**
     * Converts the string from dash format into camel case (hello-great-world -> helloGreatWorld)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String dashToCamelCase(String text) {
        if (text == null) {
            return null;
        }
        int length = text.length();
        if (length == 0) {
            return text;
        }
        if (text.indexOf('-') == -1) {
            return text;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-') {
                i++;
                sb.append(Character.toUpperCase(text.charAt(i)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Converts the string from camel case into dash format (helloGreatWorld -> hello-great-world)
     *
     * @param  text the string
     * @return      the string camel cased
     */
    public static String camelCaseToDash(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder answer = new StringBuilder();

        Character prev = null;
        Character next;
        char[] arr = text.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (i < arr.length - 1) {
                next = arr[i + 1];
            } else {
                next = null;
            }
            if (ch == '-' || ch == '_') {
                answer.append("-");
            } else if (Character.isUpperCase(ch) && prev != null && !Character.isUpperCase(prev)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else if (Character.isUpperCase(ch) && prev != null && next != null && Character.isLowerCase(next)) {
                if (prev != '-' && prev != '_') {
                    answer.append("-");
                }
                answer.append(ch);
            } else {
                answer.append(ch);
            }
            prev = ch;
        }

        return answer.toString().toLowerCase(Locale.ENGLISH);
    }

}
