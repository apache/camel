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

package org.apache.camel.web.util;

import org.apache.camel.Predicate;

/**
 * 
 */
public class PredicateRenderer {

    public static void renderPredicate(StringBuilder buffer, Predicate predicate) {
        String pre = predicate.toString();
        if (pre.contains("(")) {
            pre = pre.replaceAll("\\(", "(\"").replaceAll("\\)", "\")");

            String rightExp = pre.substring(pre.indexOf(" ") + 1);
            if (rightExp.startsWith("==")) {
                // replace == with isEqualTo
                pre = pre.replaceFirst("\\ == ", ".isEqualTo(\"");
                pre += "\")";
            } else if (rightExp.startsWith("is not null")) {
                pre = pre.replaceFirst("\\ is not null", ".isNotNull()");
            }
            buffer.append("(").append(pre).append(")");
        } else {
            String tmp[] = pre.split("\\s+");
            buffer.append("(").append(tmp[0]).append("().").append(tmp[1]).append("(\"").append(tmp[2]).append("\"))");
        }
    }

}
