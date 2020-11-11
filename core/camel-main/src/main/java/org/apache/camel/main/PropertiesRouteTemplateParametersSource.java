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
package org.apache.camel.main;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.RouteTemplateParameterSource;

public class PropertiesRouteTemplateParametersSource implements RouteTemplateParameterSource {

    private final Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();

    @Override
    public Map<String, Object> parameters(String routeId) {
        // return a copy
        return new HashMap<>(parameters.get(routeId));
    }

    @Override
    public Set<String> routeIds() {
        return parameters.keySet();
    }

    public void addParameter(String routeId, String name, Object value) {
        Map<String, Object> map = parameters.computeIfAbsent(routeId, k -> new HashMap<>());
        map.put(name, value);
    }

}
