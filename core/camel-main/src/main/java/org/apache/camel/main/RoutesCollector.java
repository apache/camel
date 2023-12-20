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

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.spi.Resource;

/**
 * Collects routes and rests from the various sources (like registry or opinionated classpath locations) and adds these
 * into the Camel context.
 */
public interface RoutesCollector {

    /**
     * Whether to ignore route loading and compilation errors (use this with care!)
     */
    boolean isIgnoreLoadingError();

    /**
     * Whether to ignore route loading and compilation errors (use this with care!)
     */
    void setIgnoreLoadingError(boolean ignoreLoadingError);

    /**
     * Collects the {@link RoutesBuilder} instances which was discovered from the {@link org.apache.camel.spi.Registry}
     * such as Spring or CDI bean containers.
     *
     * @param  camelContext   the Camel Context
     * @param  excludePattern exclude pattern (see javaRoutesExcludePattern option)
     * @param  includePattern include pattern (see javaRoutesIncludePattern option)
     * @return                the discovered routes or an empty list
     */
    Collection<RoutesBuilder> collectRoutesFromRegistry(
            CamelContext camelContext, String excludePattern, String includePattern);

    /**
     * Collects all {@link RoutesBuilder} from the given directory.
     *
     * @param  camelContext   the Camel Context
     * @param  excludePattern exclude pattern (see routesExcludePattern option)
     * @param  includePattern include pattern (see routesIncludePattern option)
     * @return                the discovered RoutesBuilder or an empty collection
     */
    Collection<RoutesBuilder> collectRoutesFromDirectory(
            CamelContext camelContext, String excludePattern, String includePattern);

    /**
     * Finds all routes as {@link Resource} from the given directory.
     *
     * @param  camelContext   the Camel Context
     * @param  excludePattern exclude pattern (see routesExcludePattern option)
     * @param  includePattern include pattern (see routesIncludePattern option)
     * @return                the discovered routes as {@link Resource} or an empty collection
     */
    Collection<Resource> findRouteResourcesFromDirectory(
            CamelContext camelContext, String excludePattern, String includePattern);

}
