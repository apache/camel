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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.RouteType;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.spi.ExchangeConverter;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.FactoryFinder;

/**
 * Interface used to represent the context used to configure routes and the
 * policies to use during message exchanges between endpoints.
 *
 * @version $Revision$
 */
public interface CamelContext extends Service {

    /**
     * Gets the name of the this context.
     *
     * @return the name
     */
    String getName();

    // Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a component to the context.
     *
     * @param componentName  the name the component is registered as
     * @param component      the component
     */
    void addComponent(String componentName, Component component);

    /**
     * Gets a component from the context by name.
     *
     * @param componentName the name of the component
     * @return the component
     */
    Component getComponent(String componentName);

    /**
     * Gets a component from the context by name and specifying the expected type of component.
     *
     * @param name  the name to lookup
     * @param componentType  the expected type
     * @return the component
     */
    <T extends Component> T getComponent(String name, Class<T> componentType);

    /**
     * Removes a previously added component.
     *
     * @param componentName the component name to remove
     * @return the previously added component or null if it had not been previously added.
     */
    Component removeComponent(String componentName);

    /**
     * Gets the a previously added component by name or lazily creates the component
     * using the factory Callback.
     *
     * @param componentName the name of the component
     * @param factory       used to create a new component instance if the component was not previously added.
     * @return the component
     */
    Component getOrCreateComponent(String componentName, Callable<Component> factory);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given URI to an {@link Endpoint}.  If the URI has a singleton endpoint
     * registered, then the singleton is returned.  Otherwise, a new {@link Endpoint} is created
     * and if the endpoint is a singleton it is registered as a singleton endpoint.
     *
     * @param uri  the URI of the endpoint
     * @return  the endpoint
     */
    Endpoint getEndpoint(String uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type.
     * If the name has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@link Endpoint} is created and if the endpoint is a
     * singleton it is registered as a singleton endpoint.
     *
     * @param name  the name of the endpoint
     * @param endpointType  the expected type
     * @return the endpoint
     */
    <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType);

    /**
     * Returns the collection of all registered endpoints.
     *
     * @return  all endpoints
     */
    Collection<Endpoint> getEndpoints();

    /**
     * Returns the collection of all registered endpoints for a uri or an empty collection.
     * For a singleton endpoint the collection will contain exactly one element.
     *
     * @param uri  the URI of the endpoints
     * @return  collection of endpoints
     */
    Collection<Endpoint> getEndpoints(String uri);

    /**
     * Returns the collection of all registered singleton endpoints.
     *
     * @return  all the singleton endpoints
     */
    Collection<Endpoint> getSingletonEndpoints();

    /**
     * Adds the endpoint to the context using the given URI.
     *
     * @param uri the URI to be used to resolve this endpoint
     * @param endpoint the endpoint to be added to the context
     * @return the old endpoint that was previously registered to the context if 
     * there was already an singleton endpoint for that URI or null
     * @throws Exception if the new endpoint could not be started or the old 
     * singleton endpoint could not be stopped
     */
    Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception;

    /**
     * Removes all endpoints with the given URI
     *
     * @param uri the URI to be used to remove
     * @return a collection of endpoints removed or null if there are no endpoints for this URI
     * @throws Exception if at least one endpoint could not be stopped
     */
    Collection<Endpoint> removeEndpoints(String uri) throws Exception;

    /**
     * Adds the endpoint to the context using the given URI.  The endpoint will be registered as a singleton.
     *
     * @param uri the URI to be used to resolve this endpoint
     * @param endpoint the endpoint to be added to the context
     * @return the old endpoint that was previously registered to the context if there was
     * already an endpoint for that URI
     * @throws Exception if the new endpoint could not be started or the old endpoint could not be stopped
     */
    @Deprecated
    Endpoint addSingletonEndpoint(String uri, Endpoint endpoint) throws Exception;

    /**
     * Removes the singleton endpoint with the given URI
     *
     * @param uri the URI to be used to remove
     * @return the endpoint that was removed or null if there is no endpoint for this URI
     * @throws Exception if endpoint could not be stopped
     */
    @Deprecated
    Endpoint removeSingletonEndpoint(String uri) throws Exception;


    // Route Management Methods
    //-----------------------------------------------------------------------

    /**
     * Returns a list of the current route definitions
     *
     * @return list of the current route definitions
     */
    List<RouteType> getRouteDefinitions();

    /**
     * Returns the current routes in this context
     *
     * @return the current routes
     */
    List<Route> getRoutes();

    /**
     * Sets the routes for this context, replacing any current routes
     *
     * @param routes the new routes to use
     * @deprecated is considered for deprecation, use addRoutes instead, could be removed in Camel 2.0
     */
    @Deprecated
    void setRoutes(List<Route> routes);

    /**
     * Adds a collection of routes to this context
     *
     * @param routes the routes to add
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(Collection<Route> routes) throws Exception;

    /**
     * Adds a collection of routes to this context using the given builder
     * to build them
     *
     * @param builder the builder which will create the routes and add them to this context
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(Routes builder) throws Exception;

    /**
     * Adds a collection of route definitions to the context
     *
     * @param routeDefinitions the route definitions to add
     * @throws Exception if the route definition could not be created for whatever reason
     */
    void addRouteDefinitions(Collection<RouteType> routeDefinitions) throws Exception;


    // Properties
    //-----------------------------------------------------------------------

    /**
     * Returns the converter of exchanges from one type to another
     *
     * @return the converter
     */
    ExchangeConverter getExchangeConverter();

    /**
     * Returns the type converter used to coerce types from one type to another
     *
     * @return the converter
     */
    TypeConverter getTypeConverter();

    /**
     * Returns the registry used to lookup components by name and type such as the Spring ApplicationContext,
     * JNDI or the OSGi Service Registry
     *
     * @return the registry
     */
    Registry getRegistry();

    /**
     * Returns the injector used to instantiate objects by type
     *
     * @return the injector
     */
    Injector getInjector();

    /**
     * Returns the lifecycle strategy used to handle lifecycle notification
     *
     * @return the lifecycle strategy
     */
    LifecycleStrategy getLifecycleStrategy();

    /**
     * Resolves a language for creating expressions
     *
     * @param language  name of the language
     * @return the resolved language
     */
    Language resolveLanguage(String language);

    /**
     * Creates a new ProducerTemplate.
     * <p/>
     * See this FAQ before use: <a href="http://activemq.apache.org/camel/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a>
     *
     * @return the template
     */
    <E extends Exchange> ProducerTemplate<E> createProducerTemplate();

    /**
     * Adds the given interceptor strategy
     *
     * @param interceptStrategy the strategy
     */
    void addInterceptStrategy(InterceptStrategy interceptStrategy);

    /**
     * Gets the default error handler builder which is inherited by the routes
     *
     * @return the builder
     */
    ErrorHandlerBuilder getErrorHandlerBuilder();

    /**
     * Sets the default error handler builder which is inherited by the routes
     *
     * @param errorHandlerBuilder  the builder
     */
    void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder);

    /**
     * Sets the data formats that can be referenced in the routes.
     * @param dataFormats the data formats
     */
    void setDataFormats(Map<String, DataFormatType> dataFormats);

    /**
     * Gets the data formats that can be referenced in the routes.
     *
     * @return the data formats available
     */
    Map<String, DataFormatType> getDataFormats();
    
    /**
     * Create a FactoryFinder which will be used for the loading the factory class from META-INF
     * @return the factory finder
     */
    FactoryFinder createFactoryFinder();
    
    /**
     * Create a FactoryFinder which will be used for the loading the factory class from META-INF
     * @param path the META-INF path
     * @return the factory finder
     */
    FactoryFinder createFactoryFinder(String path);
}
