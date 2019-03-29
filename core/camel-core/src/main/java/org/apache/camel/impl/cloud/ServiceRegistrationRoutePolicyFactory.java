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
package org.apache.camel.impl.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.cloud.ServiceRegistry;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceRegistrationRoutePolicyFactory implements RoutePolicyFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistrationRoutePolicyFactory.class);

    private final ServiceRegistry.Selector serviceRegistrySelector;
    private final ServiceRegistry serviceRegistry;

    public ServiceRegistrationRoutePolicyFactory() {
        this(null, ServiceRegistrySelectors.DEFAULT_SELECTOR);
    }

    public ServiceRegistrationRoutePolicyFactory(ServiceRegistry.Selector serviceRegistrySelector) {
        this(null, serviceRegistrySelector);
    }

    public ServiceRegistrationRoutePolicyFactory(ServiceRegistry serviceRegistry, ServiceRegistry.Selector serviceRegistrySelector) {
        this.serviceRegistry = serviceRegistry;
        this.serviceRegistrySelector = serviceRegistrySelector;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        return new ServiceRegistrationRoutePolicy(serviceRegistry, serviceRegistrySelector);
    }
}
