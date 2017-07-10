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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Experimental;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeConfiguration;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

/**
 * The context used to activate new routing rules
 *
 * @version 
 */
public interface RouteContext extends RuntimeConfiguration, EndpointAware {

    /**
     * Gets the from type
     *
     * @return the from type
     */
    FromDefinition getFrom();

    /**
     * Get the route type
     *
     * @return the route type
     */
    RouteDefinition getRoute();

    /**
     * Gets the camel context
     *
     * @return the camel context
     */
    CamelContext getCamelContext();

    /**
     * Resolves an endpoint from the URI
     *
     * @param uri the URI
     * @return the resolved endpoint
     */
    Endpoint resolveEndpoint(String uri);

    /**
     * Resolves an endpoint from either a URI or a named reference
     *
     * @param uri  the URI or
     * @param ref  the named reference
     * @return the resolved endpoint
     */
    Endpoint resolveEndpoint(String uri, String ref);

    /**
     * lookup an object by name and type
     *
     * @param name  the name to lookup
     * @param type  the expected type
     * @return the found object
     */
    <T> T lookup(String name, Class<T> type);

    /**
     * lookup an object by name and type or throws {@link org.apache.camel.NoSuchBeanException} if not found.
     *
     * @param name  the name to lookup
     * @param type  the expected type
     * @return the found object
     */
    <T> T mandatoryLookup(String name, Class<T> type);

    /**
     * lookup objects by type
     *
     * @param type the expected type
     * @return the found objects with the name as the key in the map. Returns an empty map if none found.
     */
    <T> Map<String, T> lookupByType(Class<T> type);

    /**
     * For completing the route creation, creating a single event driven route
     * for the current from endpoint with any processors required
     */
    void commit();

    /**
     * Adds an event driven processor
     *
     * @param processor the processor
     */
    void addEventDrivenProcessor(Processor processor);

    /**
     * This method retrieves the InterceptStrategy instances this route context.
     *
     * @return the strategy
     */
    List<InterceptStrategy> getInterceptStrategies();

    /**
     * This method sets the InterceptStrategy instances on this route context.
     *
     * @param interceptStrategies the strategies
     */
    void setInterceptStrategies(List<InterceptStrategy> interceptStrategies);

    /**
     * Adds a InterceptStrategy to this route context
     *
     * @param interceptStrategy the strategy
     */
    void addInterceptStrategy(InterceptStrategy interceptStrategy);

    /**
     * Sets a special intercept strategy for management.
     * <p/>
     * Is by default used to correlate managed performance counters with processors
     * when the runtime route is being constructed
     *
     * @param interceptStrategy the managed intercept strategy
     */
    void setManagedInterceptStrategy(InterceptStrategy interceptStrategy);

    /**
     * Gets the special managed intercept strategy if any
     *
     * @return the managed intercept strategy, or <tt>null</tt> if not managed
     */
    InterceptStrategy getManagedInterceptStrategy();

    /**
     * If this flag is true, {@link ProcessorDefinition#addRoutes(RouteContext, java.util.Collection)}
     * will not add processor to addEventDrivenProcessor to the RouteContext and it
     * will prevent from adding an EventDrivenRoute.
     *
     * @param value the flag
     */
    void setIsRouteAdded(boolean value);

    /**
     * Returns the isRouteAdded flag
     * 
     * @return the flag
     */
    boolean isRouteAdded();
    
    /**
     * Gets the route policy List
     *
     * @return the route policy list if any
     */
    List<RoutePolicy> getRoutePolicyList();

    /**
     * Sets a custom route policy List
     *
     * @param routePolicyList the custom route policy list
     */
    void setRoutePolicyList(List<RoutePolicy> routePolicyList);

    /**
     * A private counter that increments, is used to as book keeping
     * when building a route based on the model
     * <p/>
     * We need this special book keeping be able to assign the correct
     * {@link org.apache.camel.model.ProcessorDefinition} to the {@link org.apache.camel.Channel}
     *
     * @param node the current node
     * @return the current count
     */
    int getAndIncrement(ProcessorDefinition<?> node);

    /**
     * Gets the last error.
     *
     * @return the error
     */
    default RouteError getLastError() {
        return null;
    }

    /**
     * Sets the last error.
     *
     * @param error the error
     */
    default void setLastError(RouteError error) {
    }

    /**
     * Gets the  {@link RouteController} for this route.
     *
     * @return the route controller,
     */
    @Experimental
    default RouteController getRouteController() {
        return null;
    }

    /**
     * Sets the {@link RouteController} for this route.
     *
     * @param controller the RouteController
     */
    @Experimental
    default void setRouteController(RouteController controller) {
    }
}
