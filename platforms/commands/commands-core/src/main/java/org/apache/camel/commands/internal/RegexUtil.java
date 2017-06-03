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
package org.apache.camel.commands.internal;

/**
 * Util class.
 */
public final class RegexUtil {

    private RegexUtil() {
    }

    /**
     * convert a wild card containing * and ? to the equivalent regex
     *
     * @param wildcard wildcard string describing a file.
     * @return regex string that could be fed to Pattern.compile
     */
    public static String wildcardAsRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wildcard.length(); i++) {
            final char c = wildcard.charAt(i);
            switch (c) {
            case '*':
                sb.append(".*?");
                break;
            case '?':
                sb.append(".");
                break;
            // chars that have magic regex meaning. They need quoting to be taken literally
            case '$':
            case '(':
            case ')':
            case '+':
            case '-':
            case '.':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '{':
            case '|':
            case '}':
                sb.append('\\');
                sb.append(c);
                break;
            default:
                sb.append(c);
                break;
            }
        }
        return sb.toString();
    }

}
