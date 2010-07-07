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
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.ShutdownStrategy;
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

    /**
     * Gets the version of the this context.
     *
     * @return the version
     */
    String getVersion();

    /**
     * Get the status of this context
     *
     * @return the status
     */
    ServiceStatus getStatus();

    /**
     * Gets the uptime in a human readable format
     *
     * @return the uptime in days/hours/minutes
     */
    String getUptime();

    // Service Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a service, starting it so that it will be stopped with this context
     * <p/>
     * The added service will also be enlisted in JMX for management (if JMX is enabled)
     *
     * @param object the service
     * @throws Exception can be thrown when starting the service
     */
    void addService(Object object) throws Exception;

    /**
     * Has the given service already been added?
     *
     * @param object the service
     * @return <tt>true</tt> if already added, <tt>false</tt> if not.
     */
    boolean hasService(Object object);

    /**
     * Adds the given listener to be invoked when {@link CamelContext} have just been started.
     * <p/>
     * This allows listeners to do any custom work after the routes and other services have been started and are running.
     * <p/><b>Important:</b> The listener will always be invoked, also if the {@link CamelContext} has already been
     * started, see the {@link org.apache.camel.StartupListener#onCamelContextStarted(CamelContext, boolean)} method.
     *
     * @param listener the listener
     * @throws Exception can be thrown if {@link CamelContext} is already started and the listener is invoked
     *                   and cause an exception to be thrown
     */
    void addStartupListener(StartupListener listener) throws Exception;

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
     * Is the given component already registered?
     *
     * @param componentName the name of the component
     * @return the registered Component or <tt>null</tt> if not registered
     */
    Component hasComponent(String componentName);

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
     * @deprecated will be removed in Camel 2.5
     */
    @Deprecated
    Component removeComponent(String componentName);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type.
     * If the name has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@link Endpoint} is created and registered.
     *
     * @param uri  the URI of the endpoint
     * @return  the endpoint
     */
    Endpoint getEndpoint(String uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type.
     * If the name has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@link Endpoint} is created and registered.
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
     * Adds the endpoint to the context using the given URI.
     *
     * @param uri the URI to be used to resolve this endpoint
     * @param endpoint the endpoint to be added to the context
     * @return the old endpoint that was previously registered or <tt>null</tt> if none was registered
     * @throws Exception if the new endpoint could not be started or the old endpoint could not be stopped
     */
    Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception;

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
     * Gets the route definition with the given id
     *
     * @param id id of the route
     * @return the route definition or <tt>null</tt> if not found
     */
    RouteDefinition getRouteDefinition(String id);

    /**
     * Returns the current routes in this context
     *
     * @return the current routes
     */
    List<Route> getRoutes();

    /**
     * Gets the route with the given id
     *
     * @param id id of the route
     * @return the route or <tt>null</tt> if not found
     */
    Route getRoute(String id);

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
     * Starts the given route if it has been previously stopped
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be started for whatever reason
     */
    void startRoute(String routeId) throws Exception;

    /**
     * Stops the given route.
     * It will remain in the list of route definitions return by {@link #getRouteDefinitions()}
     * unless you use the {@link #removeRouteDefinitions(java.util.Collection)}
     *
     * @param route the route to stop
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     */
    void stopRoute(RouteDefinition route) throws Exception;

    /**
     * Stops the given route.
     * It will remain in the list of route definitions return by {@link #getRouteDefinitions()}
     * unless you use the {@link #removeRouteDefinitions(java.util.Collection)}
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     */
    void stopRoute(String routeId) throws Exception;

    /**
     * Shutdown the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     * It will remain in the list of route definitions return by {@link #getRouteDefinitions()}
     * unless you use the {@link #removeRouteDefinitions(java.util.Collection)}
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     */
    void shutdownRoute(String routeId) throws Exception;

    /**
     * Shutdown the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     * It will remain in the list of route definitions return by {@link #getRouteDefinitions()}
     * unless you use the {@link #removeRouteDefinitions(java.util.Collection)}
     *
     * @param routeId the route id
     * @param timeout   timeout
     * @param timeUnit  the unit to use
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     */
    void shutdownRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Returns the current status of the given route
     *
     * @param routeId the route id
     * @return the status for the route
     */
    ServiceStatus getRouteStatus(String routeId);

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
     * Returns the lifecycle strategies used to handle lifecycle notifications
     *
     * @return the lifecycle strategies
     */
    List<LifecycleStrategy> getLifecycleStrategies();

    /**
     * Adds the given lifecycle strategy to be used.
     *
     * @param lifecycleStrategy  the strategy
     */
    void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy);

    /**
     * Resolves a language for creating expressions
     *
     * @param language  name of the language
     * @return the resolved language
     */
    Language resolveLanguage(String language);

    /**
     * Parses the given text and resolve any property placeholders - using {{key}}.
     *
     * @param text the text such as an endpoint uri or the likes
     * @return the text with resolved property placeholders
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     */
    String resolvePropertyPlaceholders(String text) throws Exception;

    /**
     * Gets a readonly list with the names of the languages currently registered.
     *
     * @return a readonly list with the names of the the languages
     */
    List<String> getLanguageNames();

    /**
     * Creates a new {@link ProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a>
     * <p/>
     * Will use cache size defined in Camel property with key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}.
     * If no key was defined then it will fallback to a default size of 1000.
     * You can also use the {@link org.apache.camel.ProducerTemplate#setMaximumCacheSize(int)} method to use a custom value
     * before starting the template.
     *
     * @return the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ProducerTemplate createProducerTemplate();

    /**
     * Creates a new {@link ProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * You <b>must</b> start the template before its being used.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a>
     *
     * @param maximumCacheSize the maximum cache size
     * @return the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ProducerTemplate createProducerTemplate(int maximumCacheSize);

    /**
     * Creates a new {@link ConsumerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a> as it also applies for ConsumerTemplate.
     * <p/>
     * Will use cache size defined in Camel property with key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}.
     * If no key was defined then it will fallback to a default size of 1000.
     * You can also use the {@link org.apache.camel.ConsumerTemplate#setMaximumCacheSize(int)} method to use a custom value
     * before starting the template.
     *
     * @return the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ConsumerTemplate createConsumerTemplate();

    /**
     * Creates a new {@link ConsumerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a> as it also applies for ConsumerTemplate.
     *
     * @param maximumCacheSize the maximum cache size
     * @return the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ConsumerTemplate createConsumerTemplate(int maximumCacheSize);

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
     *
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
     * Resolve a data format given its name
     *
     * @param name the data format name or a reference to it in the {@link Registry}
     * @return the resolved data format, or <tt>null</tt> if not found
     */
    DataFormat resolveDataFormat(String name);

    /**
     * Resolve a data format definition given its name
     *
     * @param name the data format definition name or a reference to it in the {@link Registry}
     * @return the resolved data format definition, or <tt>null</tt> if not found
     */
    DataFormatDefinition resolveDataFormatDefinition(String name);

    /**
     * Gets the current data format resolver
     *
     * @return the resolver
     */
    DataFormatResolver getDataFormatResolver();

    /**
     * Sets a custom data format resolver
     *
     * @param dataFormatResolver  the resolver
     */
    void setDataFormatResolver(DataFormatResolver dataFormatResolver);

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

    /**
     * Gets the default tracer
     *
     * @return the default tracer
     */
    InterceptStrategy getDefaultTracer();

    /**
     * Sets a custom tracer to be used as the default tracer.
     * <p/>
     * <b>Note:</b> This must be set before any routes are created,
     * changing the defaultTracer for existing routes is not supported.
     *
     * @param tracer the custom tracer to use as default tracer
     */
    void setDefaultTracer(InterceptStrategy tracer);

    /**
     * Disables using JMX as {@link org.apache.camel.spi.ManagementStrategy}.
     */
    void disableJMX();

    /**
     * Gets the inflight repository
     *
     * @return the repository
     */
    InflightRepository getInflightRepository();

    /**
     * Sets a custom inflight repository to use
     *
     * @param repository the repository
     */
    void setInflightRepository(InflightRepository repository);
    
    /**
     * Gets the the application context class loader which may be helpful for running camel in other containers
     *
     * @return the application context class loader
     */
    ClassLoader getApplicationContextClassLoader();

    /**
     * Sets the application context class loader
     *
     * @param classLoader the class loader
     */
    void setApplicationContextClassLoader(ClassLoader classLoader);

    /**
     * Gets the current shutdown strategy
     *
     * @return the strategy
     */
    ShutdownStrategy getShutdownStrategy();

    /**
     * Sets a custom shutdown strategy
     *
     * @param shutdownStrategy the custom strategy
     */
    void setShutdownStrategy(ShutdownStrategy shutdownStrategy);

    /**
     * Gets the current {@link org.apache.camel.spi.ExecutorServiceStrategy}
     *
     * @return the strategy
     */
    ExecutorServiceStrategy getExecutorServiceStrategy();

    /**
     * Sets a custom {@link org.apache.camel.spi.ExecutorServiceStrategy}
     *
     * @param executorServiceStrategy the custom strategy
     */
    void setExecutorServiceStrategy(ExecutorServiceStrategy executorServiceStrategy);

    /**
     * Gets the current {@link org.apache.camel.spi.ProcessorFactory}
     *
     * @return the factory, can be <tt>null</tt> if no custom factory has been set
     */
    ProcessorFactory getProcessorFactory();

    /**
     * Sets a custom {@link org.apache.camel.spi.ProcessorFactory}
     *
     * @param processorFactory the custom factory
     */
    void setProcessorFactory(ProcessorFactory processorFactory);

}
