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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.health.HealthCheckRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Strategy that creates the managed wrapper objects Camel registers in JMX for its internal artifacts.
 * <p/>
 * Before Camel can register a {@link org.apache.camel.CamelContext}, {@link org.apache.camel.Route},
 * {@link org.apache.camel.Processor}, {@link org.apache.camel.Component}, or other runtime object as an MBean, it needs
 * a managed facade that exposes the right attributes and operations. This strategy supplies those facades (for example,
 * {@code ManagedCamelContext}, {@code ManagedRoute}, {@code ManagedProcessor}) for each artifact type.
 * <p/>
 * The {@link ManagementStrategy} calls the appropriate {@code getManagedObjectFor*} method, then the returned object is
 * assembled into a {@link javax.management.modelmbean.ModelMBean} by {@link ManagementMBeanAssembler} and registered
 * under the {@link javax.management.ObjectName} computed by {@link ManagementObjectNameStrategy}. Replacing this
 * strategy allows advanced users to substitute custom MBean facades for any Camel type.
 * <p/>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementObjectNameStrategy
 * @see ManagementMBeanAssembler
 * @see ManagementStrategy
 */
public interface ManagementObjectStrategy {

    Object getManagedObjectForCamelContext(CamelContext context);

    Object getManagedObjectForCamelHealth(CamelContext context, HealthCheckRegistry healthCheckRegistry);

    Object getManagedObjectForComponent(CamelContext context, Component component, String name);

    Object getManagedObjectForDataFormat(CamelContext context, DataFormat dataFormat);

    Object getManagedObjectForEndpoint(CamelContext context, Endpoint endpoint);

    Object getManagedObjectForRouteController(CamelContext context, RouteController routeController);

    Object getManagedObjectForRoute(CamelContext context, Route route);

    Object getManagedObjectForRouteGroup(CamelContext context, String group);

    Object getManagedObjectForConsumer(CamelContext context, Consumer consumer);

    Object getManagedObjectForProducer(CamelContext context, Producer producer);

    Object getManagedObjectForProcessor(
            CamelContext context, Processor processor,
            NamedNode definition, Route route);

    Object getManagedObjectForService(CamelContext context, Service service);

    Object getManagedObjectForClusterService(CamelContext context, CamelClusterService service);

    Object getManagedObjectForThreadPool(
            CamelContext context, ThreadPoolExecutor threadPool,
            String id, String sourceId, String routeId, String threadPoolProfileId);

    default @Nullable Object getManagedObjectForThreadPool(
            CamelContext context, ExecutorService executorService,
            String id, String sourceId, String routeId, String threadPoolProfileId) {
        return null;
    }

    Object getManagedObjectForEventNotifier(CamelContext context, EventNotifier eventNotifier);
}
