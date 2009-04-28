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

public interface Route {

    String ID_PROPERTY = "id";
    String PARENT_PROPERTY = "parent";
    String GROUP_PROPERTY = "group";

    /**
     * Gets the inbound endpoint
     */
    Endpoint getEndpoint();

    /**
     * Sets the inbound endpoint
     *
     * @param endpoint the endpoint
     */
    void setEndpoint(Endpoint endpoint);

    /**
     * This property map is used to associate information about the route.
     *
     * @return properties
     */
    Map<String, Object> getProperties();

    /**
     * This property map is used to associate information about
     * the route. Gets all tbe services for this routes
     *
     * @return the services
     * @throws Exception is thrown in case of error
     */
    List<Service> getServicesForRoute() throws Exception;

    /**
     * Returns the additional services required for this particular route
     */
    List<Service> getServices();

    /**
     * Sets the sources for this route
     *
     * @param services the services
     */
    void setServices(List<Service> services);

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
