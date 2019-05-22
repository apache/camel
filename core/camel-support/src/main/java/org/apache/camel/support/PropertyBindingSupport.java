/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support;

import org.apache.camel.CamelContext;

import java.util.Map;

/**
 * A convenient support class for binding String valued properties to an instance which
 * uses a set of conventions:
 * <ul>
 *     <li>nested - Properties can be nested using the dot syntax (OGNL)</li>
 *     <li>reference by id - Values can refer to other beans by their id using # syntax</li>
 * </ul>
 */
public final class PropertyBindingSupport {

    private PropertyBindingSupport() {
    }

    public static boolean bindProperties(CamelContext camelContext, Object target, Map<String, Object> properties) throws Exception {
        boolean answer = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            answer &= bindProperty(camelContext, target, entry.getKey(), entry.getValue());
        }
        return answer;
    }

    public static boolean bindProperty(CamelContext camelContext, Object target, String name, Object value) throws Exception {
        return IntrospectionSupport.setProperty(camelContext, camelContext.getTypeConverter(), target, name, value, null, true, true);
    }
}
