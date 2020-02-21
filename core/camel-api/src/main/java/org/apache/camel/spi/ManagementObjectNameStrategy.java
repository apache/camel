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

import java.util.concurrent.ThreadPoolExecutor;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.cluster.CamelClusterService;

/**
 * Strategy for computing {@link ObjectName} names for the various beans that Camel register for management.
 */
public interface ManagementObjectNameStrategy {

    ObjectName getObjectName(Object managedObject) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelContext(String managementName, String name) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelHealth(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectNameForCamelContext(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectNameForRouteController(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectNameForComponent(Component component, String name) throws MalformedObjectNameException;

    ObjectName getObjectNameForEndpoint(Endpoint endpoint) throws MalformedObjectNameException;

    ObjectName getObjectNameForDataFormat(CamelContext context, DataFormat endpoint) throws MalformedObjectNameException;

    ObjectName getObjectNameForErrorHandler(Route route, Processor errorHandler, ErrorHandlerFactory builder) throws MalformedObjectNameException;

    ObjectName getObjectNameForProcessor(CamelContext context, Processor processor, NamedNode definition) throws MalformedObjectNameException;

    ObjectName getObjectNameForStep(CamelContext context, Processor processor, NamedNode definition) throws MalformedObjectNameException;

    ObjectName getObjectNameForRoute(Route route) throws MalformedObjectNameException;

    ObjectName getObjectNameForConsumer(CamelContext context, Consumer consumer) throws MalformedObjectNameException;

    ObjectName getObjectNameForProducer(CamelContext context, Producer producer) throws MalformedObjectNameException;

    ObjectName getObjectNameForTracer(CamelContext context, Service tracer) throws MalformedObjectNameException;

    ObjectName getObjectNameForService(CamelContext context, Service service) throws MalformedObjectNameException;

    ObjectName getObjectNameForClusterService(CamelContext context, CamelClusterService service) throws MalformedObjectNameException;

    ObjectName getObjectNameForThreadPool(CamelContext context, ThreadPoolExecutor threadPool, String id, String sourceId) throws MalformedObjectNameException;

    ObjectName getObjectNameForEventNotifier(CamelContext context, EventNotifier eventNotifier) throws MalformedObjectNameException;
}
