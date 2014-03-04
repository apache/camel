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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.LoadPropertiesException;

/**
 * Interface used to represent the context used to configure routes and the
 * policies to use during message exchanges between endpoints.
 * <p/>
 * The context offers the following methods to control the lifecycle:
 * <ul>
 *   <li>{@link #start()}  - to start (<b>important:</b> the start method is not blocked, see more details
 *     <a href="http://camel.apache.org/running-camel-standalone-and-have-it-keep-running.html">here</a>)</li>
 *   <li>{@link #stop()} - to shutdown (will stop all routes/components/endpoints etc and clear internal state/cache)</li>
 *   <li>{@link #suspend()} - to pause routing messages</li>
 *   <li>{@link #resume()} - to resume after a suspend</li>
 * </ul>
 * <p/>
 * <b>Notice:</b> {@link #stop()} and {@link #suspend()} will gracefully stop/suspend routes ensuring any messages
 * in progress will be given time to complete. See more details at {@link org.apache.camel.spi.ShutdownStrategy}.
 * <p/>
 * If you are doing a hot restart then it's advised to use the suspend/resume methods which ensure a faster
 * restart but also allows any internal state to be kept as is.
 * The stop/start approach will do a <i>cold</i> restart of Camel, where all internal state is reset.
 * <p/>
 * End users are advised to use suspend/resume. Using stop is for shutting down Camel and it's not guaranteed that
 * when it's being started again using the start method that Camel will operate consistently.
 *
 * @version 
 */
public interface CamelContext extends SuspendableService, RuntimeConfiguration {

    /**
     * Starts the {@link CamelContext} (<b>important:</b> the start method is not blocked, see more details
     *     <a href="http://camel.apache.org/running-camel-standalone-and-have-it-keep-running.html">here</a>)</li>.
     * <p/>
     * See more details at the class-level javadoc of this class.
     *
     * @throws Exception is thrown if starting failed
     */
    void start() throws Exception;

    /**
     * Stop and shutdown the {@link CamelContext} (will stop all routes/components/endpoints etc and clear internal state/cache).
     * <p/>
     * See more details at the class-level javadoc of this class.
     *
     * @throws Exception is thrown if stopping failed
     */
    void stop() throws Exception;

    /**
     * Gets the name (id) of the this context.
     *
     * @return the name
     */
    String getName();

    /**
     * Gets the current name strategy
     *
     * @return name strategy
     */
    CamelContextNameStrategy getNameStrategy();

    /**
     * Sets a custom name strategy
     *
     * @param nameStrategy name strategy
     */
    void setNameStrategy(CamelContextNameStrategy nameStrategy);

    /**
     * Gets the current management name strategy
     *
     * @return management name strategy
     */
    ManagementNameStrategy getManagementNameStrategy();

    /**
     * Sets a custom management name strategy
     *
     * @param nameStrategy name strategy
     */
    void setManagementNameStrategy(ManagementNameStrategy nameStrategy);

    /**
     * Gets the name this {@link CamelContext} was registered in JMX.
     * <p/>
     * The reason that a {@link CamelContext} can have a different name in JMX is the fact to remedy for name clash
     * in JMX when having multiple {@link CamelContext}s in the same JVM. Camel will automatic reassign and use
     * a free name to avoid failing to start.
     *
     * @return the management name
     */
    String getManagementName();

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
     * Adds a service to this context, which allows this context to control the lifecycle, ensuring
     * the service is stopped when the context stops.
     * <p/>
     * The service will also have {@link CamelContext} injected if its {@link CamelContextAware}.
     * The service will also be enlisted in JMX for management (if JMX is enabled).
     * The service will be started, if its not already started.
     *
     * @param object the service
     * @throws Exception can be thrown when starting the service
     */
    void addService(Object object) throws Exception;

    /**
     * Removes a service from this context.
     * <p/>
     * The service is assumed to have been previously added using {@link #addService(Object)} method.
     * This method will <b>not</b> change the service lifecycle.
     *
     * @param object the service
     * @throws Exception can be thrown if error removing the service
     * @return <tt>true</tt> if the service was removed, <tt>false</tt> if no service existed
     */
    boolean removeService(Object object) throws Exception;

