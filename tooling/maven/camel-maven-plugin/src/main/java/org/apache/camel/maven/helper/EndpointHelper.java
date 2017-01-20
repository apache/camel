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
package org.apache.camel.maven.helper;

import java.util.regex.PatternSyntaxException;

public final class EndpointHelper {

    private EndpointHelper() {
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    public static boolean matchPattern(String name, String pattern) {
        if (name == null || pattern == null) {
            return false;
        }

        if (name.equals(pattern)) {
            // exact match
            return true;
        }

        if (matchWildcard(name, pattern)) {
            return true;
        }

        if (matchRegex(name, pattern)) {
            return true;
        }

        // no match
        return false;
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    private static boolean matchWildcard(String name, String pattern) {
        // we have wildcard support in that hence you can match with: file* to match any file endpoints
        if (pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        return false;
    }

    /**
     * Matches the name with the given pattern.
     * <p/>
     * The match rules are applied in this order:
     * <ul>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param name    the name
     * @param pattern a pattern to match
     * @return <tt>true</tt> if match, <tt>false</tt> otherwise.
     */
    private static boolean matchRegex(String name, String pattern) {
        // match by regular expression
        try {
            if (name.matches(pattern)) {
                return true;
            }
        } catch (PatternSyntaxException e) {
            // ignore
        }
        return false;
    }
}
