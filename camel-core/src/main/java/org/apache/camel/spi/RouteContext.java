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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Intercept;
import org.apache.camel.Processor;
import org.apache.camel.model.FromType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.dataformat.DataFormatType;

/**
 * The context used to activate new routing rules
 *
 * @version $Revision$
 */
public interface RouteContext {

    /**
     * Gets the endpoint
     *
     * @return the endpoint
     */
    Endpoint<? extends Exchange> getEndpoint();

    /**
     * Gets the from type
     *
     * @return the from type
     */
    FromType getFrom();

    /**
     * Get the route type
     *
     * @return the route type
     */
    RouteType getRoute();

    /**
     * Gets the camel context
     *
     * @return the camel context
     */
    CamelContext getCamelContext();

    /**
     * Creates a processor
     *
     * @param node  the node
     * @return the created processor
     * @throws Exception can be thrown
     */
    Processor createProcessor(ProcessorType node) throws Exception;

    /**
     * Resolves an endpoint from the URI
     *
     * @param uri the URI
     * @return the resolved endpoint
     */
    Endpoint<? extends Exchange> resolveEndpoint(String uri);

    /**
     * Resolves an endpoint from either a URI or a named reference
     *
     * @param uri  the URI or
     * @param ref  the named reference
     * @return the resolved endpoint
     */
    Endpoint<? extends Exchange> resolveEndpoint(String uri, String ref);

    /**
     * lookup an object by name and type
     *
     * @param name  the name to lookup
     * @param type  the expected type
     * @return the found object
     */
    <T> T lookup(String name, Class<T> type);

    /**
     * Lets complete the route creation, creating a single event driven route
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
     * Intercepts with the given interceptor
     *
     * @param interceptor the interceptor
     */
    void intercept(Intercept interceptor);

    /**
     * Creates a proceed processor
     *
     * @return the created proceed processor
     */
    Processor createProceedProcessor();

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
     * This method retrieves the ErrorHandlerWrappingStrategy.
     *
     * @return the strategy
     */
    ErrorHandlerWrappingStrategy getErrorHandlerWrappingStrategy();
    
    /**
     * This method sets the ErrorHandlerWrappingStrategy.
     *
     * @param strategy the strategy
     */
    void setErrorHandlerWrappingStrategy(ErrorHandlerWrappingStrategy strategy);

    /**
     * If this flag is true, {@link ProcessorType#addRoutes(RouteContext, java.util.Collection)}
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
     * Get a DataFormatType by ref name
     *
     * @param ref  the ref name to lookup
     * @return the found object
     */
    DataFormatType getDataFormat(String ref);
}
