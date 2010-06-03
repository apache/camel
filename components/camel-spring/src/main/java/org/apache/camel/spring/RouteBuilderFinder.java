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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * A helper class which will find all {@link org.apache.camel.builder.RouteBuilder} instances on the classpath
 *
 * @version $Revision$
 */
public class RouteBuilderFinder {
    private static final transient Log LOG = LogFactory.getLog(RouteBuilderFinder.class);
    private final SpringCamelContext camelContext;
    private final String[] packages;
    private PackageScanClassResolver resolver;
    private ApplicationContext applicationContext;    
    private BeanPostProcessor beanPostProcessor;

    public RouteBuilderFinder(SpringCamelContext camelContext, String[] packages, ClassLoader classLoader,
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
        for (Class aClass : classes) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found RouteBuilder class: " + aClass);
            }

            // check whether the class has already been instantiate by Spring as it was @Component annotated
            // and its already enlisted in the Spring registry
            RouteBuilder existing = isRouteBuilderAlreadyRegisteredByComponentAnnotation(aClass);
            if (existing != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding existing @Component annotated RouteBuilder: " + aClass);
                }
                list.add(existing);
                continue;
            }
            // certain beans should be ignored
            if (shouldIgnoreBean(aClass)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring RouteBuilder class: " + aClass);
                }
                continue;
            }

            if (!isValidClass(aClass)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Ignoring invalid RouteBuilder class: " + aClass);
                }
                continue;
            }

            // type is valid so create and instantiate the builder
            RoutesBuilder builder = instantiateBuilder(aClass);
            if (beanPostProcessor != null) {
                // Inject the annotated resource
                beanPostProcessor.postProcessBeforeInitialization(builder, builder.toString());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding instantiated RouteBuilder: " + builder);
            }
            list.add(builder);
        }
    }

    /**
     * Lookup if the given class has already been enlisted in Spring registry since the class
     * has been @Component annotated.
     * 
     * @param type  the class type
     * @return the existing route builder instance, or <tt>null</tt> if none existed
     */
    protected RouteBuilder isRouteBuilderAlreadyRegisteredByComponentAnnotation(Class<?> type) {
        Component ann = type.getAnnotation(Component.class);
        if (ann != null) {
            String id = ann.value();
            if (ObjectHelper.isEmpty(id)) {
                // no explicit id set, so Spring auto assigns the id, so lets try to find that id then
                String[] names = applicationContext.getBeanNamesForType(type, true, true);
                if (names != null && names.length == 1) {
                    id = names[0];
                }
            }
            if (ObjectHelper.isNotEmpty(id)) {
                boolean match = applicationContext.isTypeMatch(id, type);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Is there already a RouteBuilder registering in Spring with id " + id + ": " + match);
                }
                if (match) {
                    Object existing = applicationContext.getBean(id, type);
                    if (existing instanceof RouteBuilder) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Found existing @Component annotated RouteBuilder with id " + id);
                        }
                        return RouteBuilder.class.cast(existing);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Lets ignore beans that are explicitly configured in the Spring XML files
     */
    protected boolean shouldIgnoreBean(Class<?> type) {
        Map beans = applicationContext.getBeansOfType(type, true, true);
        if (beans == null || beans.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the object is non-abstract and supports a zero argument constructor
     */
    protected boolean isValidClass(Class type) {
        if (!Modifier.isAbstract(type.getModifiers()) && !type.isInterface()) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    protected RoutesBuilder instantiateBuilder(Class type) throws IllegalAccessException, InstantiationException {
        return (RoutesBuilder) camelContext.getInjector().newInstance(type);
    }
}
