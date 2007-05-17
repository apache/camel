/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.ResolverUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A helper class which will find all {@link RouteBuilder} instances on the classpath
 *
 * @version $Revision$
 */
public class RouteBuilderFinder implements ApplicationContextAware {
    private String[] packages = {};
    private ApplicationContext applicationContext;
    private ResolverUtil resolver = new ResolverUtil();

    public RouteBuilderFinder(ApplicationContext applicationContext, String[] packages) {
        this.applicationContext = applicationContext;
        this.packages = packages;
    }

    public RouteBuilderFinder(CamelContextFactoryBean factoryBean) {
        this.applicationContext = factoryBean.getApplicationContext();
        this.packages = factoryBean.getPackages();
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Appends all the {@link RouteBuilder} instances that can be found on the classpath
     */
    public void appendBuilders(List<RouteBuilder> list) throws IllegalAccessException, InstantiationException {
        resolver.findImplementations(RouteBuilder.class, packages);
        //resolver.findAnnotated(Endpoint.class, packages);
        Set<Class> classes = resolver.getClasses();
        for (Class aClass : classes) {
            if (shouldIgnoreBean(aClass)) {
                continue;
            }
            if (isValidClass(aClass)) {
                list.add(instantiateBuilder(aClass));
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
            Constructor[] constructors = type.getDeclaredConstructors();
            for (Constructor constructor : constructors) {
                Class[] classes = constructor.getParameterTypes();
                if (classes.length == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected RouteBuilder instantiateBuilder(Class type) throws IllegalAccessException, InstantiationException {
        // TODO we could support spring-injection for these types
        return (RouteBuilder) type.newInstance();
    }
}