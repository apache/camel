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
package org.apache.camel.cdi.xml;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances on the classpath.
 */
final class PackageScanRouteBuilderFinder {

    private final Logger logger = LoggerFactory.getLogger(PackageScanRouteBuilderFinder.class);

    private final CamelContext camelContext;

    private final String[] packages;

    private final PackageScanClassResolver resolver;

    PackageScanRouteBuilderFinder(CamelContext camelContext, String[] packages, ClassLoader classLoader, PackageScanClassResolver resolver) {
        this.camelContext = camelContext;
        this.packages = packages;
        this.resolver = resolver;
        resolver.addClassLoader(classLoader);
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} instances that can be found on the classpath
     */
    void appendBuilders(List<RoutesBuilder> list) throws IllegalAccessException, InstantiationException {
        Set<Class<?>> classes = resolver.findImplementations(RoutesBuilder.class, packages);
        for (Class<?> aClass : classes) {
            logger.trace("Found RouteBuilder class: {}", aClass);

            // certain beans should be ignored
            if (shouldIgnoreBean(aClass)) {
                logger.debug("Ignoring RouteBuilder class: {}", aClass);
                continue;
            }

            if (!isValidClass(aClass)) {
                logger.debug("Ignoring invalid RouteBuilder class: {}", aClass);
                continue;
            }

            // type is valid so create and instantiate the builder
            @SuppressWarnings("unchecked")
            RoutesBuilder builder = instantiateBuilder((Class<? extends RoutesBuilder>) aClass);

            logger.debug("Adding instantiated RouteBuilder: {}", builder);
            list.add(builder);
        }
    }

    private boolean shouldIgnoreBean(Class<?> type) {
        Map<String, ?> beans = camelContext.getRegistry().findByTypeWithName(type);
        return !(beans == null || beans.isEmpty());
    }

    private boolean isValidClass(Class<?> type) {
        return Modifier.isPublic(type.getModifiers())
            && !Modifier.isAbstract(type.getModifiers())
            && !type.isInterface();
    }

    private RoutesBuilder instantiateBuilder(Class<? extends RoutesBuilder> type) {
        return camelContext.getInjector().newInstance(type);
    }
}
