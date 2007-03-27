/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Interface used to represent the context used to configure routes and the
 * policies to use during message exchanges between endpoints.
 *
 * @version $Revision$
 */
public interface CamelContext {

    // Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a component to the container.
     */
    public void addComponent(String componentName, Component component);

    public Component getComponent(String componentName);

    /**
     * Removes a previously added component.
     *
     * @param componentName
     * @return the previously added component or null if it had not been previously added.
     */
    public Component removeComponent(String componentName);

    /**
     * Gets the a previously added component by name or lazily creates the component
     * using the factory Callback.
     *
     * @param componentName
     * @param factory       used to create a new component instance if the component was not previously added.
     * @return
     */
    public Component getOrCreateComponent(String componentName, Callable<Component> factory);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint resolveEndpoint(String uri);

    /**
     * Activates all the starting endpoints in that were added as routes.
     */
    public void activateEndpoints() throws Exception;

    /**
     * Deactivates all the starting endpoints in that were added as routes.
     */
    public void deactivateEndpoints() throws Exception;

    /**
     * Returns the collection of all active endpoints currently registered
     */
    Collection<Endpoint> getEndpoints();

    // Route Management Methods
    //-----------------------------------------------------------------------
    public List<Route> getRoutes();

    public void setRoutes(List<Route> routes);

    public void addRoutes(List<Route> routes);

    public void addRoutes(RouteBuilder builder) throws Exception;

    public void addRoutes(RouteFactory factory) throws Exception;

    // Properties
    //-----------------------------------------------------------------------
    public EndpointResolver getEndpointResolver();

    public ExchangeConverter getExchangeConverter();

    public TypeConverter getTypeConverter();
}
