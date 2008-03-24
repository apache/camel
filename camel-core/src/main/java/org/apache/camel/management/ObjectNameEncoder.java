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
package org.apache.camel.management;

/**
 * Utility class providing RFC 1738 style encoding for ObjectName values.
 * (see section 2.2).
 *
 * Key Property Values in ObjectName(s) may not contain one of :",=*?
 * (see jmx_1.2_spec, Chapter 6)
 *
 * @author hzbarcea
 *
 */
public final class ObjectNameEncoder {

    private ObjectNameEncoder() {
        // Complete (utility class should not have instances)
    }

    public static String encode(String on) {
        return encode(on, false);
    }

    public static String encode(String on, boolean ignoreWildcards) {
        on = on.replace("%", "%25");    // must be first
        on = on.replace(":", "%3a");
        on = on.replace("\"", "%22");
        on = on.replace(",", "%2c");
        on = on.replace("=", "%3d");
        if (!ignoreWildcards) {
            on = on.replace("*", "%2a");
            on = on.replace("?", "%3f");
        }
        return on;
    }

    public static String decode(String on) {
        on = on.replace("%25", "%");
        on = on.replace("%3a", ":");
        on = on.replace("%22", "\"");
        on = on.replace("%2c", ",");
        on = on.replace("%3d", "=");
        on = on.replace("%2a", "*");
        on = on.replace("%3f", "?");
        return on;
    }
}
