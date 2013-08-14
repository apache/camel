/**
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
package org.apache.camel.impl;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.util.CamelContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default implementation of {@link ComponentResolver} which tries to find
 * components by using the URI scheme prefix and searching for a file of the URI
 * scheme name in the <b>META-INF/services/org/apache/camel/component/</b>
 * directory on the classpath.
 *
 * @version 
 */
public class DefaultComponentResolver implements ComponentResolver {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/component/";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultComponentResolver.class);
    private FactoryFinder factoryFinder;

    public Component resolveComponent(String name, CamelContext context) {
        // lookup in registry first
        Object bean = null;
        try {
            bean = context.getRegistry().lookupByName(name);
            getLog().debug("Found component: {} in registry: {}", name, bean);
        } catch (Exception e) {
            getLog().debug("Ignored error looking up bean: " + name, e);
        }
        if (bean != null) {
            if (bean instanceof Component) {
                return (Component) bean;
            } else {
                // lets use Camel's type conversion mechanism to convert things like CamelContext
                // and other types into a valid Component
                Component component = CamelContextHelper.convertTo(context, Component.class, bean);
                if (component != null) {
                    return component;
                }
            }
            // we do not throw the exception here and try to auto create a component
        }

        // not in registry then use component factory
        Class<?> type;
        try {
            type = findComponent(name, context);
            if (type == null) {
                // not found
                return null;
            }
        } catch (NoFactoryAvailableException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no Component registered for scheme: " + name, e);
        }

        if (getLog().isDebugEnabled()) {
            getLog().debug("Found component: {} via type: {} via: {}{}", new Object[]{name, type.getName(), factoryFinder.getResourcePath(), name});
        }

        // create the component
        if (Component.class.isAssignableFrom(type)) {
            return (Component) context.getInjector().newInstance(type);
        } else {
            throw new IllegalArgumentException("Type is not a Component implementation. Found: " + type.getName());
        }
    }

    private Class<?> findComponent(String name, CamelContext context) throws ClassNotFoundException, IOException {
        if (factoryFinder == null) {
            factoryFinder = context.getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name);
    }

    protected Logger getLog() {
        return LOG;
    }

}
