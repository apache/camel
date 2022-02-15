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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.PropertiesFunctionFactory;
import org.apache.camel.support.ResolverHelper;

/**
 * Resolver for built-in and custom {@link PropertiesFunction}.
 */
public final class PropertiesFunctionResolver implements CamelContextAware {

    private CamelContext camelContext;
    private final Map<String, PropertiesFunction> functions = new LinkedHashMap<>();

    public PropertiesFunctionResolver() {
        // include out of the box functions
        addPropertiesFunction(new EnvPropertiesFunction());
        addPropertiesFunction(new SysPropertiesFunction());
        addPropertiesFunction(new ServicePropertiesFunction());
        addPropertiesFunction(new ServiceHostPropertiesFunction());
        addPropertiesFunction(new ServicePortPropertiesFunction());
        // TODO: Move AWSSecretsManagerPropertiesFunction to camel-aws-secrets-manager
        addPropertiesFunction(new AWSSecretsManagerPropertiesFunction());
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Registers the {@link PropertiesFunction} as a function to this component.
     */
    public void addPropertiesFunction(PropertiesFunction function) {
        this.functions.put(function.getName(), function);
    }

    /**
     * Gets the functions registered in this properties component.
     */
    public Map<String, PropertiesFunction> getFunctions() {
        return functions;
    }

    /**
     * Is there a {@link PropertiesFunction} with the given name?
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public PropertiesFunction resolvePropertiesFunction(String name) {
        PropertiesFunction answer = functions.get(name);
        if (answer == null) {
            // it may be a custom function from a 3rd party JAR so use factory finder
            ExtendedCamelContext ecc = camelContext.adapt(ExtendedCamelContext.class);
            FactoryFinder finder = ecc.getBootstrapFactoryFinder(PropertiesFunctionFactory.FACTORY);

            PropertiesFunctionFactory factory
                    = ResolverHelper.resolveService(ecc, finder, name, PropertiesFunctionFactory.class).orElse(null);
            if (factory != null) {
                answer = factory.createPropertiesFunction();
                if (answer != null) {
                    functions.put(name, answer);
                }
            }
        }
        return answer;
    }

}
