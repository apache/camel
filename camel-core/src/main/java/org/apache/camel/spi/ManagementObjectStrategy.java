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
package org.apache.camel.spi;

import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.model.ProcessorDefinition;

/**
 * Strategy for creating the managed object for the various beans Camel register for management.
 */
public interface ManagementObjectStrategy {
    
    Object getManagedObjectForCamelContext(CamelContext context);

    Object getManagedObjectForCamelHealth(CamelContext context);

    Object getManagedObjectForComponent(CamelContext context, Component component, String name);

    Object getManagedObjectForDataFormat(CamelContext context, DataFormat dataFormat);

    Object getManagedObjectForEndpoint(CamelContext context, Endpoint endpoint);

    Object getManagedObjectForErrorHandler(CamelContext context, RouteContext routeContext,
                                           Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder);

    Object getManagedObjectForRouteController(CamelContext context);

    Object getManagedObjectForRoute(CamelContext context, Route route);

    Object getManagedObjectForConsumer(CamelContext context, Consumer consumer);

    Object getManagedObjectForProducer(CamelContext context, Producer producer);

    Object getManagedObjectForProcessor(CamelContext context, Processor processor,
                                        ProcessorDefinition<?> definition, Route route);

    Object getManagedObjectForService(CamelContext context, Service service);

    Object getManagedObjectForClusterService(CamelContext context, CamelClusterService service);

    Object getManagedObjectForThreadPool(CamelContext context, ThreadPoolExecutor threadPool,
                                         String id, String sourceId, String routeId, String threadPoolProfileId);

    Object getManagedObjectForEventNotifier(CamelContext context, EventNotifier eventNotifier);
}
