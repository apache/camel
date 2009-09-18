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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

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

    @SuppressWarnings("unchecked")
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

    public String[] getPackages() {
        return packages;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * Appends all the {@link org.apache.camel.builder.RouteBuilder} instances that can be found on the classpath
     */
    public void appendBuilders(List<RoutesBuilder> list) throws IllegalAccessException, InstantiationException {
        Set<Class> classes = resolver.findImplementations(RoutesBuilder.class, packages);
        for (Class aClass : classes) {
            if (shouldIgnoreBean(aClass)) {
                continue;
            }
            if (isValidClass(aClass)) {
                RoutesBuilder builder = instantiateBuilder(aClass);
                if (beanPostProcessor != null) {
                    // Inject the annotated resource
                    beanPostProcessor.postProcessBeforeInitialization(builder, builder.toString());
                }
                list.add(builder);
            }
        }
    }

    public void destroy() throws Exception {
    }

    /**
     * Lets ignore beans that are not explicitly configured in the spring.xml
     */
    protected boolean shouldIgnoreBean(Class type) {
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
