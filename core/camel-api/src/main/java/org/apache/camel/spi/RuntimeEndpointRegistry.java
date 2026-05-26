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

import java.util.List;

import org.apache.camel.StaticService;

/**
 * A registry which listen for runtime usage of {@link org.apache.camel.Endpoint} during routing in Camel.
 */
public interface RuntimeEndpointRegistry extends StaticService {

    /**
     * Statistics gathered about the endpoint.
     */
    interface Statistic {

        /**
         * The endpoint uri
         */
        String getUri();

        /**
         * The route id (if the endpoint is associated with a route)
         */
        String getRouteId();

        /**
         * Whether the endpoint is used as input our output
         * <p/>
         * The returned value can either be <tt>in</tt> or <tt>out</tt>
         */
        String getDirection();

        /**
         * Usage of the endpoint, such as how many messages it has received / sent to
         * <p/>
         * This information is only available if {@link org.apache.camel.ManagementStatisticsLevel} is configured as
         * {@link org.apache.camel.ManagementStatisticsLevel#Extended}.
         */
        long getHits();

        /**
         * Minimum message body size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMinBodySize() {
            return -1;
        }

        /**
         * Maximum message body size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMaxBodySize() {
            return -1;
        }

        /**
         * Mean message body size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMeanBodySize() {
            return -1;
        }

        /**
         * Minimum message headers size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMinHeadersSize() {
            return -1;
        }

        /**
         * Maximum message headers size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMaxHeadersSize() {
            return -1;
        }

        /**
         * Mean message headers size in bytes (-1 if not available)
         *
         * @since 4.21
         */
        default long getMeanHeadersSize() {
            return -1;
        }
    }

    /**
     * Whether gathering runtime usage is enabled or not.
     */
    boolean isEnabled();

    /**
     * Sets whether gathering runtime usage is enabled or not.
     */
    void setEnabled(boolean enabled);

    /**
     * Maximum number of endpoints to keep in the cache per route.
     * <p/>
     * The default value is <tt>1000</tt>
     */
    int getLimit();

    /**
     * Sets the maximum number of endpoints to keep in the cache per route.
     */
    void setLimit(int limit);

    /**
     * Clears the registry
     */
    void clear();

    /**
     * Reset the statistic counters
     */
    void reset();

    /**
     * Number of endpoints currently in the cache.
     */
    int size();

    /**
     * Gets all the endpoint uris captured during runtime routing that are in-use of the routes.
     *
     * @param includeInputs whether to include route inputs
     */
    List<String> getAllEndpoints(boolean includeInputs);

    /**
     * Gets all the endpoint uris captured from the given route during runtime routing that are in-use of the routes.
     *
     * @param routeId       the route id
     * @param includeInputs whether to include route inputs
     */
    List<String> getEndpointsPerRoute(String routeId, boolean includeInputs);

    /**
     * Gets details about all the endpoint captured from the given route during runtime routing that are in-use of the
     * routes.
     */
    List<Statistic> getEndpointStatistics();

}
