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
package org.apache.camel.util;

/**
 * Helper methods for working with Strings. 
 */
public final class StringHelper {

    /**
     * Constructor of utility class should be private.
     */
    private StringHelper() {
    }
    
    /**
     * Ensures that <code>s</code> is friendly for a URL or file system.
     * 
     * @param s String to be sanitized.
     * @return sanitized version of <code>s</code>.
     * @throws NullPointerException if <code>s</code> is <code>null</code>.
     */
    public static String sanitize(String s) {
        return s
            .replace(':', '-')
            .replace('_', '-')
            .replace('.', '-')
            .replace('/', '-')
            .replace('\\', '-');
    }

    /**
     * Counts the number of times the given char is in the string
     *
     * @param s  the string
     * @param ch the char
     * @return number of times char is located in the string
     */
    public static int countChar(String s, char ch) {
        if (ObjectHelper.isEmpty(s)) {
            return 0;
        }

        int matches = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ch == c) {
                matches++;
            }
        }

        return matches;
    }

    public static String removeQuotes(String s) {
        if (ObjectHelper.isEmpty(s)) {
            return s;
        }

        s = s.replaceAll("'", "");
        s = s.replaceAll("\"", "");
        return s;
    }
    
}
