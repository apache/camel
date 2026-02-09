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

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.spi.SimpleFunction;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.SimpleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link SimpleFunctionRegistry}.
 */
public class DefaultSimpleFunctionRegistry extends ServiceSupport implements SimpleFunctionRegistry, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSimpleFunctionRegistry.class);

    private final CamelContext camelContext;
    private final Map<String, Expression> functions = new ConcurrentHashMap<>();

    public DefaultSimpleFunctionRegistry(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void addFunction(String name, Expression expression) {
        LOG.debug("Adding simple custom function: {}", name);

        String lower = name.toLowerCase(Locale.ENGLISH);
        if (SimpleUtils.getFunctions().contains(lower)) {
            throw new IllegalArgumentException("Simple already have built-in function with name: " + name);
        }
        expression.init(camelContext);
        functions.put(name, expression);
    }

    @Override
    public void addFunction(SimpleFunction function) {
        LOG.debug("Adding simple custom function: {}", function.getName());

        String lower = function.getName().toLowerCase(Locale.ENGLISH);
        if (SimpleUtils.getFunctions().contains(lower)) {
            throw new IllegalArgumentException("Simple already have built-in function with name: " + function.getName());
        }

        ExpressionAdapter adapter = new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Object body = exchange.getMessage().getBody();
                if (body == null && !function.allowNull()) {
                    return null;
                }
                try {
                    return function.apply(exchange, body);
                } catch (Exception e) {
                    throw RuntimeCamelException.wrapRuntimeException(e);
                }
            }

            @Override
            public String toString() {
                return "function(" + function.getName() + ")";
            }
        };
        adapter.init(camelContext);

        functions.put(function.getName(), adapter);
    }

    @Override
    public void removeFunction(String name) {
        functions.remove(name);
    }

    @Override
    public Expression getFunction(String name) {
        // in dev mode then always lookup function as it may be updated due to a live reload
        boolean dev = "dev".equals(camelContext.getCamelContextExtension().getProfile());
        Expression exp = dev ? null : functions.get(name);
        if (exp == null) {
            SimpleFunction sf = lookupFunction(name);
            if (sf != null) {
                removeFunction(name);
                addFunction(sf);
                exp = functions.get(name);
            }
        }
        return exp;
    }

    private SimpleFunction lookupFunction(String name) {
        // lookup if there is a function with the given name
        var custom = camelContext.getRegistry().findByType(SimpleFunction.class);
        for (SimpleFunction sf : custom) {
            if (name.equals(sf.getName())) {
                return sf;
            }
        }
        return null;
    }

    @Override
    public Set<String> getCustomFunctionNames() {
        return new TreeSet<>(functions.keySet());
    }

    @Override
    public Set<String> getCoreFunctionNames() {
        return SimpleUtils.getFunctions();
    }

    @Override
    public int customSize() {
        return functions.size();
    }

    @Override
    public int coreSize() {
        return getCoreFunctionNames().size();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        functions.clear();
    }
}
