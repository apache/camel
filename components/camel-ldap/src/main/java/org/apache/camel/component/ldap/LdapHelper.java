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
package org.apache.camel.component.ldap;

public final class LdapHelper {

    private LdapHelper() {
    }

    /**
     * Given an LDAP search string, returns the string with certain characters escaped according to RFC 2254 guidelines.
     * The character mapping is as follows:
     * <ul>
     * <li>* = \2a</li>
     * <li>( = \28</li>
     * <li>) = \29</li>
     * <li>\ = \5c</li>
     * <li>\0 = \00</li>
     * </ul>
     *
     * @param  filter string to escape according to RFC 2254 guidelines
     * @return        String the escaped/encoded result
     */
    public static String escapeFilter(String filter) {
        if (filter == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder(filter.length());
        for (int i = 0; i < filter.length(); i++) {
            char c = filter.charAt(i);
            switch (c) {
                case '\\':
                    buf.append("\\5c");
                    break;
                case '*':
                    buf.append("\\2a");
                    break;
                case '(':
                    buf.append("\\28");
                    break;
                case ')':
                    buf.append("\\29");
                    break;
                case '\0':
                    buf.append("\\00");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }
}
