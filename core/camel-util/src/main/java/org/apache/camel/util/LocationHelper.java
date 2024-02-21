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
package org.apache.camel.util;

public final class LocationHelper {

    private LocationHelper() {
    }

    /**
     * The location as human-readable with the given key from the properties
     *
     * @param  properties the properties
     * @param  key        the key
     * @return            the location or empty if not possible to resolve a location.
     */
    public static String locationSummary(OrderedLocationProperties properties, String key) {
        String loc = properties.getLocation(key);
        if (loc == null) {
            loc = "";
        }
        // remove scheme to make it shorter
        if (loc.contains(":")) {
            loc = StringHelper.after(loc, ":");
        }
        // strip paths so location is only the name
        loc = FileUtil.stripPath(loc);
        // clip long name
        if (loc.length() > 28) {
            int pos = loc.length() - 28;
            loc = loc.substring(pos);
        }
        // let us have human friendly locations
        if ("initial".equals(loc) || "override".equals(loc)) {
            loc = "camel-main";
        } else if ("SYS".equals(loc)) {
            loc = "JVM System Property";
        } else if ("ENV".equals(loc)) {
            loc = "OS Environment Variable";
        } else if ("arguments".equals(loc) || "CLI".equals(loc)) {
            loc = "Command Line";
        }
        loc = "[" + loc + "]";
        loc = String.format("%-30s", loc);
        return loc;
    }

}
