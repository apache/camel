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
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.ExchangeConverter;

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
     * Adds a component to the container.
     */
    void addComponent(String componentName, Component component);

    /**
     * Gets a component from the container by name.
     */
    Component getComponent(String componentName);

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
     * @param componentName
     * @param factory       used to create a new component instance if the component was not previously added.
     * @return
     */
    Component getOrCreateComponent(String componentName, Callable<Component> factory);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an endpoint
     */
    Endpoint resolveEndpoint(String uri);

    /**
     * Returns the collection of all active endpoints currently registered
     */
    Collection<Endpoint> getEndpoints();


    // Route Management Methods
    //-----------------------------------------------------------------------
    List<Route> getRoutes();

    void setRoutes(List<Route> routes);

    void addRoutes(List<Route> routes);

    void addRoutes(RouteBuilder builder) throws Exception;

    void addRoutes(RouteFactory factory) throws Exception;

    
    // Properties
    //-----------------------------------------------------------------------
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
