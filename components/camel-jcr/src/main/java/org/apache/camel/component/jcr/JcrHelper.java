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
package org.apache.camel.component.jcr;

import java.util.HashMap;
import java.util.Map;

public final class JcrHelper {

    private JcrHelper() {
    }
    
    /**
     * Filter exchange properties to only have properties that are relevant
     * to JCR operations.
     * 
     * @param properties Exchange properties
     * @return Filtered exchange properties
     */
    public static Map<String, Object> filterJcrProperties(Map<String, Object> properties) {
        Map<String, Object> result = new HashMap<String, Object>(properties.size());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("Camel")) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
}
