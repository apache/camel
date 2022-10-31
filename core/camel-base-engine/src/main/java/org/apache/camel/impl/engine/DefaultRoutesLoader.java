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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ExtendedRoutesBuilderLoader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ModelineFactory;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * Default {@link RoutesLoader}.
 */
public class DefaultRoutesLoader extends ServiceSupport implements RoutesLoader, StaticService {

    /**
     * Prefix to use for looking up existing {@link RoutesLoader} from the {@link org.apache.camel.spi.Registry}.
     */
    public static final String ROUTES_LOADER_KEY_PREFIX = "routes-builder-loader-";

    private final Map<String, RoutesBuilderLoader> loaders;

    private CamelContext camelContext;

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

    @Override
    public Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources) throws Exception {
        List<RoutesBuilder> answer = new ArrayList<>(resources.size());

        // first we need to parse for modeline to gather all the configurations
        if (camelContext.isModeline()) {
            ModelineFactory factory = camelContext.adapt(ExtendedCamelContext.class).getModelineFactory();
            for (Resource resource : resources) {
                RoutesBuilderLoader loader = resolveRoutesBuilderLoader(resource);
                // gather resources for modeline
                factory.parseModeline(resource);
                // pre-parse before loading
                loader.preParseRoute(resource);
            }
        }

        // now group resources by loader
        Map<RoutesBuilderLoader, List<Resource>> groups = new LinkedHashMap<>();
        for (Resource resource : resources) {
            RoutesBuilderLoader loader = resolveRoutesBuilderLoader(resource);
            List<Resource> list = groups.getOrDefault(loader, new ArrayList<>());
            list.add(resource);
            groups.put(loader, list);
        }

        // now load all the same resources for each loader
        for (Map.Entry<RoutesBuilderLoader, List<Resource>> entry : groups.entrySet()) {
            RoutesBuilderLoader loader = entry.getKey();
            if (loader instanceof ExtendedRoutesBuilderLoader) {
                // extended loader can load all resources ine one unit
                ExtendedRoutesBuilderLoader extLoader = (ExtendedRoutesBuilderLoader) loader;
                Collection<RoutesBuilder> builders = extLoader.loadRoutesBuilders(entry.getValue());
                if (builders != null) {
                    answer.addAll(builders);
                }
            } else {
                for (Resource resource : entry.getValue()) {
                    RoutesBuilder builder = loader.loadRoutesBuilder(resource);
                    if (builder != null) {
                        answer.add(builder);
                    }
                }
            }
        }

        return answer;
    }

    @Override
    public void preParseRoute(Resource resource) throws Exception {
        resolveRoutesBuilderLoader(resource).preParseRoute(resource);
    }

    @Override
    public RoutesBuilderLoader getRoutesLoader(String extension) throws Exception {
        RoutesBuilderLoader answer = getCamelContext().getRegistry().lookupByNameAndType(
                ROUTES_LOADER_KEY_PREFIX + extension,
                RoutesBuilderLoader.class);

        if (answer == null) {
            answer = loaders.computeIfAbsent(extension, this::resolveService);
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
        final ExtendedCamelContext ecc = getCamelContext().adapt(ExtendedCamelContext.class);
        final FactoryFinder finder = ecc.getBootstrapFactoryFinder(RoutesBuilderLoader.FACTORY_PATH);

        RoutesBuilderLoader answer
                = ResolverHelper.resolveService(ecc, finder, extension, RoutesBuilderLoader.class).orElse(null);

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

    protected RoutesBuilderLoader resolveRoutesBuilderLoader(Resource resource) throws Exception {
        // the loader to use is derived from the file extension
        final String extension = FileUtil.onlyExt(resource.getLocation(), false);

        if (ObjectHelper.isEmpty(extension)) {
            throw new IllegalArgumentException(
                    "Unable to determine file extension for resource: " + resource.getLocation());
        }

        RoutesBuilderLoader loader = getRoutesLoader(extension);
        if (loader == null) {
            throw new IllegalArgumentException(
                    "Cannot find RoutesBuilderLoader in classpath supporting file extension: " + extension);
        }
        return loader;
    }

}
