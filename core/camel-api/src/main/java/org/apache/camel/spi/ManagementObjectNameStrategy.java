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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

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
import org.jspecify.annotations.Nullable;

/**
 * Strategy for computing the full {@link ObjectName} (domain plus all key-value properties) for every Camel artifact
 * registered in JMX.
 * <p/>
 * A JMX {@link ObjectName} encodes both a domain (e.g., {@code org.apache.camel}) and a set of typed key-value pairs
 * (e.g., {@code context=myApp, type=routes, name=myRoute}). This strategy provides a dedicated factory method for each
 * Camel artifact type so that the correct type key and identifying attributes are always applied consistently.
 * <p/>
 * The {@link ManagementStrategy} calls the appropriate {@code getObjectNameFor*} method before invoking
 * {@link ManagementAgent#register(Object, ObjectName)} or {@link ManagementAgent#unregister(ObjectName)}. The domain
 * portion of the {@link ObjectName} is configured on {@link ManagementAgent#getMBeanObjectDomainName()}, while the name
 * key within the context entry is governed by {@link ManagementNameStrategy}.
 * <p/>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementNameStrategy
 * @see ManagementObjectStrategy
 * @see ManagementAgent
 */
public interface ManagementObjectNameStrategy {

    @Nullable
    ObjectName getObjectName(Object managedObject) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelContext(String managementName, String name) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelHealth(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelContext(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectNameForRouteController(CamelContext context, RouteController controller)
            throws MalformedObjectNameException;

    ObjectName getObjectNameForComponent(Component component, String name) throws MalformedObjectNameException;

    ObjectName getObjectNameForEndpoint(Endpoint endpoint) throws MalformedObjectNameException;

    ObjectName getObjectNameForDataFormat(CamelContext context, DataFormat endpoint) throws MalformedObjectNameException;

    ObjectName getObjectNameForProcessor(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException;

    ObjectName getObjectNameForStep(CamelContext context, Processor processor, NamedNode definition)
            throws MalformedObjectNameException;

    ObjectName getObjectNameForRoute(Route route) throws MalformedObjectNameException;

    ObjectName getObjectNameForRouteGroup(CamelContext camelContext, String group) throws MalformedObjectNameException;

    ObjectName getObjectNameForConsumer(CamelContext context, Consumer consumer) throws MalformedObjectNameException;

    ObjectName getObjectNameForProducer(CamelContext context, Producer producer) throws MalformedObjectNameException;

    ObjectName getObjectNameForTracer(CamelContext context, Service tracer) throws MalformedObjectNameException;

    ObjectName getObjectNameForService(CamelContext context, Service service) throws MalformedObjectNameException;

    ObjectName getObjectNameForClusterService(CamelContext context, CamelClusterService service)
            throws MalformedObjectNameException;

    ObjectName getObjectNameForThreadPool(CamelContext context, ThreadPoolExecutor threadPool, String id, String sourceId)
            throws MalformedObjectNameException;

    default @Nullable ObjectName getObjectNameForThreadPool(
            CamelContext context, ExecutorService executorService, String id, String sourceId)
            throws MalformedObjectNameException {
        return null;
    }

    ObjectName getObjectNameForEventNotifier(CamelContext context, EventNotifier eventNotifier)
            throws MalformedObjectNameException;
}
