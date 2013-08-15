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
package org.apache.camel.blueprint;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanClassResolver;
import org.osgi.service.blueprint.container.BlueprintContainer;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
 *
 * @version 
 */
public class PackageScanRouteBuilderFinder {
    private static final Logger LOG = LoggerFactory.getLogger(PackageScanRouteBuilderFinder.class);
    private final BlueprintCamelContext camelContext;
    private final String[] packages;
    private final PackageScanClassResolver resolver;
    private final BlueprintContainer blueprintContainer;

    public PackageScanRouteBuilderFinder(BlueprintCamelContext camelContext, String[] packages, ClassLoader classLoader,
                                         PackageScanClassResolver resolver) {
        this.camelContext = camelContext;
        this.blueprintContainer = camelContext.getBlueprintContainer();
        this.packages = packages;
        this.resolver = resolver;
        // add our provided loader as well
        resolver.addClassLoader(classLoader);
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} instances that can be found on the classpath
     */
    public void appendBuilders(List<RoutesBuilder> list) throws IllegalAccessException, InstantiationException {
        Set<Class<?>> classes = resolver.findImplementations(RoutesBuilder.class, packages);
        for (Class<?> aClass : classes) {
            LOG.trace("Found RouteBuilder class: {}", aClass);

            // certain beans should be ignored
            if (shouldIgnoreBean(aClass)) {
                LOG.debug("Ignoring RouteBuilder class: {}", aClass);
                continue;
            }

            if (!isValidClass(aClass)) {
                LOG.debug("Ignoring invalid RouteBuilder class: {}", aClass);
                continue;
            }

            // type is valid so create and instantiate the builder
            RoutesBuilder builder = instantiateBuilder(aClass);
            LOG.debug("Adding instantiated RouteBuilder: {}", builder);
            list.add(builder);
        }
    }

    /**
     * Allows for ignoring beans that are explicitly configured in the Spring XML files
     */
    protected boolean shouldIgnoreBean(Class<?> type) {
        for (Object metadataObject : blueprintContainer.getMetadata(BeanMetadata.class)) {
            BeanMetadata metadata = (BeanMetadata) metadataObject;
            if (BeanMetadata.SCOPE_SINGLETON.equals(metadata.getScope())) {
                Object bean = blueprintContainer.getComponentInstance(metadata.getId());
                if (type.isInstance(bean)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns <tt>true</tt>if the class is a public, non-abstract class
     */
    protected boolean isValidClass(Class<?> type) {
        // should skip non public classes
        if (!Modifier.isPublic(type.getModifiers())) {
            return false;
        }

        if (!Modifier.isAbstract(type.getModifiers()) && !type.isInterface()) {
            return true;
        }
        return false;
    }

    protected RoutesBuilder instantiateBuilder(Class<?> type) throws IllegalAccessException, InstantiationException {
        return (RoutesBuilder) camelContext.getInjector().newInstance(type);
    }
}
