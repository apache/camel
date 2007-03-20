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

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.builder.RouteBuilder;

/**
 * Interface used to represent the context used to configure routes and the 
 * policies to use during message exchanges between endpoints.
 *
 * @version $Revision$
 */
public interface CamelContext<E extends Exchange> {
    
	// Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a component to the container.
     */
    public void addComponent(String componentName, final Component<E> component);

    public Component getComponent(String componentName);
    
    /**
     * Removes a previously added component.
     * @param componentName
     * @return the previously added component or null if it had not been previously added.
     */
    public Component removeComponent(String componentName);

    /**
     * Gets the a previously added component by name or lazily creates the component
     * using the factory Callback. 
     * 
     * @param componentName
     * @param factory used to create a new component instance if the component was not previously added.
     * @return
     */
    public Component getOrCreateComponent(String componentName, Callable<Component<E>> factory);
    
    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an endpoint
     */
    public Endpoint<E> resolveEndpoint(String uri);
    
    /**
     * Activates all the starting endpoints in that were added as routes.
     */
    public void activateEndpoints();
    
    /**
     * Deactivates all the starting endpoints in that were added as routes.
     */
    public void deactivateEndpoints() ;

    // Route Management Methods
    //-----------------------------------------------------------------------
	public Map<Endpoint<E>, Processor<E>> getRoutes() ;
	
	public void setRoutes(Map<Endpoint<E>, Processor<E>> routes);
	
    public void setRoutes(RouteBuilder<E> builder);

    public void setRoutes(final RouteFactory factory);

    // Properties
    //-----------------------------------------------------------------------
    public EndpointResolver<E> getEndpointResolver();
    
    public ExchangeConverter getExchangeConverter();

    public TypeConverter getTypeConverter();


}
