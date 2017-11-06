/**
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

package org.apache.camel.spring.boot.cluster;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.cluster.CamelClusterService;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.clustered.controller")
public class ClusteredRouteControllerConfiguration {
    /**
     * Global option to enable/disable this ${@link org.apache.camel.spi.RouteController}, default is false.
     */
    private boolean enabled;

    /**
     * Set the amount of time the route controller should wait before to start
     * the routes after the camel context is started or after the route is
     * initialized if the route is created after the camel context is started.
     */
    private String initialDelay;

    /**
     * The default namespace.
     */
    private String namespace;

    /**
     * The reference to a cluster service.
     */
    private String clusterServiceRef;

    /**
     * The cluster service.
     */
    private CamelClusterService clusterService;

    /**
     * Routes configuration.
     */
    private Map<String, RouteConfiguration> routes = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(String initialDelay) {
        this.initialDelay = initialDelay;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Map<String, RouteConfiguration> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, RouteConfiguration> routes) {
        this.routes = routes;
    }

    public CamelClusterService getClusterService() {
        return clusterService;
    }

    public void setClusterService(CamelClusterService clusterService) {
        this.clusterService = clusterService;
    }

    // *****************************************
    // Configuration Classes
    // *****************************************

    public static class RouteConfiguration {
        /**
         * Control if the route should be clustered or not, default is true.
         */
        private boolean clustered = true;

        /**
         * Set the amount of time the route controller should wait before to start
         * the routes after the camel context is started or after the route is
         * initialized if the route is created after the camel context is started.
         */
        private String initialDelay;

        /**
         * The default namespace.
         */
        private String namespace;


        public boolean isClustered() {
            return clustered;
        }

        public void setClustered(boolean clustered) {
            this.clustered = clustered;
        }

        public String getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(String initialDelay) {
            this.initialDelay = initialDelay;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }
}
