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
package org.apache.camel.impl.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.StaticService;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default {@link SimpleFunctionRegistry}.
 */
public class DefaultSimpleFunctionRegistry extends ServiceSupport implements SimpleFunctionRegistry, StaticService {

    private final Map<String, Expression> functions = new ConcurrentHashMap<>();
    private final CamelContext camelContext;

    public DefaultSimpleFunctionRegistry(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void addFunction(String name, Expression expression) {
        expression.init(camelContext);
        functions.put(name, expression);
    }

    @Override
    public void removeFunction(String name) {
        functions.remove(name);
    }

    @Override
    public Expression getFunction(String name) {
        return functions.get(name);
    }

    @Override
    protected void doStop() throws Exception {
        super.doShutdown();
        functions.clear();
    }

    @Override
    public int size() {
        return functions.size();
    }
}
