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

import org.apache.camel.Routes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ResolverUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;

/**
 * A helper class which will find all {@link RouteBuilder} instances on the classpath
 *
 * @version $Revision$
 */
public class RouteBuilderFinder {
    private static final transient Log LOG = LogFactory.getLog(RouteBuilderFinder.class);
    private final SpringCamelContext camelContext;
    private final String[] packages;
    private ResolverUtil resolver;
    private ApplicationContext applicationContext;    
    private BeanPostProcessor beanPostProcessor;

    public RouteBuilderFinder(SpringCamelContext camelContext, String[] packages, ClassLoader classLoader, BeanPostProcessor postProcessor, ResolverUtil resolverUtil) {
        this.camelContext = camelContext;
        this.applicationContext = camelContext.getApplicationContext();
        this.packages = packages;
        this.beanPostProcessor = postProcessor;
        this.resolver = resolverUtil;
        // lets add all the available class loaders just in case of weirdness
        // we could make this more strict once we've worked out all the gremlins
        // in servicemix-camel
        Set set = resolver.getClassLoaders();
        set.clear();
        set.add(classLoader);
/*
        set.add(classLoader);
        set.add(applicationContext.getClassLoader());
        set.add(getClass().getClassLoader());
*/
    }
  
    public String[] getPackages() {
        return packages;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * Appends all the {@link RouteBuilder} instances that can be found on the classpath
     */
    public void appendBuilders(List<Routes> list) throws IllegalAccessException, InstantiationException {
        resolver.findImplementations(Routes.class, packages);
        Set<Class> classes = resolver.getClasses();
        for (Class aClass : classes) {
            if (shouldIgnoreBean(aClass)) {
                continue;
            }
            if (isValidClass(aClass)) {
                Routes builder = instantiateBuilder(aClass);
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
        // TODO apply some filter?
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

    protected Routes instantiateBuilder(Class type) throws IllegalAccessException, InstantiationException {
        return (Routes) camelContext.getInjector().newInstance(type);
    }
}
