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
import java.util.Objects;
import java.util.Properties;

public final class PropertiesHelper {

    private PropertiesHelper() {
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix) {
        return extractProperties(properties, optionPrefix, true);
    }

    public static Map<String, Object> extractProperties(Map<String, Object> properties, String optionPrefix, boolean remove) {
        if (properties == null) {
            return new LinkedHashMap<>(0);
        }

        return doExtractProperties(properties, optionPrefix, remove);
    }

    static Map<String, Object> doExtractProperties(Map<String, Object> properties, String optionPrefix, boolean remove) {
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

    public static Properties asProperties(String... properties) {
        if ((properties.length & 1) != 0) {
            throw new InternalError("length is odd");
        }

        Properties answer = new Properties();
        for (int i = 0; i < properties.length; i += 2) {
            answer.setProperty(
                    Objects.requireNonNull(properties[i]),
                    Objects.requireNonNull(properties[i + 1]));
        }

        return answer;
    }

    public static Properties asProperties(Map<String, Object> properties) {
        Properties answer = new Properties();
        answer.putAll(properties);

        return answer;
    }

}
