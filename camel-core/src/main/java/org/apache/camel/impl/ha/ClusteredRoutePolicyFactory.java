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
package org.apache.camel.impl.ha;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.util.ObjectHelper;

public class ClusteredRoutePolicyFactory implements RoutePolicyFactory {
    private final String namespace;
    private final CamelClusterService clusterService;

    public ClusteredRoutePolicyFactory(String viewName) {
        this.namespace = ObjectHelper.notNull(viewName, "Cluster View Namespace");
        this.clusterService = null;
    }

    public ClusteredRoutePolicyFactory(CamelClusterService clusterService, String viewName) {
        this.namespace = ObjectHelper.notNull(viewName, "Cluster View Namespace");
        this.clusterService = ObjectHelper.notNull(clusterService, "Cluster Service");
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition route) {
        try {
            return clusterService != null
                ? ClusteredRoutePolicy.forNamespace(clusterService, namespace)
                : ClusteredRoutePolicy.forNamespace(camelContext, namespace);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    // ****************************************************
    // Static helpers
    // ****************************************************

    public static ClusteredRoutePolicyFactory forNamespace(String namespace) {
        return new ClusteredRoutePolicyFactory(namespace);
    }

    public static ClusteredRoutePolicyFactory forNamespace(CamelClusterService clusterService, String namespace) {
        return new ClusteredRoutePolicyFactory(clusterService, namespace);
    }
}
