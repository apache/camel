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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.OrderedComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To configure routes using {@link RoutesCollector} which collects the routes from various sources.
 */
public class RoutesConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(RoutesConfigurer.class);

    private RoutesCollector routesCollector;
    private CamelBeanPostProcessor beanPostProcessor;
    private List<RoutesBuilder> routesBuilders;
    private String packageScanRouteBuilders;
    private String routesBuilderClasses;
    private String javaRoutesExcludePattern;
    private String javaRoutesIncludePattern;
    private String routesExcludePattern;
    private String routesIncludePattern;

    public List<RoutesBuilder> getRoutesBuilders() {
        return routesBuilders;
    }

    public void setRoutesBuilders(List<RoutesBuilder> routesBuilders) {
        this.routesBuilders = routesBuilders;
    }

    public String getPackageScanRouteBuilders() {
        return packageScanRouteBuilders;
    }

    public void setPackageScanRouteBuilders(String packageScanRouteBuilders) {
        this.packageScanRouteBuilders = packageScanRouteBuilders;
    }

    public String getRoutesBuilderClasses() {
        return routesBuilderClasses;
    }

    public void setRoutesBuilderClasses(String routesBuilderClasses) {
        this.routesBuilderClasses = routesBuilderClasses;
    }

    public String getJavaRoutesExcludePattern() {
        return javaRoutesExcludePattern;
    }

    public void setJavaRoutesExcludePattern(String javaRoutesExcludePattern) {
        this.javaRoutesExcludePattern = javaRoutesExcludePattern;
    }

    public String getJavaRoutesIncludePattern() {
        return javaRoutesIncludePattern;
    }

    public void setJavaRoutesIncludePattern(String javaRoutesIncludePattern) {
        this.javaRoutesIncludePattern = javaRoutesIncludePattern;
    }

    public String getRoutesExcludePattern() {
        return routesExcludePattern;
    }

    public void setRoutesExcludePattern(String routesExcludePattern) {
        this.routesExcludePattern = routesExcludePattern;
    }

    public String getRoutesIncludePattern() {
        return routesIncludePattern;
    }

    public void setRoutesIncludePattern(String routesIncludePattern) {
        this.routesIncludePattern = routesIncludePattern;
    }

    public RoutesCollector getRoutesCollector() {
        return routesCollector;
    }

    public void setRoutesCollector(RoutesCollector routesCollector) {
        this.routesCollector = routesCollector;
    }

    public CamelBeanPostProcessor getBeanPostProcessor() {
        return beanPostProcessor;
    }

    public void setBeanPostProcessor(CamelBeanPostProcessor beanPostProcessor) {
        this.beanPostProcessor = beanPostProcessor;
    }

    /**
     * Collects routes and rests from the various sources (like registry or opinionated classpath locations) and injects
     * (adds) these into the Camel context.
     *
     * @param camelContext the Camel context
     */
    public void configureRoutes(CamelContext camelContext) throws Exception {
        final List<RoutesBuilder> routes = new ArrayList<>();
        if (getRoutesBuilders() != null) {
            routes.addAll(getRoutesBuilders());
        }

        if (getRoutesBuilderClasses() != null) {
            String[] routeClasses = getRoutesBuilderClasses().split(",");
            for (String routeClass : routeClasses) {
                Class<RoutesBuilder> routeClazz = camelContext.getClassResolver().resolveClass(routeClass, RoutesBuilder.class);
                if (routeClazz == null) {
                    LOG.warn("Unable to resolve class: {}", routeClass);
                    continue;
                }

                // lets use Camel's injector so the class has some support for dependency injection
                RoutesBuilder builder = camelContext.getInjector().newInstance(routeClazz);

                routes.add(builder);
            }
        }

        if (getPackageScanRouteBuilders() != null) {
            String[] pkgs = getPackageScanRouteBuilders().split(",");
            Set<Class<?>> set = camelContext.adapt(ExtendedCamelContext.class)
                    .getPackageScanClassResolver()
                    .findImplementations(RoutesBuilder.class, pkgs);

            for (Class<?> routeClazz : set) {
                Object builder = camelContext.getInjector().newInstance(routeClazz);
                if (builder instanceof RoutesBuilder) {
                    routes.add((RoutesBuilder) builder);
                } else {
                    LOG.warn("Class {} is not a RouteBuilder class", routeClazz);
                }
            }
        }

        if (getRoutesCollector() != null) {
            try {
                LOG.debug("RoutesCollectorEnabled: {}", getRoutesCollector());

                // add discovered routes from registry
                routes.addAll(getRoutesCollector().collectRoutesFromRegistry(
                        camelContext,
                        getJavaRoutesExcludePattern(),
                        getJavaRoutesIncludePattern()));
                // add discovered routes from directories
                routes.addAll(getRoutesCollector().collectRoutesFromDirectory(
                        camelContext,
                        getRoutesExcludePattern(),
                        getRoutesIncludePattern()));

            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        if (getBeanPostProcessor() != null) {
            // lets use Camel's bean post processor on any existing route builder classes
            // so the instance has some support for dependency injection

            for (RoutesBuilder routeBuilder : routes) {
                getBeanPostProcessor().postProcessBeforeInitialization(routeBuilder, routeBuilder.getClass().getName());
                getBeanPostProcessor().postProcessAfterInitialization(routeBuilder, routeBuilder.getClass().getName());
            }
        }

        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // then add the routes
        for (RoutesBuilder builder : routes) {
            LOG.debug("Adding routes into CamelContext from RoutesBuilder: {}", builder);
            camelContext.addRoutes(builder);
        }

        Set<ConfigureRouteTemplates> set = camelContext.getRegistry().findByType(ConfigureRouteTemplates.class);
        for (ConfigureRouteTemplates crt : set) {
            LOG.debug("Configuring route templates via: {}", crt);
            crt.configure(camelContext);
        }
    }
}
