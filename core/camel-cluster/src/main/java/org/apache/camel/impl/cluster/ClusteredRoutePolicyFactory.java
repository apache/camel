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
package org.apache.camel.impl.cluster;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.apache.camel.util.ObjectHelper;

public class ClusteredRoutePolicyFactory implements RoutePolicyFactory {
    private final String namespace;
    private final CamelClusterService clusterService;
    private final CamelClusterService.Selector clusterServiceSelector;

    public ClusteredRoutePolicyFactory(String namespace) {
        ObjectHelper.notNull(namespace, "Cluster View Namespace");

        this.namespace = namespace;
        this.clusterService = null;
        this.clusterServiceSelector = ClusterServiceSelectors.DEFAULT_SELECTOR;
    }

    public ClusteredRoutePolicyFactory(CamelClusterService.Selector selector, String namespace) {
        ObjectHelper.notNull(namespace, "Cluster View Namespace");
        ObjectHelper.notNull(selector, "Cluster Service Selector");

        this.namespace = namespace;
        this.clusterService = null;
        this.clusterServiceSelector = selector;
    }

    public ClusteredRoutePolicyFactory(CamelClusterService clusterService, String viewName) {
        ObjectHelper.notNull(clusterService, "Cluster Service");
        ObjectHelper.notNull(viewName, "Cluster View Namespace");

        this.clusterService = clusterService;
        this.namespace = viewName;
        this.clusterServiceSelector = null;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        try {
            return clusterService != null
                ? ClusteredRoutePolicy.forNamespace(clusterService, namespace)
                : ClusteredRoutePolicy.forNamespace(camelContext, clusterServiceSelector, namespace);
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

    public static ClusteredRoutePolicyFactory forNamespace(CamelClusterService.Selector selector, String namespace) {
        return new ClusteredRoutePolicyFactory(selector, namespace);
    }

    public static ClusteredRoutePolicyFactory forNamespace(CamelClusterService clusterService, String namespace) {
        return new ClusteredRoutePolicyFactory(clusterService, namespace);
    }
}
