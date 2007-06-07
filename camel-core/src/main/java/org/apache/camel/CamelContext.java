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
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.spi.Injector;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Interface used to represent the context used to configure routes and the
 * policies to use during message exchanges between endpoints.
 *
 * @version $Revision$
 */
public interface CamelContext extends Service {

    // Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a component to the context.
     */
    void addComponent(String componentName, Component component);

    /**
     * Gets a component from the context by name.
     */
    Component getComponent(String componentName);

    /**
     * Gets a component from the context by name and specifying the expected type of component.
     */
    <T extends Component> T getComponent(String name, Class<T> componentType);

    /**
     * Removes a previously added component.
     *
     * @param componentName
     * @return the previously added component or null if it had not been previously added.
     */
    Component removeComponent(String componentName);

    /**
     * Gets the a previously added component by name or lazily creates the component
     * using the factory Callback.
     *
     * @param componentName the name of the component
     * @param factory       used to create a new component instance if the component was not previously added.
     * @return
     */
    Component getOrCreateComponent(String componentName, Callable<Component> factory);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an {@see Endpoint}.  If the URI has a singleton endpoint
     * registered, then the singleton is returned.  Otherwise, a new {@see Endpoint} is created
     * and if the endpoint is a singleton it is registered as a singleton endpoint.
     */
    Endpoint getEndpoint(String uri);

    /**
     * Resolves the given URI to an {@see Endpoint} of the specified type.
     * If the URI has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@see Endpoint} is created and if the endpoint is a
     * singleton it is registered as a singleton endpoint.
     */
    <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType);

    /**
     * Returns the collection of all registered singleton endpoints.
     */
    Collection<Endpoint> getSingletonEndpoints();

    /**
     * Adds the endpoint to the context using the given URI.  The endpoint will be registered as a singleton.
     *
     * @param uri the URI to be used to resolve this endpoint
     * @param endpoint the endpoint to be added to the context
     * @return the old endpoint that was previously registered to the context if there was
     * already an endpoint for that URI
     * @throws Exception if the new endpoint could not be started or the old endpoint could not be stopped
     */
    Endpoint addSingletonEndpoint(String uri, Endpoint endpoint) throws Exception;

    /**
     * Removes the singleton endpoint with the given URI
     *
     * @param uri the URI to be used to remove
     * @return the endpoint that was removed or null if there is no endpoint for this URI
     * @throws Exception if endpoint could not be stopped
     */
    Endpoint removeSingletonEndpoint(String uri) throws Exception;


    // Route Management Methods
    //-----------------------------------------------------------------------

    /**
     * Returns the current routes in this context
     *
     * @return the current routes in this context
     */
    List<Route> getRoutes();

    /**
     * Sets the routes for this context, replacing any current routes
     *
     * @param routes the new routes to use
     */
    void setRoutes(List<Route> routes);

    /**
     * Adds a collection of routes to this context
     *
     * @param routes the routes to add
     */
    void addRoutes(Collection<Route> routes) throws Exception;

    /**
     * Adds a collection of routes to this context using the given builder
     * to build them
     *
     * @param builder the builder which will create the routes and add them to this context
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(RouteBuilder builder) throws Exception;

    // Properties
    //-----------------------------------------------------------------------

    /**
     * Returns the converter of exchanges from one type to another
     * @return
     */
    ExchangeConverter getExchangeConverter();

    /**
     * Returns the type converter used to coerce types from one type to another
     */
    TypeConverter getTypeConverter();

    /**
     * Returns the injector used to instantiate objects by type
     */
    Injector getInjector();

}
