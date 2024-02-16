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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ExtendedRoutesBuilderLoader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RoutesLoader}.
 */
public class DefaultRoutesLoader extends ServiceSupport implements RoutesLoader, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoutesLoader.class);

    /**
     * Prefix to use for looking up existing {@link RoutesLoader} from the {@link org.apache.camel.spi.Registry}.
     */
    public static final String ROUTES_LOADER_KEY_PREFIX = "routes-builder-loader-";

    private final Map<String, RoutesBuilderLoader> loaders;

    private CamelContext camelContext;
    private boolean ignoreLoadingError;

    public DefaultRoutesLoader() {
        this(null);
    }

    public DefaultRoutesLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.loaders = new ConcurrentHashMap<>();
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(loaders.values());
        loaders.clear();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isIgnoreLoadingError() {
        return ignoreLoadingError;
    }

    public void setIgnoreLoadingError(boolean ignoreLoadingError) {
        this.ignoreLoadingError = ignoreLoadingError;
    }

    @Override
    public Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources) throws Exception {
        return findRoutesBuilders(resources, false);
    }

    @Override
    public Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources, boolean optional) throws Exception {
        List<RoutesBuilder> answer = new ArrayList<>(resources.size());

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
            RoutesBuilderLoader loader = resolveRoutesBuilderLoader(resource, optional);
            if (loader != null) {
                List<Resource> list = groups.getOrDefault(loader, new ArrayList<>());
                list.add(resource);
                groups.put(loader, list);
            }
        }

        // first we need to parse for modeline to gather all the configurations
        if (camelContext.isModeline()) {
            ModelineFactory factory = PluginHelper.getModelineFactory(camelContext);
            for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
                // parse modelines for all resources
                for (Resource resource : entry.getValue()) {
                    factory.parseModeline(resource);
                }
            }
        }

        // then pre-parse routes
        for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
            RoutesBuilderLoader loader = entry.getKey();
            if (loader instanceof ExtendedRoutesBuilderLoader) {
                // extended loader can load all resources ine one unit
                ExtendedRoutesBuilderLoader extLoader = (ExtendedRoutesBuilderLoader) loader;
                // pre-parse before loading
                List<Resource> files = entry.getValue();
                try {
                    extLoader.preParseRoutes(files);
                } catch (Exception e) {
                    if (isIgnoreLoadingError()) {
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
                        if (isIgnoreLoadingError()) {
                            LOG.warn("Loading resources error: {} due to: {}. This exception is ignored.", resource,
                                    e.getMessage());
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }

        // now load all the same resources for each loader
        for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
            RoutesBuilderLoader loader = entry.getKey();
            if (loader instanceof ExtendedRoutesBuilderLoader) {
                // extended loader can load all resources ine one unit
                ExtendedRoutesBuilderLoader extLoader = (ExtendedRoutesBuilderLoader) loader;
                List<Resource> files = entry.getValue();
                try {
                    Collection<RoutesBuilder> builders = extLoader.loadRoutesBuilders(files);
                    if (builders != null) {
                        answer.addAll(builders);
                    }
                } catch (Exception e) {
                    if (isIgnoreLoadingError()) {
                        LOG.warn("Loading resources error: {} due to: {}. This exception is ignored.", files, e.getMessage());
                    } else {
                        throw e;
                    }
                }
            } else {
                for (Resource resource : entry.getValue()) {
                    try {
                        RoutesBuilder builder = loader.loadRoutesBuilder(resource);
                        if (builder != null) {
                            answer.add(builder);
                        }
                    } catch (Exception e) {
                        if (isIgnoreLoadingError()) {
                            LOG.warn("Loading resources error: {} due to: {}. This exception is ignored.", resource,
                                    e.getMessage());
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public void preParseRoute(Resource resource, boolean optional) throws Exception {
        RoutesBuilderLoader loader = resolveRoutesBuilderLoader(resource, optional);
        if (loader != null) {
            loader.preParseRoute(resource);
        }
    }

    @Override
    public RoutesBuilderLoader getRoutesLoader(String extension) throws Exception {
        ObjectHelper.notNull(extension, "extension");

        RoutesBuilderLoader answer = getCamelContext().getRegistry().lookupByNameAndType(
                ROUTES_LOADER_KEY_PREFIX + extension,
                RoutesBuilderLoader.class);

        if (answer == null) {
            answer = loaders.values().stream()
                    // find existing loader that support this extension
                    .filter(l -> l.isSupportedExtension(extension)).findFirst()
                    // or resolve loader from classpath
                    .orElse(loaders.computeIfAbsent(extension, this::resolveService));
        }

        return answer;
    }

    /**
     * Looks up a {@link RoutesBuilderLoader} for the given extension with factory finder.
     *
     * @param  extension the file extension for which a loader should be found.
     * @return           a {@link RoutesBuilderLoader} or null if none found
     */
    protected RoutesBuilderLoader resolveService(String extension) {
        final CamelContext ecc = getCamelContext();
        final FactoryFinder finder = ecc.getCamelContextExtension().getBootstrapFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);

        // the marker files are generated with dot as dash
        String sanitized = extension.replace(".", "-");
        RoutesBuilderLoader answer
                = ResolverHelper.resolveService(getCamelContext(), finder, sanitized, RoutesBuilderLoader.class).orElse(null);

        // if it's a multi-extension then fallback to parent
        if (answer == null && extension.contains(".")) {
            String single = FileUtil.onlyExt(extension, true);
            answer = ResolverHelper.resolveService(getCamelContext(), finder, single, RoutesBuilderLoader.class).orElse(null);
            if (answer != null && !answer.isSupportedExtension(extension)) {
                // okay we cannot support this extension as fallback
                answer = null;
            }
        }

        if (answer != null) {
            CamelContextAware.trySetCamelContext(answer, getCamelContext());
            // allows for custom initialization
            initRoutesBuilderLoader(answer);
            ServiceHelper.startService(answer);
        }

        return answer;
    }

    @Override
    public Set<String> updateRoutes(Collection<Resource> resources) throws Exception {
        Set<String> answer = new LinkedHashSet<>();
        if (resources == null || resources.isEmpty()) {
            return answer;
        }

        Collection<RoutesBuilder> builders = findRoutesBuilders(resources);
        for (RoutesBuilder builder : builders) {
            // update any existing route configurations first
            if (builder instanceof RouteConfigurationsBuilder) {
                RouteConfigurationsBuilder rcb = (RouteConfigurationsBuilder) builder;
                rcb.updateRouteConfigurationsToCamelContext(getCamelContext());
            }
        }
        for (RoutesBuilder builder : builders) {
            // update any existing routes
            Set<String> ids = builder.updateRoutesToCamelContext(getCamelContext());
            answer.addAll(ids);
        }

        return answer;
    }

    protected RoutesBuilderLoader resolveRoutesBuilderLoader(Resource resource, boolean optional) throws Exception {
        // the loader to use is derived from the file extension
        final String extension = FileUtil.onlyExt(resource.getLocation(), false);

        if (ObjectHelper.isEmpty(extension)) {
            throw new IllegalArgumentException(
                    "Unable to determine file extension for resource: " + resource.getLocation());
        }

        RoutesBuilderLoader loader = getRoutesLoader(extension);
        if (!optional && loader == null) {
            throw new IllegalArgumentException(
                    "Cannot find RoutesBuilderLoader in classpath supporting file extension: " + extension);
        }
        return loader;
    }

}