    /**
     * Has the given service already been added to this context?
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
     * @param componentName the name the component is registered as
     * @param component     the component
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
     * Gets a component from the context by name.
     *
     * @param name                 the name of the component
     * @param autoCreateComponents whether or not the component should
     *                             be lazily created if it does not already exist
     * @return the component
     */
    Component getComponent(String name, boolean autoCreateComponents);

    /**
     * Gets a component from the context by name and specifying the expected type of component.
     *
     * @param name          the name to lookup
     * @param componentType the expected type
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
     * <p/>
     * The component being removed will be stopped first.
     *
     * @param componentName the component name to remove
     * @return the previously added component or null if it had not been previously added.
     */
    Component removeComponent(String componentName);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type.
     * If the name has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@link Endpoint} is created and registered.
     *
     * @param uri the URI of the endpoint
     * @return the endpoint
     */
    Endpoint getEndpoint(String uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type.
     * If the name has a singleton endpoint registered, then the singleton is returned.
     * Otherwise, a new {@link Endpoint} is created and registered.
     *
     * @param name         the name of the endpoint
     * @param endpointType the expected type
     * @return the endpoint
     */
    <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType);

    /**
     * Returns the collection of all registered endpoints.
     *
     * @return all endpoints
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
     * @param uri the URI of the endpoint
     * @return the registered endpoint or <tt>null</tt> if not registered
     */
    Endpoint hasEndpoint(String uri);

    /**
     * Adds the endpoint to the context using the given URI.
     *
     * @param uri      the URI to be used to resolve this endpoint
     * @param endpoint the endpoint to be added to the context
     * @return the old endpoint that was previously registered or <tt>null</tt> if none was registered
     * @throws Exception if the new endpoint could not be started or the old endpoint could not be stopped
     */
    Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception;

    /**
     * Removes all endpoints with the given URI.
     * <p/>
     * The endpoints being removed will be stopped first.
     *
     * @param pattern an uri or pattern to match
     * @return a collection of endpoints removed which could be empty if there are no endpoints found for the given <tt>pattern</tt>
     * @throws Exception if at least one endpoint could not be stopped
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(CamelContext, String, String)  for pattern
     */
    Collection<Endpoint> removeEndpoints(String pattern) throws Exception;

    /**
     * Registers a {@link org.apache.camel.spi.EndpointStrategy callback} to allow you to do custom
     * logic when an {@link Endpoint} is about to be registered to the {@link CamelContext} endpoint registry.
     * <p/>
     * When a callback is added it will be executed on the already registered endpoints allowing you to catch-up
     *
     * @param strategy callback to be invoked
     */
    void addRegisterEndpointCallback(EndpointStrategy strategy);

    // Route Management Methods
    //-----------------------------------------------------------------------

    /**
     * Returns a list of the current route definitions
     *
     * @return list of the current route definitions
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#getRouteDefinitions()}
     */
    @Deprecated
    List<RouteDefinition> getRouteDefinitions();

    /**
     * Gets the route definition with the given id
     *
     * @param id id of the route
     * @return the route definition or <tt>null</tt> if not found
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#getRouteDefinition(String)}
     */
    @Deprecated
    RouteDefinition getRouteDefinition(String id);

    /**
     * Returns the order in which the route inputs was started.
     * <p/>
     * The order may not be according to the startupOrder defined on the route.
     * For example a route could be started manually later, or new routes added at runtime.
     *
     * @return a list in the order how routes was started
     */
    List<RouteStartupOrder> getRouteStartupOrder();

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
     * to build them.
     * <p/>
     * <b>Important:</b> The added routes will <b>only</b> be started, if {@link CamelContext}
     * is already started. You may want to check the state of {@link CamelContext} before
     * adding the routes, using the {@link org.apache.camel.CamelContext#getStatus()} method.
     * <p/>
     * <b>Important: </b> Each route in the same {@link org.apache.camel.CamelContext} must have an <b>unique</b> route id.
     * If you use the API from {@link org.apache.camel.CamelContext} or {@link org.apache.camel.model.ModelCamelContext} to add routes, then any
     * new routes which has a route id that matches an old route, then the old route is replaced by the new route.
     *
     * @param builder the builder which will create the routes and add them to this context
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(RoutesBuilder builder) throws Exception;

    /**
     * Loads a collection of route definitions from the given {@link java.io.InputStream}.
     *
     * @param is input stream with the route(s) definition to add
     * @throws Exception if the route definitions could not be loaded for whatever reason
     * @return the route definitions
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#loadRoutesDefinition(java.io.InputStream)}
     */
    @Deprecated
    RoutesDefinition loadRoutesDefinition(InputStream is) throws Exception;

    /**
     * Adds a collection of route definitions to the context
     *
     * @param routeDefinitions the route(s) definition to add
     * @throws Exception if the route definitions could not be created for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#addRouteDefinitions(java.util.Collection)}
     */
    @Deprecated
    void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Add a route definition to the context
     *
     * @param routeDefinition the route definition to add
     * @throws Exception if the route definition could not be created for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#addRouteDefinition(org.apache.camel.model.RouteDefinition)}
     */
    @Deprecated
    void addRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Removes a collection of route definitions from the context - stopping any previously running
     * routes if any of them are actively running
     *
     * @param routeDefinitions route(s) definitions to remove
     * @throws Exception if the route definitions could not be removed for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#removeRouteDefinitions(java.util.Collection)}
     */
    @Deprecated
    void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Removes a route definition from the context - stopping any previously running
     * routes if any of them are actively running
     *
     * @param routeDefinition route definition to remove
     * @throws Exception if the route definition could not be removed for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#removeRouteDefinition(org.apache.camel.model.RouteDefinition)}
     */
    @Deprecated
    void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Starts the given route if it has been previously stopped
     *
     * @param route the route to start
     * @throws Exception is thrown if the route could not be started for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#startRoute(org.apache.camel.model.RouteDefinition)}
     */
    @Deprecated
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
     *
     * @param route the route to stop
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#stopRoute(org.apache.camel.model.RouteDefinition)}
     */
    @Deprecated
    void stopRoute(RouteDefinition route) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see #suspendRoute(String)
     */
    void stopRoute(String routeId) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     *
     * @param routeId the route id
     * @param timeout  timeout
     * @param timeUnit the unit to use
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see #suspendRoute(String, long, java.util.concurrent.TimeUnit)
     */
    void stopRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Stops the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout 
     * and optional abortAfterTimeout mode.
     *
     * @param routeId the route id
     * @param timeout  timeout
     * @param timeUnit the unit to use
     * @param abortAfterTimeout should abort shutdown after timeout
     * @return <tt>true</tt> if the route is stopped before the timeout
     * @throws Exception is thrown if the route could not be stopped for whatever reason
     * @see #suspendRoute(String, long, java.util.concurrent.TimeUnit)
     */
    boolean stopRoute(String routeId, long timeout, TimeUnit timeUnit, boolean abortAfterTimeout) throws Exception;
    
    /**
     * Shutdown and <b>removes</b> the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     * @deprecated use {@link #stopRoute(String)} and {@link #removeRoute(String)}
     */
    @Deprecated
    void shutdownRoute(String routeId) throws Exception;

    /**
     * Shutdown and <b>removes</b> the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     *
     * @param routeId  the route id
     * @param timeout  timeout
     * @param timeUnit the unit to use
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     * @deprecated use {@link #stopRoute(String, long, java.util.concurrent.TimeUnit)} and {@link #removeRoute(String)}
     */
    @Deprecated
    void shutdownRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Removes the given route (the route <b>must</b> be stopped before it can be removed).
     * <p/>
     * <br/>A route which is removed will be unregistered from JMX, have its services stopped/shutdown and the route
     * definition etc. will also be removed. All the resources related to the route will be stopped and cleared.
     * <p/>
     * <br/>End users can use this method to remove unwanted routes or temporary routes which no longer is in demand.
     *
     * @param routeId the route id
     * @return <tt>true</tt> if the route was removed, <tt>false</tt> if the route could not be removed because its not stopped
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     */
    boolean removeRoute(String routeId) throws Exception;

    /**
     * Resumes the given route if it has been previously suspended
     * <p/>
     * If the route does <b>not</b> support suspension the route will be started instead
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be resumed for whatever reason
     */
    void resumeRoute(String routeId) throws Exception;

    /**
     * Suspends the given route using {@link org.apache.camel.spi.ShutdownStrategy}.
     * <p/>
     * Suspending a route is more gently than stopping, as the route consumers will be suspended (if they support)
     * otherwise the consumers will be stopped.
     * <p/>
     * By suspending the route services will be kept running (if possible) and therefore its faster to resume the route.
     * <p/>
     * If the route does <b>not</b> support suspension the route will be stopped instead
     *
     * @param routeId the route id
     * @throws Exception is thrown if the route could not be suspended for whatever reason
     */
    void suspendRoute(String routeId) throws Exception;

    /**
     * Suspends the given route using {@link org.apache.camel.spi.ShutdownStrategy} with a specified timeout.
     * <p/>
     * Suspending a route is more gently than stopping, as the route consumers will be suspended (if they support)
     * otherwise the consumers will be stopped.
     * <p/>
     * By suspending the route services will be kept running (if possible) and therefore its faster to resume the route.
     * <p/>
     * If the route does <b>not</b> support suspension the route will be stopped instead
     *
     * @param routeId  the route id
     * @param timeout  timeout
     * @param timeUnit the unit to use
     * @throws Exception is thrown if the route could not be suspended for whatever reason
     */
    void suspendRoute(String routeId, long timeout, TimeUnit timeUnit) throws Exception;

    /**
     * Returns the current status of the given route
     *
     * @param routeId the route id
     * @return the status for the route
     */
    ServiceStatus getRouteStatus(String routeId);

    /**
     * Indicates whether current thread is starting route(s).
     * <p/>
     * This can be useful to know by {@link LifecycleStrategy} or the likes, in case
     * they need to react differently.
     *
     * @return <tt>true</tt> if current thread is starting route(s), or <tt>false</tt> if not.
     */
    boolean isStartingRoutes();

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
     * Returns the management mbean assembler
     *
     * @return the mbean assembler
     */
    ManagementMBeanAssembler getManagementMBeanAssembler();

    /**
     * Returns the lifecycle strategies used to handle lifecycle notifications
     *
     * @return the lifecycle strategies
     */
    List<LifecycleStrategy> getLifecycleStrategies();

    /**
     * Adds the given lifecycle strategy to be used.
     *
     * @param lifecycleStrategy the strategy
     */
    void addLifecycleStrategy(LifecycleStrategy lifecycleStrategy);

    /**
     * Resolves a language for creating expressions
     *
     * @param language name of the language
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
     * Returns the configured property placeholder prefix token if and only if the context has
     * property placeholder abilities, otherwise returns {@code null}.
     * 
     * @return the prefix token or {@code null}
     */
    String getPropertyPrefixToken();
    
    /**
     * Returns the configured property placeholder suffix token if and only if the context has
     * property placeholder abilities, otherwise returns {@code null}.
     * 
     * @return the suffix token or {@code null}
     */
    String getPropertySuffixToken();

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
     * <b>Important:</b> Make sure to call {@link org.apache.camel.ProducerTemplate#stop()} when you are done using the template,
     * to clean up any resources.
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
     * See this FAQ before use: <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html">
     * Why does Camel use too many threads with ProducerTemplate?</a>
     * <p/>
     * <b>Important:</b> Make sure to call {@link ProducerTemplate#stop()} when you are done using the template,
     * to clean up any resources.
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
     * <b>Important:</b> Make sure to call {@link ConsumerTemplate#stop()} when you are done using the template,
     * to clean up any resources.
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
     * <p/>
     * <b>Important:</b> Make sure to call {@link ConsumerTemplate#stop()} when you are done using the template,
     * to clean up any resources.
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
     * @deprecated The return type will be switched to {@link ErrorHandlerFactory} in Camel 3.0
     *
     * @return the builder
     */
    @Deprecated
    ErrorHandlerBuilder getErrorHandlerBuilder();

    /**
     * Sets the default error handler builder which is inherited by the routes
     *
     * @param errorHandlerBuilder the builder
     */
    void setErrorHandlerBuilder(ErrorHandlerFactory errorHandlerBuilder);

    /**
     * Gets the default shared thread pool for error handlers which
     * leverages this for asynchronous redelivery tasks.
     */
    ScheduledExecutorService getErrorHandlerExecutorService();

    /**
     * Sets the data formats that can be referenced in the routes.
     *
     * @param dataFormats the data formats
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#setDataFormats(java.util.Map)}
     */
    @Deprecated
    void setDataFormats(Map<String, DataFormatDefinition> dataFormats);

    /**
     * Gets the data formats that can be referenced in the routes.
     *
     * @return the data formats available
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#getDataFormats()}
     */
    @Deprecated
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
     * @deprecated use {@link org.apache.camel.model.ModelCamelContext#resolveDataFormatDefinition(String)}
     */
    @Deprecated
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
     * @param dataFormatResolver the resolver
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
     * Gets the property value that can be referenced in the camel context
     *
     * @return the string value of property
     */
    String getProperty(String name);
    
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
     * @param factory custom factory to use
     */
    void setNodeIdFactory(NodeIdFactory factory);

    /**
     * Gets the node id factory
     *
     * @return the node id factory
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
     * changing the default tracer for existing routes is not supported.
     *
     * @param tracer the custom tracer to use as default tracer
     */
    void setDefaultTracer(InterceptStrategy tracer);

    /**
     * Gets the default backlog tracer
     *
     * @return the default backlog tracer
     */
    InterceptStrategy getDefaultBacklogTracer();

    /**
     * Sets a custom backlog tracer to be used as the default backlog tracer.
     * <p/>
     * <b>Note:</b> This must be set before any routes are created,
     * changing the default backlog tracer for existing routes is not supported.
     *
     * @param backlogTracer the custom tracer to use as default backlog tracer
     */
    void setDefaultBacklogTracer(InterceptStrategy backlogTracer);

    /**
     * Gets the default backlog debugger
     *
     * @return the default backlog debugger
     */
    InterceptStrategy getDefaultBacklogDebugger();

    /**
     * Sets a custom backlog debugger to be used as the default backlog debugger.
     * <p/>
     * <b>Note:</b> This must be set before any routes are created,
     * changing the default backlog debugger for existing routes is not supported.
     *
     * @param backlogDebugger the custom debugger to use as default backlog debugger
     */
    void setDefaultBacklogDebugger(InterceptStrategy backlogDebugger);

    /**
     * Disables using JMX as {@link org.apache.camel.spi.ManagementStrategy}.
     * <p/>
     * <b>Important:</b> This method must be called <b>before</b> the {@link CamelContext} is started.
     *
     * @throws IllegalStateException is thrown if the {@link CamelContext} is not in stopped state.
     */
    void disableJMX() throws IllegalStateException;

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
     * Gets the current {@link org.apache.camel.spi.ExecutorServiceManager}
     *
     * @return the manager
     */
    ExecutorServiceManager getExecutorServiceManager();

    /**
     * Gets the current {@link org.apache.camel.spi.ExecutorServiceStrategy}
     *
     * @return the manager
     * @deprecated use {@link #getExecutorServiceManager()}
     */
    @Deprecated
    org.apache.camel.spi.ExecutorServiceStrategy getExecutorServiceStrategy();

    /**
     * Sets a custom {@link org.apache.camel.spi.ExecutorServiceManager}
     *
     * @param executorServiceManager the custom manager
     */
    void setExecutorServiceManager(ExecutorServiceManager executorServiceManager);

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

    /**
     * Gets the current {@link Debugger}
     *
     * @return the debugger
     */
    Debugger getDebugger();

    /**
     * Sets a custom {@link Debugger}
     *
     * @param debugger the debugger
     */
    void setDebugger(Debugger debugger);

    /**
     * Gets the current {@link UuidGenerator}
     *
     * @return the uuidGenerator
     */
    UuidGenerator getUuidGenerator();
    
    /**
     * Sets a custom {@link UuidGenerator} (should only be set once) 
     *
     * @param uuidGenerator the UUID Generator
     */
    void setUuidGenerator(UuidGenerator uuidGenerator);

    /**
     * Whether or not type converters should be loaded lazy
     *
     * @return <tt>true</tt> to load lazy, <tt>false</tt> to load on startup
     * @deprecated this option is no longer supported, will be removed in a future Camel release.
     */
    @Deprecated
    Boolean isLazyLoadTypeConverters();

    /**
     * Sets whether type converters should be loaded lazy
     *
     * @param lazyLoadTypeConverters <tt>true</tt> to load lazy, <tt>false</tt> to load on startup
     * @deprecated this option is no longer supported, will be removed in a future Camel release.
     */
    @Deprecated
    void setLazyLoadTypeConverters(Boolean lazyLoadTypeConverters);

    /**
     * Whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled.
     * <b>Notice:</b> If enabled then there is a slight performance impact under very heavy load.
     *
     * @return <tt>true</tt> if enabled, <tt>false</tt> if disabled (default).
     */
    Boolean isTypeConverterStatisticsEnabled();

    /**
     * Sets whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled.
     * <b>Notice:</b> If enabled then there is a slight performance impact under very heavy load.
     * <p/>
     * You can enable/disable the statistics at runtime using the
     * {@link org.apache.camel.spi.TypeConverterRegistry#getStatistics()#setTypeConverterStatisticsEnabled(Boolean)} method,
     * or from JMX on the {@link org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean} mbean.
     *
     * @param typeConverterStatisticsEnabled <tt>true</tt> to enable, <tt>false</tt> to disable
     */
    void setTypeConverterStatisticsEnabled(Boolean typeConverterStatisticsEnabled);

    /**
     * Whether or not <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a> logging is being enabled.
     *
     * @return <tt>true</tt> if MDC logging is enabled
     */
    Boolean isUseMDCLogging();

    /**
     * Set whether <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a> is enabled.
     *
     * @param useMDCLogging <tt>true</tt> to enable MDC logging, <tt>false</tt> to disable
     */
    void setUseMDCLogging(Boolean useMDCLogging);

    /**
     * Whether or not breadcrumb is enabled.
     *
     * @return <tt>true</tt> if breadcrumb is enabled
     */
    Boolean isUseBreadcrumb();

    /**
     * Set whether breadcrumb is enabled.
     *
     * @param useBreadcrumb <tt>true</tt> to enable breadcrumb, <tt>false</tt> to disable
     */
    void setUseBreadcrumb(Boolean useBreadcrumb);

    /**
     * Find information about all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a map with the component name, and value with component details.
     * @throws LoadPropertiesException is thrown if error during classpath discovery of the components
     * @throws IOException is thrown if error during classpath discovery of the components
     */
    Map<String, Properties> findComponents() throws LoadPropertiesException, IOException;

    /**
     * Returns the HTML documentation for the given camel component
     */
    String getComponentDocumentation(String componentName) throws IOException;

    /**
     * Creates a JSON representation of all the <b>static</b> configured endpoints defined in the given route(s).
     *
     * @param routeId for a particular route, or <tt>null</tt> for all routes
     * @return a JSON string
     */
    String createRouteStaticEndpointJson(String routeId);

    /**
     * Gets the {@link StreamCachingStrategy} to use.
     */
    StreamCachingStrategy getStreamCachingStrategy();

    /**
     * Sets a custom {@link StreamCachingStrategy} to use.
     */
    void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy);

    /**
     * Gets the {@link UnitOfWorkFactory} to use.
     */
    UnitOfWorkFactory getUnitOfWorkFactory();

    /**
     * Sets a custom {@link UnitOfWorkFactory} to use.
     */
    void setUnitOfWorkFactory(UnitOfWorkFactory unitOfWorkFactory);

}
