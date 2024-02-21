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
package org.apache.camel.component.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.StaticService;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link PropertiesFunctionResolver}.
 */
public class DefaultPropertiesFunctionResolver extends ServiceSupport
        implements PropertiesFunctionResolver, CamelContextAware, NonManagedService, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPropertiesFunctionResolver.class);

    private CamelContext camelContext;
    private FactoryFinder factoryFinder;
    private final Map<String, PropertiesFunction> functions = new LinkedHashMap<>();

    public DefaultPropertiesFunctionResolver() {
        // include out of the box functions
        addPropertiesFunction(new EnvPropertiesFunction());
        addPropertiesFunction(new SysPropertiesFunction());
        addPropertiesFunction(new ServicePropertiesFunction());
        addPropertiesFunction(new ServiceHostPropertiesFunction());
        addPropertiesFunction(new ServicePortPropertiesFunction());
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void addPropertiesFunction(PropertiesFunction function) {
        this.functions.put(function.getName(), function);
    }

    @Override
    public Map<String, PropertiesFunction> getFunctions() {
        return functions;
    }

    @Override
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    @Override
    public PropertiesFunction resolvePropertiesFunction(String name) {
        PropertiesFunction answer = functions.get(name);
        if (answer == null) {
            answer = resolve(camelContext, name);
            if (answer != null) {
                functions.put(name, answer);
            }
        }
        return answer;
    }

    private PropertiesFunction resolve(CamelContext context, String name) {
        // use factory finder to find a custom implementations
        Class<?> type = null;
        try {
            type = findFactory(name, context);
        } catch (Exception e) {
            // ignore
        }

        if (type != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found PropertiesFunction: {} via: {}{}", type.getName(), factoryFinder.getResourcePath(), name);
            }
            if (PropertiesFunction.class.isAssignableFrom(type)) {
                PropertiesFunction answer = (PropertiesFunction) context.getInjector().newInstance(type, false);
                CamelContextAware.trySetCamelContext(answer, camelContext);
                ServiceHelper.startService(answer);
                return answer;
            } else {
                throw new IllegalArgumentException("Type is not a PropertiesFunction implementation. Found: " + type.getName());
            }
        }

        return null;
    }

    private Class<?> findFactory(String name, CamelContext context) {
        if (factoryFinder == null) {
            factoryFinder = context.getCamelContextExtension().getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(functions.values());
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(functions.values());
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(functions.values());
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(functions.values());
    }
}
