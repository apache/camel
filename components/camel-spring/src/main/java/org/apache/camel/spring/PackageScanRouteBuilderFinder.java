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
package org.apache.camel.spring;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.PackageScanClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
 *
 * @version 
 */
public class PackageScanRouteBuilderFinder {
    private static final Logger LOG = LoggerFactory.getLogger(PackageScanRouteBuilderFinder.class);
    private final SpringCamelContext camelContext;
    private final String[] packages;
    private final PackageScanClassResolver resolver;
    private final ApplicationContext applicationContext;
    private final BeanPostProcessor beanPostProcessor;

    public PackageScanRouteBuilderFinder(SpringCamelContext camelContext, String[] packages, ClassLoader classLoader,
                                         BeanPostProcessor postProcessor, PackageScanClassResolver resolver) {
        this.camelContext = camelContext;
        this.applicationContext = camelContext.getApplicationContext();
        this.packages = packages;
        this.beanPostProcessor = postProcessor;
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
            @SuppressWarnings("unchecked")
            RoutesBuilder builder = instantiateBuilder((Class<? extends RoutesBuilder>) aClass);
            if (beanPostProcessor != null) {
                // Inject the annotated resource
                beanPostProcessor.postProcessBeforeInitialization(builder, builder.toString());
            }
            LOG.debug("Adding instantiated RouteBuilder: {}", builder);
            list.add(builder);
        }
    }

    /**
     * Lets ignore beans that are explicitly configured in the Spring XML files
     */
    protected boolean shouldIgnoreBean(Class<?> type) {
        Map<String, ?> beans = applicationContext.getBeansOfType(type, true, true);
        if (beans == null || beans.isEmpty()) {
            return false;
        }
        return true;
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

    protected RoutesBuilder instantiateBuilder(Class<? extends RoutesBuilder> type) throws IllegalAccessException, InstantiationException {
        return camelContext.getInjector().newInstance(type);
    }
}
