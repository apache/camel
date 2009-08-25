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
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.TypeConverterRegistry;

/**
 * Interface used to represent the context used to configure routes and the
 * policies to use during message exchanges between endpoints.
 *
 * @version $Revision$
 */
public interface CamelContext extends Service, RuntimeConfiguration {

    /**
     * Gets the name of the this context.
     *
     * @return the name
     */
    String getName();

    // Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a service, starting it so that it will be stopped with this context
     *
     * @param object the service
     * @throws Exception can be thrown when starting the service
     */
    void addService(Object object) throws Exception;

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
     * Gets a readonly list of names of the components currently registered
     *
     * @return a readonly list with the names of the the components
     */
    List<String> getComponentNames();

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
     * Returns a new Map containing all of the active endpoints with the key of the map being their
     * unique key.
     *
     * @return map of active endpoints
     */
    Map<String, Endpoint> getEndpointMap();

    /**
     * Is the given endpoint already registered?
     *
     * @param uri  the URI of the endpoint
     * @return the registered endpoint or <tt>null</tt> if not registered
     */
    Endpoint hasEndpoint(String uri);

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
     * Registers a {@link org.apache.camel.spi.EndpointStrategy callback} to allow you to do custom
     * logic when an {@link Endpoint} is about to be registered to the {@link CamelContext} endpoint registry.
     * <p/>
     * When a callback is added it will be executed on the already registered endpoints allowing you to catch-up
     *
     * @param strategy  callback to be invoked
     */
    void addRegisterEndpointCallback(EndpointStrategy strategy);

    // Route Management Methods
    //-----------------------------------------------------------------------

    /**
     * Returns a list of the current route definitions
     *
     * @return list of the current route definitions
     */
    List<RouteDefinition> getRouteDefinitions();

    /**
     * Returns the current routes in this context
     *
     * @return the current routes
     */
    List<Route> getRoutes();

    /**
     * Adds a collection of routes to this context using the given builder
     * to build them
     *
     * @param builder the builder which will create the routes and add them to this context
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(RoutesBuilder builder) throws Exception;

    /**
     * Adds a collection of route definitions to the context
     *
     * @param routeDefinitions the route definitions to add
     * @throws Exception if the route definition could not be created for whatever reason
     */
    void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Removes a collection of route definitions from the context - stopping any previously running
     * routes if any of them are actively running
     *
     * @param routeDefinitions route definitions
     * @throws Exception if the route definition could not be removed for whatever reason
     */
    void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Starts the given route if it has been previously stopped
     *
     * @param route the route to start
     * @throws Exception is thrown if the route could not be started for whatever reason
     */
    void startRoute(RouteDefinition route) throws Exception;

    /**
     * Stops the given route. It will remain in the list of route definitions return by {@link #getRouteDefinitions()}
     * unless you use the {@link #removeRouteDefinitions(java.util.Collection)}
     *
     * @param route the route to stop
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     */
    void stopRoute(RouteDefinition route) throws Exception;


    // Properties
    //-----------------------------------------------------------------------

    /**
     * Returns the type converter used to coerce types from one type to another
     *
     * @return the converter
     */
    TypeConverter getTypeConverter();

    /**
     * Returns the type converter registry where type converters can be added or looked up
     *
     * @return the type converter registry
     */
    TypeConverterRegistry getTypeConverterRegistry();
    
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
     * Gets a readonly list with the names of the languages currently registered.
     *
     * @return a readonly list with the names of the the languages
     */
    List<String> getLanguageNames();

    /**
     * Creates a new ProducerTemplate.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a>
     *
     * @return the template
     */
    ProducerTemplate createProducerTemplate();

    /**
     * Creates a new ConsumerTemplate.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a> as it also applies for ConsumerTemplate.
     *
     * @return the template
     */
    ConsumerTemplate createConsumerTemplate();

    /**
     * Adds the given interceptor strategy
     *
     * @param interceptStrategy the strategy
     */
    void addInterceptStrategy(InterceptStrategy interceptStrategy);

    /**
     * Gets the interceptor strategies
     *
     * @return the list of current interceptor strategies
     */
    List<InterceptStrategy> getInterceptStrategies();

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
    void setDataFormats(Map<String, DataFormatDefinition> dataFormats);

    /**
     * Gets the data formats that can be referenced in the routes.
     *
     * @return the data formats available
     */
    Map<String, DataFormatDefinition> getDataFormats();
    
    /**
     * Sets the properties that can be referenced in the camel context
     *
     * @param properties properties
     */
    void setProperties(Map<String, String> properties);
    
    /**
     * Gets the properties that can be referenced in the camel context
     *
     * @return the properties
     */
    Map<String, String> getProperties();
    
    /**
     * Gets the default FactoryFinder which will be used for the loading the factory class from META-INF
     *
     * @return the default factory finder
     */
    FactoryFinder getDefaultFactoryFinder();

    /**
     * Sets the factory finder resolver to use.
     *
     * @param resolver the factory finder resolver
     */
    void setFactoryFinderResolver(FactoryFinderResolver resolver);
    
    /**
     * Gets the FactoryFinder which will be used for the loading the factory class from META-INF in the given path
     *
     * @param path the META-INF path
     * @return the factory finder
     * @throws NoFactoryAvailableException is thrown if a factory could not be found
     */
    FactoryFinder getFactoryFinder(String path) throws NoFactoryAvailableException;

    /**
     * Returns the current status of the given route
     *
     * @param route the route
     * @return the status for the route
     */
    ServiceStatus getRouteStatus(RouteDefinition route);

    /**
     * Returns the class resolver to be used for loading/lookup of classes.
     *
     * @return the resolver
     */
    ClassResolver getClassResolver();

    /**
     * Returns the package scanning class resolver
     *
     * @return the resolver
     */
    PackageScanClassResolver getPackageScanClassResolver();

    /**
     * Sets the class resolver to be use
     *
     * @param resolver the resolver
     */
    void setClassResolver(ClassResolver resolver);

    /**
     * Sets the package scanning class resolver to use
     *
     * @param resolver the resolver
     */
    void setPackageScanClassResolver(PackageScanClassResolver resolver);

    /**
     * Sets a pluggable service pool to use for {@link Producer} pooling.
     *
     * @param servicePool the pool
     */
    void setProducerServicePool(ServicePool<Endpoint, Producer> servicePool);

    /**
     * Gets the service pool for {@link Producer} pooling.
     *
     * @return the service pool
     */
    ServicePool<Endpoint, Producer> getProducerServicePool();

    /**
     * Uses a custom node id factory when generating auto assigned ids to the nodes in the route definitions
     *
     * @param factory  custom factory to use
     */
    void setNodeIdFactory(NodeIdFactory factory);

    /**
     * Gets the node id factory
     *
     * @return  the node id factory
     */
    NodeIdFactory getNodeIdFactory();

    /**
     * Gets the management strategy
     *
     * @return the management strategy
     */
    ManagementStrategy getManagementStrategy();

    /**
     * Sets the management strategy to use
     *
     * @param strategy the management strategy
     */
    void setManagementStrategy(ManagementStrategy strategy);

}
