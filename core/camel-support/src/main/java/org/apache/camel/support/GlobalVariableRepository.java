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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.spi.VariableRepository;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Global {@link VariableRepository} which stores variables in-memory in a {@link Map}.
 */
public class GlobalVariableRepository extends ServiceSupport implements VariableRepository {

    private final ConcurrentMap<String, Object> variables = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "global";
    }

    @Override
    public Object getVariable(String name) {
        return variables.get(name);
    }

    @Override
    public void setVariable(String name, Object value) {
        if (value != null) {
            // avoid the NullPointException
            variables.put(name, value);
        } else {
            // if the value is null, we just remove the key from the map
            variables.remove(name);
        }
    }

    @Override
    public Object removeVariable(String name) {
        return variables.remove(name);
    }
}
