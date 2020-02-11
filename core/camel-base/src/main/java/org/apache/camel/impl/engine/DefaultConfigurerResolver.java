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

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default configurer resolver that looks for configurer factories in <b>META-INF/services/org/apache/camel/configurer/</b>.
 */
public class DefaultConfigurerResolver implements ConfigurerResolver {
    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/configurer/";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConfigurerResolver.class);

    protected FactoryFinder factoryFinder;

    @Override
    public GeneratedPropertyConfigurer resolvePropertyConfigurer(String name, CamelContext context) {
        // lookup in registry first
        GeneratedPropertyConfigurer configurer = context.getRegistry().lookupByNameAndType(name, GeneratedPropertyConfigurer.class);
        if (configurer != null) {
            return configurer;
        }

        // clip -configurer from the name as that is not the name in META-INF
        if (name.endsWith("-configurer")) {
            name = name.substring(0, name.length() - 11);
        }

        // not in registry then use configurer factory
        Class<?> type;
        try {
            type = findConfigurer(name, context);
            if (type == null) {
                // not found
                return null;
            }
        } catch (NoFactoryAvailableException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Configurer registered for scheme: " + name, e);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Found configurer: {} via type: {} via: {}{}", name, type.getName(), factoryFinder.getResourcePath(), name);
        }

        // create the component
        if (GeneratedPropertyConfigurer.class.isAssignableFrom(type)) {
            return (GeneratedPropertyConfigurer) context.getInjector().newInstance(type, false);
        } else {
            throw new IllegalArgumentException("Type is not a GeneratedPropertyConfigurer implementation. Found: " + type.getName());
        }
    }

    private Class<?> findConfigurer(String name, CamelContext context) throws IOException {
        if (factoryFinder == null) {
            factoryFinder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

    protected Logger getLog() {
        return LOG;
    }
}
