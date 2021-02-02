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

    private final RoutesCollector routesCollector;

    /**
     * Creates a new routes configurer
     *
     * @param routesCollector routes collector
     */
    public RoutesConfigurer(RoutesCollector routesCollector) {
        this.routesCollector = routesCollector;
    }

    /**
     * Collects routes and rests from the various sources (like registry or opinionated classpath locations) and injects
     * (adds) these into the Camel context.
     *
     * @param camelContext the Camel context
     * @param config       the configuration
     */
    public void configureRoutes(CamelContext camelContext, MainConfigurationProperties config) throws Exception {
        final List<RoutesBuilder> routes = new ArrayList<>(config.getRoutesBuilders());

        if (config.getRoutesBuilderClasses() != null) {
            String[] routeClasses = config.getRoutesBuilderClasses().split(",");
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

        if (config.getPackageScanRouteBuilders() != null) {
            String[] pkgs = config.getPackageScanRouteBuilders().split(",");
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

        if (config.isRoutesCollectorEnabled()) {
            try {
                LOG.debug("RoutesCollectorEnabled: {}", routesCollector);

                // add discovered routes from registry
                routes.addAll(routesCollector.collectRoutesFromRegistry(
                        camelContext,
                        config.getJavaRoutesExcludePattern(),
                        config.getJavaRoutesIncludePattern()));
                // add discovered routes from directories
                routes.addAll(routesCollector.collectRoutesFromDirectory(
                        camelContext,
                        config.getRoutesExcludePattern(),
                        config.getRoutesIncludePattern()));

            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        // lets use Camel's bean post processor on any existing route builder classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
        for (RoutesBuilder routeBuilder : routes) {
            postProcessor.postProcessBeforeInitialization(routeBuilder, routeBuilder.getClass().getName());
            postProcessor.postProcessAfterInitialization(routeBuilder, routeBuilder.getClass().getName());
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
