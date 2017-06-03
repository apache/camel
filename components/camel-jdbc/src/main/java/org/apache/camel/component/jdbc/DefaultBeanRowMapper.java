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
package org.apache.camel.component.jdbc;

/**
 * The default {@link BeanRowMapper} will map row names to lower case names,
 * but use a single upper case letter after underscores or dashes (which is skipped).
 * <p/>
 * For example <tt>CUST_ID</tt> is mapped as <tt>custId</tt>.
 */
public class DefaultBeanRowMapper implements BeanRowMapper {

    @Override
    public String map(String row, Object value) {
        // convert to lover case, and underscore as new upper case name;
        return mapRowName(row);
    }

    protected String mapRowName(String row) {
        StringBuilder sb = new StringBuilder();
        boolean toUpper = false;
        for (char ch : row.toCharArray()) {
            if (ch == '_' || ch == '-') {
                toUpper = true;
                continue;
            }
            if (toUpper) {
                char upper = Character.toUpperCase(ch);
                sb.append(upper);
                // reset flag
                toUpper = false;
            } else {
                char lower = Character.toLowerCase(ch);
                sb.append(lower);
            }
        }
        return sb.toString();
    }

}
