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
import java.util.LinkedHashSet;
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
        Set<String> answer = new LinkedHashSet<>();
        Collection<RoutesBuilder> builders = findRoutesBuilders(resources);
        for (RoutesBuilder builder : builders) {
            // update any existing routes
            Set<String> ids = builder.updateRoutesToCamelContext(getCamelContext());
            answer.addAll(ids);
        }

        return answer;
    }

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
}
