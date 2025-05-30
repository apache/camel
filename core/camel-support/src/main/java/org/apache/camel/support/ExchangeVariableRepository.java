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
package org.apache.camel.support;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.VariableRepository;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.util.StringHelper;

/**
 * {@link VariableRepository} which is local per {@link Exchange} to hold request-scoped variables.
 */
final class ExchangeVariableRepository extends AbstractVariableRepository {

    public ExchangeVariableRepository(CamelContext camelContext) {
        setCamelContext(camelContext);
        // ensure its started
        ServiceHelper.startService(this);
    }

    void copyFrom(ExchangeVariableRepository source) {
        setVariables(source.getVariables());
    }

    @Override
    public Object getVariable(String name) {
        String id = StringHelper.before(name, ":");
        if ("header".equals(id)) {
            String prefix = StringHelper.after(name, ":");
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("Variable " + name + " must have header key");
            }
            if (!prefix.contains(".")) {
                prefix = prefix + ".";
                // we want all headers for a given variable
                Map<String, Object> map = new CaseInsensitiveMap();
                for (Map.Entry<String, Object> entry : getVariables().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith(name + ".")) {
                        key = StringHelper.after(key, prefix);
                        map.put(key, entry.getValue());
                    }
                }
                return map;
            }
        }
        return super.getVariable(name);
    }

    @Override
    public Object removeVariable(String name) {
        String id = StringHelper.before(name, ":");
        if ("header".equals(id)) {
            String prefix = StringHelper.after(name, ":");
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("Variable " + name + " must have header key");
            }
            if (!prefix.contains(".")) {
                // we want to remove all headers for a given variable
                Set<String> toRemove = new HashSet<>();
                for (Map.Entry<String, Object> entry : getVariables().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith(name + ".")) {
                        toRemove.add(key);
                    }
                }
                toRemove.forEach(super::removeVariable);
                return null;
            }
        }
        return super.removeVariable(name);
    }

    @Override
    public String getId() {
        return "exchange";
    }

}
