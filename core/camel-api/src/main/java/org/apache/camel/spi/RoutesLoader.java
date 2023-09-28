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
package org.apache.camel.spi;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.camel.CamelContextAware;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;

/**
 * SPI for loading {@link RoutesBuilder} from a list of {@link Resource}.
 */
public interface RoutesLoader extends CamelContextAware {

    /**
     * Service factory key.
     */
    String FACTORY = "routes-loader";

    /**
     * Whether to ignore route loading and compilation errors (use this with care!)
     */
    boolean isIgnoreLoadingError();

    /**
     * Whether to ignore route loading and compilation errors (use this with care!)
     */
    void setIgnoreLoadingError(boolean ignoreLoadingError);

    /**
     * Looks up a {@link RoutesBuilderLoader} in the registry or fallback to a factory finder mechanism if none found.
     *
     * @param  extension                the file extension for which a loader should be found.
     * @return                          a {@link RoutesBuilderLoader}
     * @throws IllegalArgumentException if no {@link RoutesBuilderLoader} can be found for the given file extension
     */
    RoutesBuilderLoader getRoutesLoader(String extension) throws Exception;

    /**
     * Loads {@link RoutesBuilder} from the give list of {@link Resource} into the current
     * {@link org.apache.camel.CamelContext}.
     *
     * @param resources the resources to be loaded.
     */
    default void loadRoutes(Collection<Resource> resources) throws Exception {
        Collection<RoutesBuilder> builders = findRoutesBuilders(resources);
        // add configuration first before the routes
        for (RoutesBuilder builder : builders) {
            if (builder instanceof RouteConfigurationsBuilder) {
                getCamelContext().addRoutesConfigurations((RouteConfigurationsBuilder) builder);
            }
        }
        for (RoutesBuilder builder : builders) {
            getCamelContext().addRoutes(builder);
        }
        for (RoutesBuilder builder : builders) {
            getCamelContext().addTemplatedRoutes(builder);
        }
    }

    /**
     * Loads {@link RoutesBuilder} from the give list of {@link Resource} into the current
     * {@link org.apache.camel.CamelContext}.
     *
     * @param resources the resources to be loaded.
     */
    default void loadRoutes(Resource... resources) throws Exception {
        Collection<RoutesBuilder> builders = findRoutesBuilders(resources);
        // add configuration first before the routes
        for (RoutesBuilder builder : builders) {
            if (builder instanceof RouteConfigurationsBuilder) {
                getCamelContext().addRoutesConfigurations((RouteConfigurationsBuilder) builder);
            }
        }
        for (RoutesBuilder builder : builders) {
            getCamelContext().addRoutes(builder);
        }
        for (RoutesBuilder builder : builders) {
            getCamelContext().addTemplatedRoutes(builder);
        }
    }

    /**
     * Loads or updates existing {@link RoutesBuilder} from the give list of {@link Resource} into the current
     * {@link org.apache.camel.CamelContext}.
     *
     * If a route is loaded with a route id for an existing route, then the existing route is stopped and remove, so it
     * can be updated.
     *
     * @param  resources the resources to be loaded or updated.
     * @return           route ids for the routes that was loaded or updated.
     */
    default Set<String> updateRoutes(Resource... resources) throws Exception {
        return updateRoutes(Arrays.asList(resources));
    }

    /**
     * Loads or updates existing {@link RoutesBuilder} from the give list of {@link Resource} into the current
     * {@link org.apache.camel.CamelContext}.
     *
     * If a route is loaded with a route id for an existing route, then the existing route is stopped and remove, so it
     * can be updated.
     *
     * @param  resources the resources to be loaded or updated.
     * @return           route ids for the routes that was loaded or updated.
     */
    Set<String> updateRoutes(Collection<Resource> resources) throws Exception;

    /**
     * Find {@link RoutesBuilder} from the give list of {@link Resource}.
     *
     * @param  resources the resource to be loaded.
     * @return           a collection of {@link RoutesBuilder}
     */
    default Collection<RoutesBuilder> findRoutesBuilders(Resource... resources) throws Exception {
        return findRoutesBuilders(Arrays.asList(resources));
    }

    /**
     * Find {@link RoutesBuilder} from the give list of {@link Resource}.
     *
     * @param  resources the resource to be loaded.
     * @return           a collection {@link RoutesBuilder}
     */
    Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources) throws Exception;

    /**
     * Find {@link RoutesBuilder} from the give list of {@link Resource}.
     *
     * @param  resources the resource to be loaded.
     * @param  optional  whether parsing the resource is optional, such as there is no supported parser for the given
     *                   resource extension
     * @return           a collection {@link RoutesBuilder}
     */
    Collection<RoutesBuilder> findRoutesBuilders(Collection<Resource> resources, boolean optional) throws Exception;

    /**
     * Pre-parses the {@link RoutesBuilder} from {@link Resource}.
     *
     * This is used during bootstrap, to eager detect configurations from route DSL resources which makes it possible to
     * specify configurations that affect the bootstrap, such as by camel-jbang and camel-yaml-dsl.
     *
     * @param resource the resource to be pre parsed.
     * @param optional whether parsing the resource is optional, such as there is no supported parser for the given
     *                 resource extension
     */
    default void preParseRoute(Resource resource, boolean optional) throws Exception {
        // noop
    }

    /**
     * Initializes the discovered {@link RoutesBuilderLoader} before its started and used for the first time.
     */
    default void initRoutesBuilderLoader(RoutesBuilderLoader loader) {
        // noop
    }
}
