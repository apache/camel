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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PropertiesHelper {

    private PropertiesHelper() {
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix) {
        return extractProperties(properties, optionPrefix, true);
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix, boolean remove) {
        Map<String, Object> rc = new LinkedHashMap<>(properties.size());

        for (Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith(optionPrefix)) {
                Object value = properties.get(name);
                name = name.substring(optionPrefix.length());
                rc.put(name, value);

                if (remove) {
                    it.remove();
                }
            }
        }

        return rc;
    }

    @Deprecated
    public static Map<String, String> extractStringProperties(Map<String, Object> properties) {
        Map<String, String> rc = new LinkedHashMap<>(properties.size());

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue().toString();
            rc.put(name, value);
        }

        return rc;
    }

    public static boolean hasProperties(Map<String, Object> properties, String optionPrefix) {
        ObjectHelper.notNull(properties, "properties");

        if (ObjectHelper.isNotEmpty(optionPrefix)) {
            for (Object o : properties.keySet()) {
                String name = (String) o;
                if (name.startsWith(optionPrefix)) {
                    return true;
                }
            }
            // no parameters with this prefix
            return false;
        } else {
            return !properties.isEmpty();
        }
    }
    
}
