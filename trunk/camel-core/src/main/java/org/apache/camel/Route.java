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
package org.apache.camel;

import java.util.List;
import java.util.Map;

import org.apache.camel.spi.RouteContext;

/**
 * A <a href="http://camel.apache.org/routes.html">Route</a>
 * defines the processing used on an inbound message exchange
 * from a specific {@link org.apache.camel.Endpoint} within a {@link org.apache.camel.CamelContext}
 */
public interface Route {

    String ID_PROPERTY = "id";
    String PARENT_PROPERTY = "parent";
    String GROUP_PROPERTY = "group";

    /**
     * Gets the route id
     *
     * @return the route id
     */
    String getId();

    /**
     * Gets the inbound endpoint
     */
    Endpoint getEndpoint();

    /**
     * Gets the inbound {@link Consumer}
     */
    Consumer getConsumer();

    /**
     * Whether or not the route supports suspension
     */
    boolean supportsSuspension();

    /**
     * This property map is used to associate information about the route.
     *
     * @return properties
     */
    Map<String, Object> getProperties();

    /**
     * Gets the route context
     *
     * @return the route context
     */
    RouteContext getRouteContext();

    /**
     * A strategy callback allowing special initialization when services is starting.
     *
     * @param services the service
     * @throws Exception is thrown in case of error
     */
    void onStartingServices(List<Service> services) throws Exception;

    /**
     * Returns the services for this particular route
     */
    List<Service> getServices();

    /**
     * Adds a service to this route
     *
     * @param service the service
     */
    void addService(Service service);

    /**
     * Returns a navigator to navigate this route by navigating all the {@link Processor}s.
     *
     * @return a navigator for {@link Processor}.
     */
    Navigate<Processor> navigate();

}
