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

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default configurer resolver that looks for configurer factories in
 * <b>META-INF/services/org/apache/camel/configurer/</b>.
 */
public class DefaultConfigurerResolver implements ConfigurerResolver {
    /**
     * This is a special container for the CamelContext because, with Camel 4, we split the CamelContext and the former
     * ExtendedCamelContext. This holds them in a single configuration, directing the target appropriately
     */
    public static class ContextConfigurer implements PropertyConfigurer {
        private final PropertyConfigurer contextConfigurer;
        private final PropertyConfigurer extensionConfigurer;

        public ContextConfigurer(PropertyConfigurer contextConfigurer, PropertyConfigurer extensionConfigurer) {
            this.contextConfigurer = contextConfigurer;
            this.extensionConfigurer = extensionConfigurer;
        }

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (target instanceof CamelContext contextTarget) {
                if (!contextConfigurer.configure(camelContext, contextTarget, name, value, ignoreCase)) {
                    return extensionConfigurer.configure(camelContext, contextTarget.getCamelContextExtension(), name, value,
                            ignoreCase);
                }
            }

            return false;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConfigurerResolver.class);

    protected FactoryFinder factoryFinder;

    public DefaultConfigurerResolver() {
    }

    public DefaultConfigurerResolver(FactoryFinder factoryFinder) {
        this.factoryFinder = factoryFinder;
    }

    @Override
    public PropertyConfigurer resolvePropertyConfigurer(String name, CamelContext context) {
        if (ObjectHelper.isEmpty(name)) {
            return null;
        }

        // lookup in registry first
        PropertyConfigurer configurer = context.getRegistry().lookupByNameAndType(name, PropertyConfigurer.class);
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
            // fallback special for camel context itself as we have an extended configurer
            if (name.startsWith("org.apache.camel.") && name.contains("CamelContext") && !name.contains("Extension")) {
                type = findConfigurer(CamelContext.class.getName(), context);

                if (type != null) {
                    var extensionType = findConfigurer(ExtendedCamelContext.class.getName(), context);

                    if (extensionType != null) {
                        return createPropertyConfigurerForContext(name, context, type, extensionType);
                    }
                }
                //
            } else {
                type = findConfigurer(name, context);
                if (type == null) {

                    if (type == null) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Configurer registered for scheme: " + name, e);
        }

        return createPropertyConfigurer(name, context, type);
    }

    private PropertyConfigurer createPropertyConfigurer(String name, CamelContext context, Class<?> type) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Found configurer: {} via type: {} via: {}{}", name, type.getName(), factoryFinder.getResourcePath(),
                    name);
        }

        // create the component
        if (PropertyConfigurer.class.isAssignableFrom(type)) {
            return (PropertyConfigurer) context.getInjector().newInstance(type, false);
        } else {
            throw new IllegalArgumentException(
                    "Type is not a PropertyConfigurer implementation. Found: " + type.getName());
        }
    }

    private PropertyConfigurer createPropertyConfigurerForContext(
            String name, CamelContext context, Class<?> type, Class<?> extensionType) {
        if (getLog().isDebugEnabled()) {
            getLog().debug("Found configurer: {} via type: {} via: {}{}", name, type.getName(), factoryFinder.getResourcePath(),
                    name);
        }

        var contextConfigurer = (PropertyConfigurer) context.getInjector().newInstance(type, false);
        var extensionConfigurer = (PropertyConfigurer) context.getInjector().newInstance(extensionType, false);

        // create the component
        if (PropertyConfigurer.class.isAssignableFrom(type)) {
            return new ContextConfigurer(contextConfigurer, extensionConfigurer);
        } else {
            throw new IllegalArgumentException(
                    "Type is not a PropertyConfigurer implementation. Found: " + type.getName());
        }
    }

    private Class<?> findConfigurer(String name, CamelContext context) {
        if (factoryFinder == null) {
            factoryFinder = context.getCamelContextExtension().getFactoryFinder(ConfigurerResolver.RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

    protected Logger getLog() {
        return LOG;
    }
}
