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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.ExtendedRoutesBuilderLoader;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To configure routes using {@link RoutesCollector} which collects the routes from various sources.
 */
public class RoutesConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(RoutesConfigurer.class);

    private RoutesCollector routesCollector;
    private boolean ignoreLoadingError;
    private CamelBeanPostProcessor beanPostProcessor;
    private List<RoutesBuilder> routesBuilders;
    private String basePackageScan;
    private String routesBuilderClasses;
    private String javaRoutesExcludePattern;
    private String javaRoutesIncludePattern;
    private String routesExcludePattern;
    private String routesIncludePattern;
    private String routesSourceDir;

    public boolean isIgnoreLoadingError() {
        return ignoreLoadingError;
    }

    public void setIgnoreLoadingError(boolean ignoreLoadingError) {
        this.ignoreLoadingError = ignoreLoadingError;
    }

    public List<RoutesBuilder> getRoutesBuilders() {
        return routesBuilders;
    }

    public void setRoutesBuilders(List<RoutesBuilder> routesBuilders) {
        this.routesBuilders = routesBuilders;
    }

    public String getBasePackageScan() {
        return basePackageScan;
    }

    public void setBasePackageScan(String basePackageScan) {
        this.basePackageScan = basePackageScan;
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

    public String getRoutesSourceDir() {
        return routesSourceDir;
    }

    public void setRoutesSourceDir(String routesSourceDir) {
        this.routesSourceDir = routesSourceDir;
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

        if (getBasePackageScan() != null) {
            String[] pkgs = getBasePackageScan().split(",");
            Set<Class<?>> set = PluginHelper.getPackageScanClassResolver(camelContext)
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
                Collection<RoutesBuilder> routesFromRegistry = getRoutesCollector().collectRoutesFromRegistry(
                        camelContext,
                        getJavaRoutesExcludePattern(),
                        getJavaRoutesIncludePattern());
                routes.addAll(routesFromRegistry);

                if (LOG.isDebugEnabled() && !routesFromRegistry.isEmpty()) {
                    LOG.debug("Discovered {} additional RoutesBuilder from registry: {}", routesFromRegistry.size(),
                            getRoutesIncludePattern());
                }

                // add discovered routes from directories
                StopWatch watch = new StopWatch();
                Collection<RoutesBuilder> routesFromDirectory = getRoutesCollector().collectRoutesFromDirectory(
                        camelContext,
                        getRoutesExcludePattern(),
                        getRoutesIncludePattern());
                routes.addAll(routesFromDirectory);

                if (LOG.isDebugEnabled() && !routesFromDirectory.isEmpty()) {
                    LOG.debug("Loaded {} additional RoutesBuilder from: {} (took {})", routesFromDirectory.size(),
                            getRoutesIncludePattern(), TimeUtils.printDuration(watch.taken(), true));
                }
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

        // add the discovered routes
        addDiscoveredRoutes(camelContext, routes);
    }

    private void addDiscoveredRoutes(CamelContext camelContext, List<RoutesBuilder> routes) throws Exception {
        // sort routes according to ordered
        routes.sort(OrderedComparator.get());

        // first add the routes configurations as they are globally for all routes
        for (RoutesBuilder builder : routes) {
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                LOG.debug("Adding routes configurations into CamelContext from RouteConfigurationsBuilder: {}", rcb);
                camelContext.addRoutesConfigurations(rcb);
            }
        }
        // then add the routes
        for (RoutesBuilder builder : routes) {
            LOG.debug("Adding routes into CamelContext from RoutesBuilder: {}", builder);
            camelContext.addRoutes(builder);
        }
        // then add templated routes last
        for (RoutesBuilder builder : routes) {
            LOG.debug("Adding templated routes into CamelContext from RoutesBuilder: {}", builder);
            camelContext.addTemplatedRoutes(builder);
        }
    }

    /**
     * Discover routes and rests from directories and scan for modeline present in their source code, which is then
     * parsed using {@link ModelineFactory}.
     *
     * @param camelContext the Camel context
     */
    public void configureModeline(CamelContext camelContext) throws Exception {
        if (getRoutesCollector() == null) {
            return;
        }

        Collection<Resource> resources;
        try {
            LOG.debug("RoutesCollectorEnabled: {}", getRoutesCollector());

            // include pattern may indicate a resource is optional, so we need to scan twice
            String pattern = getRoutesIncludePattern();
            String optionalPattern = null;
            if (pattern != null && pattern.contains("?optional=true")) {
                StringJoiner sj1 = new StringJoiner(",");
                StringJoiner sj2 = new StringJoiner(",");
                for (String p : pattern.split(",")) {
                    if (p.endsWith("?optional=true")) {
                        sj2.add(p.substring(0, p.length() - 14));
                    } else {
                        sj1.add(p);
                    }
                }
                pattern = sj1.length() > 0 ? sj1.toString() : null;
                optionalPattern = sj2.length() > 0 ? sj2.toString() : null;
            }

            // we can only scan for modeline for routes that we can load from directory as modelines
            // are comments in the source files
            if (optionalPattern == null) {
                resources = getRoutesCollector().findRouteResourcesFromDirectory(camelContext, getRoutesExcludePattern(),
                        pattern);
                doConfigureModeline(camelContext, resources, false);
            } else {
                // we have optional resources
                resources = getRoutesCollector().findRouteResourcesFromDirectory(camelContext, getRoutesExcludePattern(),
                        optionalPattern);
                doConfigureModeline(camelContext, resources, true);
                // and then mandatory after
                if (pattern != null) {
                    resources = getRoutesCollector().findRouteResourcesFromDirectory(camelContext, getRoutesExcludePattern(),
                            pattern);
                    doConfigureModeline(camelContext, resources, false);
                }
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    protected void doConfigureModeline(CamelContext camelContext, Collection<Resource> resources, boolean optional)
            throws Exception {

        // sort groups so java is first
        List<Resource> sort = new ArrayList<>(resources);
        sort.sort((o1, o2) -> {
            String ext1 = FileUtil.onlyExt(o1.getLocation(), false);
            String ext2 = FileUtil.onlyExt(o2.getLocation(), false);
            if ("java".equals(ext1)) {
                return -1;
            } else if ("java".equals(ext2)) {
                return 1;
            }
            return 0;
        });

        // group resources by loader (java, xml, yaml in their own group)
        Map<RoutesBuilderLoader, List<Resource>> groups = new LinkedHashMap<>();
        for (Resource resource : sort) {
            RoutesBuilderLoader loader = resolveRoutesBuilderLoader(camelContext, resource, optional);
            if (loader != null) {
                List<Resource> list = groups.getOrDefault(loader, new ArrayList<>());
                list.add(resource);
                groups.put(loader, list);
            }
        }

        if (camelContext.isModeline()) {
            // parse modelines for all resources
            ModelineFactory factory = PluginHelper.getModelineFactory(camelContext);
            for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
                for (Resource resource : entry.getValue()) {
                    factory.parseModeline(resource);
                }
            }
        }

        // the resource may also have additional configurations which we need to detect via pre-parsing
        for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
            RoutesBuilderLoader loader = entry.getKey();
            if (loader instanceof ExtendedRoutesBuilderLoader) {
                // extended loader can pre-parse all resources ine one unit
                ExtendedRoutesBuilderLoader extLoader = (ExtendedRoutesBuilderLoader) loader;
                List<Resource> files = entry.getValue();
                try {
                    extLoader.preParseRoutes(files);
                } catch (Exception e) {
                    if (ignoreLoadingError) {
                        LOG.warn("Loading resources error: {} due to: {}. This exception is ignored.", files, e.getMessage());
                    } else {
                        throw e;
                    }
                }
            } else {
                for (Resource resource : entry.getValue()) {
                    try {
                        loader.preParseRoute(resource);
                    } catch (Exception e) {
                        if (ignoreLoadingError) {
                            LOG.warn("Loading resources error: {} due to: {}. This exception is ignored.", resource,
                                    e.getMessage());
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    protected RoutesBuilderLoader resolveRoutesBuilderLoader(
            CamelContext camelContext, Resource resource,
            boolean optional)
            throws Exception {
        // the loader to use is derived from the file extension
        final String extension = FileUtil.onlyExt(resource.getLocation(), false);

        if (ObjectHelper.isEmpty(extension)) {
            throw new IllegalArgumentException(
                    "Unable to determine file extension for resource: " + resource.getLocation());
        }

        RoutesLoader loader = PluginHelper.getRoutesLoader(camelContext);
        RoutesBuilderLoader answer = loader.getRoutesLoader(extension);
        if (!optional && answer == null) {
            throw new IllegalArgumentException(
                    "Cannot find RoutesBuilderLoader in classpath supporting file extension: " + extension);
        }
        return answer;
    }

}
