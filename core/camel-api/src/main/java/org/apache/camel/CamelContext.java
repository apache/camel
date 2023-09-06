/*
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerRegistry;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.spi.Validator;
import org.apache.camel.spi.ValidatorRegistry;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.vault.VaultConfiguration;

/**
 * Interface used to represent the CamelContext used to configure routes and the policies to use during message
 * exchanges between endpoints.
 * <p/>
 * The CamelContext offers the following methods {@link CamelContextLifecycle} to control the lifecycle:
 * <ul>
 * <li>{@link #start()} - to start</li>
 * <li>{@link #stop()} - to shutdown (will stop all routes/components/endpoints etc and clear internal state/cache)</li>
 * <li>{@link #suspend()} - to pause routing messages</li>
 * <li>{@link #resume()} - to resume after a suspend</li>
 * </ul>
 * <p/>
 * <b>Notice:</b> {@link #stop()} and {@link #suspend()} will gracefully stop/suspend routes ensuring any messages in
 * progress will be given time to complete. See more details at {@link org.apache.camel.spi.ShutdownStrategy}.
 * <p/>
 * If you are doing a hot restart then it's advised to use the suspend/resume methods which ensure a faster restart but
 * also allows any internal state to be kept as is. The stop/start approach will do a <i>cold</i> restart of Camel,
 * where all internal state is reset.
 * <p/>
 * End users are advised to use suspend/resume. Using stop is for shutting down Camel and it's not guaranteed that when
 * it's being started again using the start method that Camel will operate consistently.
 * <p/>
 * You can use the {@link CamelContext#getCamelContextExtension()} to obtain the extension point for the
 * {@link CamelContext}. This extension point exposes internal APIs via {@link ExtendedCamelContext}.
 */
public interface CamelContext extends CamelContextLifecycle, RuntimeConfiguration {

    /**
     * Gets the {@link ExtendedCamelContext} that contains the extension points for internal context APIs. These APIs
     * are intended for internal usage within Camel and end-users should avoid using them.
     *
     * @return this {@link ExtendedCamelContext} extension point for this context.
     */
    ExtendedCamelContext getCamelContextExtension();

    /**
     * If CamelContext during the start procedure was vetoed, and therefore causing Camel to not start.
     */
    boolean isVetoStarted();

    /**
     * Gets the name (id) of this CamelContext.
     *
     * @return the name
     */
    String getName();

    /**
     * Gets the description of this CamelContext.
     *
     * @return the description, or null if no description has been set.
     */
    String getDescription();

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
     * The reason that a {@link CamelContext} can have a different name in JMX is the fact to remedy for name clash in
     * JMX when having multiple {@link CamelContext}s in the same JVM. Camel will automatic reassign and use a free name
     * to avoid failing to start.
     *
     * @return the management name
     */
    String getManagementName();

    /**
     * Sets the name this {@link CamelContext} will be registered in JMX.
     */
    void setManagementName(String name);

    /**
     * Gets the version of the this CamelContext.
     *
     * @return the version
     */
    String getVersion();

    /**
     * Gets the uptime in a human readable format
     *
     * @return the uptime in days/hours/minutes
     */
    String getUptime();

    /**
     * Gets the uptime in milli seconds
     *
     * @return the uptime in millis seconds
     */
    long getUptimeMillis();

    /**
     * Gets the date and time Camel was started up.
     */
    Date getStartDate();

    // Service Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a service to this CamelContext, which allows this CamelContext to control the lifecycle, ensuring the
     * service is stopped when the CamelContext stops.
     * <p/>
     * The service will also have {@link CamelContext} injected if its {@link CamelContextAware}. The service will also
     * be enlisted in JMX for management (if JMX is enabled). The service will be started, if its not already started.
     *
     * @param  object    the service
     * @throws Exception can be thrown when starting the service
     */
    void addService(Object object) throws Exception;

    /**
     * Adds a service to this CamelContext.
     * <p/>
     * The service will also have {@link CamelContext} injected if its {@link CamelContextAware}. The service will also
     * be enlisted in JMX for management (if JMX is enabled). The service will be started, if its not already started.
     * <p/>
     * If the option <tt>closeOnShutdown</tt> is <tt>true</tt> then this CamelContext will control the lifecycle,
     * ensuring the service is stopped when the CamelContext stops. If the option <tt>closeOnShutdown</tt> is
     * <tt>false</tt> then this CamelContext will not stop the service when the CamelContext stops.
     *
     * @param  object         the service
     * @param  stopOnShutdown whether to stop the service when this CamelContext shutdown.
     * @throws Exception      can be thrown when starting the service
     */
    void addService(Object object, boolean stopOnShutdown) throws Exception;

    /**
     * Adds a service to this CamelContext.
     * <p/>
     * The service will also have {@link CamelContext} injected if its {@link CamelContextAware}. The service will also
     * be enlisted in JMX for management (if JMX is enabled). The service will be started, if its not already started.
     * <p/>
     * If the option <tt>closeOnShutdown</tt> is <tt>true</tt> then this CamelContext will control the lifecycle,
     * ensuring the service is stopped when the CamelContext stops. If the option <tt>closeOnShutdown</tt> is
     * <tt>false</tt> then this CamelContext will not stop the service when the CamelContext stops.
     *
     * @param  object         the service
     * @param  stopOnShutdown whether to stop the service when this CamelContext shutdown.
     * @param  forceStart     whether to force starting the service right now, as otherwise the service may be deferred
     *                        being started to later using {@link #deferStartService(Object, boolean)}
     * @throws Exception      can be thrown when starting the service
     */
    void addService(Object object, boolean stopOnShutdown, boolean forceStart) throws Exception;

    /**
     * Adds a service to this CamelContext (prototype scope).
     * <p/>
     * The service will also have {@link CamelContext} injected if its {@link CamelContextAware}. The service will be
     * started, if its not already started.
     *
     * @param  object    the service
     * @throws Exception can be thrown when starting the service
     */
    void addPrototypeService(Object object) throws Exception;

    /**
     * Removes a service from this CamelContext.
     * <p/>
     * The service is assumed to have been previously added using {@link #addService(Object)} method. This method will
     * <b>not</b> change the service lifecycle.
     *
     * @param  object    the service
     * @throws Exception can be thrown if error removing the service
     * @return           <tt>true</tt> if the service was removed, <tt>false</tt> if no service existed
     */
    boolean removeService(Object object) throws Exception;

    /**
     * Has the given service already been added to this CamelContext?
     *
     * @param  object the service
     * @return        <tt>true</tt> if already added, <tt>false</tt> if not.
     */
    boolean hasService(Object object);

    /**
     * Has the given service type already been added to this CamelContext?
     *
     * @param  type the class type
     * @return      the service instance or <tt>null</tt> if not already added.
     */
    <T> T hasService(Class<T> type);

    /**
     * Has the given service type already been added to this CamelContext?
     *
     * @param  type the class type
     * @return      the services instance or empty set.
     */
    <T> Set<T> hasServices(Class<T> type);

    /**
     * Defers starting the service until {@link CamelContext} is (almost started) or started and has initialized all its
     * prior services and routes.
     * <p/>
     * If {@link CamelContext} is already started then the service is started immediately.
     *
     * @param  object         the service
     * @param  stopOnShutdown whether to stop the service when this CamelContext shutdown. Setting this to <tt>true</tt>
     *                        will keep a reference to the service in this {@link CamelContext} until the CamelContext
     *                        is stopped. So do not use it for short lived services.
     * @throws Exception      can be thrown when starting the service, which is only attempted if {@link CamelContext}
     *                        has already been started when calling this method.
     */
    void deferStartService(Object object, boolean stopOnShutdown) throws Exception;

    /**
     * Adds the given listener to be invoked when {@link CamelContext} have just been started.
     * <p/>
     * This allows listeners to do any custom work after the routes and other services have been started and are
     * running.
     * <p/>
     * <b>Important:</b> The listener will always be invoked, also if the {@link CamelContext} has already been started,
     * see the {@link org.apache.camel.StartupListener#onCamelContextStarted(CamelContext, boolean)} method.
     *
     * @param  listener  the listener
     * @throws Exception can be thrown if {@link CamelContext} is already started and the listener is invoked and cause
     *                   an exception to be thrown
     */
    void addStartupListener(StartupListener listener) throws Exception;

    // Component Management Methods
    //-----------------------------------------------------------------------

    /**
     * Adds a component to the context.
     *
     * Notice the component will be auto-started if Camel is already started.
     *
     * @param componentName the name the component is registered as
     * @param component     the component
     */
    void addComponent(String componentName, Component component);

    /**
     * Is the given component already registered?
     *
     * @param  componentName the name of the component
     * @return               the registered Component or <tt>null</tt> if not registered
     */
    Component hasComponent(String componentName);

    /**
     * Gets a component from the CamelContext by name.
     * <p/>
     * Notice the returned component will be auto-started. If you do not intend to do that then use
     * {@link #getComponent(String, boolean, boolean)}.
     *
     * @param  componentName the name of the component
     * @return               the component
     */
    Component getComponent(String componentName);

    /**
     * Gets a component from the CamelContext by name.
     * <p/>
     * Notice the returned component will be auto-started. If you do not intend to do that then use
     * {@link #getComponent(String, boolean, boolean)}.
     *
     * @param  name                 the name of the component
     * @param  autoCreateComponents whether or not the component should be lazily created if it does not already exist
     * @return                      the component
     */
    Component getComponent(String name, boolean autoCreateComponents);

    /**
     * Gets a component from the CamelContext by name.
     *
     * @param  name                 the name of the component
     * @param  autoCreateComponents whether or not the component should be lazily created if it does not already exist
     * @param  autoStart            whether to auto start the component if {@link CamelContext} is already started.
     * @return                      the component
     */
    Component getComponent(String name, boolean autoCreateComponents, boolean autoStart);

    /**
     * Gets a component from the CamelContext by name and specifying the expected type of component.
     *
     * @param  name          the name to lookup
     * @param  componentType the expected type
     * @return               the component
     */
    <T extends Component> T getComponent(String name, Class<T> componentType);

    /**
     * Gets a readonly list of names of the components currently registered
     *
     * @return a readonly list with the names of the components
     */
    Set<String> getComponentNames();

    /**
     * Removes a previously added component.
     * <p/>
     * The component being removed will be stopped first.
     *
     * @param  componentName the component name to remove
     * @return               the previously added component or null if it had not been previously added.
     */
    Component removeComponent(String componentName);

    // Endpoint Management Methods
    //-----------------------------------------------------------------------

    /**
     * Gets the {@link org.apache.camel.spi.EndpointRegistry}
     */
    EndpointRegistry<? extends ValueHolder<String>> getEndpointRegistry();

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type. If the name has a singleton endpoint
     * registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created and registered in the
     * {@link org.apache.camel.spi.EndpointRegistry}.
     *
     * @param  uri the URI of the endpoint
     * @return     the endpoint
     */
    Endpoint getEndpoint(String uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type. If the name has a singleton endpoint
     * registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created and registered in the
     * {@link org.apache.camel.spi.EndpointRegistry}.
     *
     * @param  uri        the URI of the endpoint
     * @param  parameters the parameters to customize the endpoint
     * @return            the endpoint
     */
    Endpoint getEndpoint(String uri, Map<String, Object> parameters);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type. If the name has a singleton endpoint
     * registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created and registered in the
     * {@link org.apache.camel.spi.EndpointRegistry}.
     *
     * @param  name         the name of the endpoint
     * @param  endpointType the expected type
     * @return              the endpoint
     */
    <T extends Endpoint> T getEndpoint(String name, Class<T> endpointType);

    /**
     * Returns a read-only {@link Collection} of all of the endpoints from the
     * {@link org.apache.camel.spi.EndpointRegistry}
     *
     * @return all endpoints
     */
    Collection<Endpoint> getEndpoints();

    /**
     * Is the given endpoint already registered in the {@link org.apache.camel.spi.EndpointRegistry}
     *
     * @param  uri the URI of the endpoint
     * @return     the registered endpoint or <tt>null</tt> if not registered
     */
    Endpoint hasEndpoint(String uri);

    /**
     * Adds and starts the endpoint to the {@link org.apache.camel.spi.EndpointRegistry} using the given URI.
     *
     * @param  uri       the URI to be used to resolve this endpoint
     * @param  endpoint  the endpoint to be started and added to the registry
     * @return           the old endpoint that was previously registered or <tt>null</tt> if none was registered
     * @throws Exception if the new endpoint could not be started or the old endpoint could not be stopped
     */
    Endpoint addEndpoint(String uri, Endpoint endpoint) throws Exception;

    /**
     * Removes the endpoint from the {@link org.apache.camel.spi.EndpointRegistry}.
     * <p/>
     * The endpoint being removed will be stopped first.
     *
     * @param  endpoint  the endpoint
     * @throws Exception if the endpoint could not be stopped
     */
    void removeEndpoint(Endpoint endpoint) throws Exception;

    /**
     * Removes all endpoints with the given URI from the {@link org.apache.camel.spi.EndpointRegistry}.
     * <p/>
     * The endpoints being removed will be stopped first.
     *
     * @param  pattern   an uri or pattern to match
     * @return           a collection of endpoints removed which could be empty if there are no endpoints found for the
     *                   given <tt>pattern</tt>
     * @throws Exception if at least one endpoint could not be stopped
     * @see              org.apache.camel.support.EndpointHelper#matchEndpoint(CamelContext, String, String) for pattern
     */
    Collection<Endpoint> removeEndpoints(String pattern) throws Exception;

    /**
     * Gets the global endpoint configuration, where you can configure common endpoint options.
     */
    GlobalEndpointConfiguration getGlobalEndpointConfiguration();

    // Route Management Methods
    //-----------------------------------------------------------------------

    /**
     * Sets a custom {@link RouteController} to use
     *
     * @param routeController the route controller
     */
    void setRouteController(RouteController routeController);

    /**
     * Gets the {@link RouteController}
     *
     * @return the route controller.
     */
    RouteController getRouteController();

    /**
     * Returns the current routes in this CamelContext
     *
     * @return the current routes
     */
    List<Route> getRoutes();

    /**
     * Returns the total number of routes in this CamelContext
     */
    int getRoutesSize();

    /**
     * Gets the route with the given id
     *
     * @param  id id of the route
     * @return    the route or <tt>null</tt> if not found
     */
    Route getRoute(String id);

    /**
     * Gets the processor from any of the routes which with the given id
     *
     * @param  id id of the processor
     * @return    the processor or <tt>null</tt> if not found
     */
    Processor getProcessor(String id);

    /**
     * Gets the processor from any of the routes which with the given id
     *
     * @param  id                           id of the processor
     * @param  type                         the processor type
     * @return                              the processor or <tt>null</tt> if not found
     * @throws java.lang.ClassCastException is thrown if the type is not correct type
     */
    <T extends Processor> T getProcessor(String id, Class<T> type);

    /**
     * Adds a collection of routes to this CamelContext using the given builder to build them.
     * <p/>
     * <b>Important:</b> The added routes will <b>only</b> be started, if {@link CamelContext} is already started. You
     * may want to check the state of {@link CamelContext} before adding the routes, using the
     * {@link org.apache.camel.CamelContext#getStatus()} method.
     * <p/>
     * <b>Important: </b> Each route in the same {@link org.apache.camel.CamelContext} must have an <b>unique</b> route
     * id. If you use the API from {@link org.apache.camel.CamelContext} or
     * {@link org.apache.camel.model.ModelCamelContext} to add routes, then any new routes which has a route id that
     * matches an old route, then the old route is replaced by the new route.
     *
     * @param  builder   the builder which will create the routes and add them to this CamelContext
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addRoutes(RoutesBuilder builder) throws Exception;

    /**
     * Adds the templated routes from the routes builder. For example in Java DSL you can use
     * {@link org.apache.camel.builder.TemplatedRouteBuilder}.
     *
     * @param  builder   the builder which has templated routes
     * @throws Exception if the routes could not be created for whatever reason
     */
    void addTemplatedRoutes(RoutesBuilder builder) throws Exception;

    /**
     * Adds the routes configurations (global configuration for all routes) from the routes builder.
     *
     * @param  builder   the builder which has routes configurations
     * @throws Exception if the routes configurations could not be created for whatever reason
     */
    void addRoutesConfigurations(RouteConfigurationsBuilder builder) throws Exception;

    /**
     * Removes the given route (the route <b>must</b> be stopped before it can be removed).
     * <p/>
     * A route which is removed will be unregistered from JMX, have its services stopped/shutdown and the route
     * definition etc. will also be removed. All the resources related to the route will be stopped and cleared.
     * <p/>
     * <b>Important:</b> When removing a route, the {@link Endpoint}s which are in the static cache of
     * {@link org.apache.camel.spi.EndpointRegistry} and are <b>only</b> used by the route (not used by other routes)
     * will also be removed. But {@link Endpoint}s which may have been created as part of routing messages by the route,
     * and those endpoints are enlisted in the dynamic cache of {@link org.apache.camel.spi.EndpointRegistry} are
     * <b>not</b> removed. To remove those dynamic kind of endpoints, use the {@link #removeEndpoints(String)} method.
     * If not removing those endpoints, they will be kept in the dynamic cache of
     * {@link org.apache.camel.spi.EndpointRegistry}, but my eventually be removed (evicted) when they have not been in
     * use for a longer period of time; and the dynamic cache upper limit is hit, and it evicts the least used
     * endpoints.
     * <p/>
     * End users can use this method to remove unwanted routes or temporary routes which no longer is in demand.
     *
     * @param  routeId   the route id
     * @return           <tt>true</tt> if the route was removed, <tt>false</tt> if the route could not be removed
     *                   because it's not stopped
     * @throws Exception is thrown if the route could not be shutdown for whatever reason
     */
    boolean removeRoute(String routeId) throws Exception;

    /**
     * Adds a new route from a given route template.
     *
     * Camel end users should favour using {@link org.apache.camel.builder.TemplatedRouteBuilder} which is a fluent
     * builder with more functionality than this API.
     *
     * @param  routeId         the id of the new route to add (optional)
     * @param  routeTemplateId the id of the route template (mandatory)
     * @param  parameters      parameters to use for the route template when creating the new route
     * @return                 the id of the route added (for example when an id was auto assigned)
     * @throws Exception       is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters) throws Exception;

    /**
     * Adds a new route from a given route template.
     *
     * Camel end users should favour using {@link org.apache.camel.builder.TemplatedRouteBuilder} which is a fluent
     * builder with more functionality than this API.
     *
     * @param  routeId         the id of the new route to add (optional)
     * @param  routeTemplateId the id of the route template (mandatory)
     * @param  prefixId        prefix to use for all node ids (not route id). Use null for no prefix. (optional)
     * @param  parameters      parameters to use for the route template when creating the new route
     * @return                 the id of the route added (for example when an id was auto assigned)
     * @throws Exception       is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId,
            Map<String, Object> parameters)
            throws Exception;

    /**
     * Adds a new route from a given route template.
     *
     * Camel end users should favour using {@link org.apache.camel.builder.TemplatedRouteBuilder} which is a fluent
     * builder with more functionality than this API.
     *
     * @param  routeId              the id of the new route to add (optional)
     * @param  routeTemplateId      the id of the route template (mandatory)
     * @param  prefixId             prefix to use for all node ids (not route id). Use null for no prefix. (optional)
     * @param  routeTemplateContext the route template context (mandatory)
     * @return                      the id of the route added (for example when an id was auto assigned)
     * @throws Exception            is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId, RouteTemplateContext routeTemplateContext)
            throws Exception;

    /**
     * Removes the route templates matching the pattern
     *
     * @param  pattern   pattern, such as * for all, or foo* to remove all foo templates
     * @throws Exception is thrown if error during removing route templates
     */
    void removeRouteTemplates(String pattern) throws Exception;

    /**
     * Adds the given route policy factory
     *
     * @param routePolicyFactory the factory
     */
    void addRoutePolicyFactory(RoutePolicyFactory routePolicyFactory);

    /**
     * Gets the route policy factories
     *
     * @return the list of current route policy factories
     */
    List<RoutePolicyFactory> getRoutePolicyFactories();

    // Rest Methods
    //-----------------------------------------------------------------------

    /**
     * Sets a custom {@link org.apache.camel.spi.RestConfiguration}
     *
     * @param restConfiguration the REST configuration
     */
    void setRestConfiguration(RestConfiguration restConfiguration);

    /**
     * Gets the default REST configuration
     *
     * @return the configuration, or <tt>null</tt> if none has been configured.
     */
    RestConfiguration getRestConfiguration();

    /**
     * Sets a custom {@link VaultConfiguration}
     *
     * @param vaultConfiguration the vault configuration
     */
    void setVaultConfiguration(VaultConfiguration vaultConfiguration);

    /**
     * Gets the vault configuration
     *
     * @return the configuration, or <tt>null</tt> if none has been configured.
     */
    VaultConfiguration getVaultConfiguration();

    /**
     * Gets the {@link org.apache.camel.spi.RestRegistry} to use
     */
    RestRegistry getRestRegistry();

    /**
     * Sets a custom {@link org.apache.camel.spi.RestRegistry} to use.
     */
    void setRestRegistry(RestRegistry restRegistry);

    // Properties
    //-----------------------------------------------------------------------

    /**
     * Returns the type converter used to coerce types from one type to another.
     * <p/>
     * Notice that this {@link CamelContext} should be at least initialized before you can get the type converter.
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
     * Configures the type converter registry to use, where type converters can be added or looked up.
     *
     * @param typeConverterRegistry the registry to use
     */
    void setTypeConverterRegistry(TypeConverterRegistry typeConverterRegistry);

    /**
     * Returns the registry used to lookup components by name and type such as SimpleRegistry, Spring
     * ApplicationContext, JNDI, or the OSGi Service Registry.
     *
     * @return the registry
     */
    Registry getRegistry();

    /**
     * Returns the registry used to lookup components by name and as the given type
     *
     * @param  type the registry type such as org.apache.camel.impl.JndiRegistry
     * @return      the registry, or <tt>null</tt> if the given type was not found as a registry implementation
     */
    <T> T getRegistry(Class<T> type);

    /**
     * Returns the injector used to instantiate objects by type
     *
     * @return the injector
     */
    Injector getInjector();

    /**
     * Sets the injector to use
     */
    void setInjector(Injector injector);

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
     * @param  language                name of the language
     * @return                         the resolved language
     * @throws NoSuchLanguageException is thrown if language could not be resolved
     */
    Language resolveLanguage(String language) throws NoSuchLanguageException;

    /**
     * Parses the given text and resolve any property placeholders - using {{key}}.
     * <p/>
     * <b>Important:</b> If resolving placeholders on an endpoint uri, then you SHOULD use
     * EndpointHelper#resolveEndpointUriPropertyPlaceholders instead.
     *
     * @param  text                     the text such as an endpoint uri or the likes
     * @return                          the text with resolved property placeholders
     * @throws IllegalArgumentException is thrown if property placeholders was used and there was an error resolving
     *                                  them
     */
    String resolvePropertyPlaceholders(String text);

    /**
     * Returns the configured properties component or create one if none has been configured.
     *
     * @return the properties component
     */
    PropertiesComponent getPropertiesComponent();

    /**
     * Sets a custom properties component to be used.
     */
    void setPropertiesComponent(PropertiesComponent propertiesComponent);

    /**
     * Gets a readonly list with the names of the languages currently registered.
     *
     * @return a readonly list with the names of the languages
     */
    Set<String> getLanguageNames();

    /**
     * Creates a new {@link ProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a>
     * <p/>
     * <b>Important:</b> Make sure to call {@link org.apache.camel.ProducerTemplate#stop()} when you are done using the
     * template, to clean up any resources.
     * <p/>
     * Will use cache size defined in Camel property with key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}. If no key was
     * defined then it will fallback to a default size of 1000. You can also use the
     * {@link org.apache.camel.ProducerTemplate#setMaximumCacheSize(int)} method to use a custom value before starting
     * the template.
     *
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ProducerTemplate createProducerTemplate();

    /**
     * Creates a new {@link ProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a>
     * <p/>
     * <b>Important:</b> Make sure to call {@link ProducerTemplate#stop()} when you are done using the template, to
     * clean up any resources.
     *
     * @param  maximumCacheSize      the maximum cache size
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ProducerTemplate createProducerTemplate(int maximumCacheSize);

    /**
     * Creates a new {@link FluentProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a>
     * <p/>
     * <b>Important:</b> Make sure to call {@link org.apache.camel.FluentProducerTemplate#stop()} when you are done
     * using the template, to clean up any resources.
     * <p/>
     * Will use cache size defined in Camel property with key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}. If no key was
     * defined then it will fallback to a default size of 1000. You can also use the
     * {@link org.apache.camel.FluentProducerTemplate#setMaximumCacheSize(int)} method to use a custom value before
     * starting the template.
     *
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    FluentProducerTemplate createFluentProducerTemplate();

    /**
     * Creates a new {@link FluentProducerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a>
     * <p/>
     * <b>Important:</b> Make sure to call {@link FluentProducerTemplate#stop()} when you are done using the template,
     * to clean up any resources.
     *
     * @param  maximumCacheSize      the maximum cache size
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    FluentProducerTemplate createFluentProducerTemplate(int maximumCacheSize);

    /**
     * Creates a new {@link ConsumerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a> as it also applies for ConsumerTemplate.
     * <p/>
     * <b>Important:</b> Make sure to call {@link ConsumerTemplate#stop()} when you are done using the template, to
     * clean up any resources.
     * <p/>
     * Will use cache size defined in Camel property with key {@link Exchange#MAXIMUM_CACHE_POOL_SIZE}. If no key was
     * defined then it will fallback to a default size of 1000. You can also use the
     * {@link org.apache.camel.ConsumerTemplate#setMaximumCacheSize(int)} method to use a custom value before starting
     * the template.
     *
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ConsumerTemplate createConsumerTemplate();

    /**
     * Creates a new {@link ConsumerTemplate} which is <b>started</b> and therefore ready to use right away.
     * <p/>
     * See this FAQ before use:
     * <a href="http://camel.apache.org/why-does-camel-use-too-many-threads-with-producertemplate.html"> Why does Camel
     * use too many threads with ProducerTemplate?</a> as it also applies for ConsumerTemplate.
     * <p/>
     * <b>Important:</b> Make sure to call {@link ConsumerTemplate#stop()} when you are done using the template, to
     * clean up any resources.
     *
     * @param  maximumCacheSize      the maximum cache size
     * @return                       the template
     * @throws RuntimeCamelException is thrown if error starting the template
     */
    ConsumerTemplate createConsumerTemplate(int maximumCacheSize);

    /**
     * Resolve an existing data format, or creates a new by the given its name
     *
     * @param  name the data format name or a reference to it in the {@link Registry}
     * @return      the resolved data format, or <tt>null</tt> if not found
     */
    DataFormat resolveDataFormat(String name);

    /**
     * Creates a new instance of the given data format given its name.
     *
     * @param  name the data format name or a reference to a data format factory in the {@link Registry}
     * @return      the created data format, or <tt>null</tt> if not found
     */
    DataFormat createDataFormat(String name);

    /**
     * Gets a readonly list of names of the data formats currently registered
     *
     * @return a readonly list with the names of the data formats
     */
    Set<String> getDataFormatNames();

    /**
     * Resolve a transformer given a scheme
     *
     * @param  name the transformer name, usually a combination of some scheme and name.
     * @return      the resolved transformer, or <tt>null</tt> if not found
     */
    Transformer resolveTransformer(String name);

    /**
     * Resolve a transformer given from/to data type.
     *
     * @param  from from data type
     * @param  to   to data type
     * @return      the resolved transformer, or <tt>null</tt> if not found
     */
    Transformer resolveTransformer(DataType from, DataType to);

    /**
     * Gets the {@link org.apache.camel.spi.TransformerRegistry}
     *
     * @return the TransformerRegistry
     */
    TransformerRegistry getTransformerRegistry();

    /**
     * Resolve a validator given from/to data type.
     *
     * @param  type the data type
     * @return      the resolved validator, or <tt>null</tt> if not found
     */
    Validator resolveValidator(DataType type);

    /**
     * Gets the {@link org.apache.camel.spi.ValidatorRegistry}
     *
     * @return the ValidatorRegistry
     */
    ValidatorRegistry getValidatorRegistry();

    /**
     * Sets global options that can be referenced in the camel context
     * <p/>
     * <b>Important:</b> This has nothing to do with property placeholders, and is just a plain set of key/value pairs
     * which are used to configure global options on CamelContext, such as a maximum debug logging length etc. For
     * property placeholders use {@link #resolvePropertyPlaceholders(String)} method and see more details at the
     * <a href="http://camel.apache.org/using-propertyplaceholder.html">property placeholder</a> documentation.
     *
     * @param globalOptions global options that can be referenced in the camel context
     */
    void setGlobalOptions(Map<String, String> globalOptions);

    /**
     * Gets global options that can be referenced in the camel context.
     * <p/>
     * <b>Important:</b> This has nothing to do with property placeholders, and is just a plain set of key/value pairs
     * which are used to configure global options on CamelContext, such as a maximum debug logging length etc. For
     * property placeholders use {@link #resolvePropertyPlaceholders(String)} method and see more details at the
     * <a href="http://camel.apache.org/using-propertyplaceholder.html">property placeholder</a> documentation.
     *
     * @return global options for this context
     */
    Map<String, String> getGlobalOptions();

    /**
     * Gets the global option value that can be referenced in the camel context
     * <p/>
     * <b>Important:</b> This has nothing to do with property placeholders, and is just a plain set of key/value pairs
     * which are used to configure global options on CamelContext, such as a maximum debug logging length etc. For
     * property placeholders use {@link #resolvePropertyPlaceholders(String)} method and see more details at the
     * <a href="http://camel.apache.org/using-propertyplaceholder.html">property placeholder</a> documentation.
     *
     * @return the string value of the global option
     */
    String getGlobalOption(String key);

    /**
     * Returns the class resolver to be used for loading/lookup of classes.
     *
     * @return the resolver
     */
    ClassResolver getClassResolver();

    /**
     * Sets the class resolver to be use
     *
     * @param resolver the resolver
     */
    void setClassResolver(ClassResolver resolver);

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
     * Gets the application CamelContext class loader which may be helpful for running camel in other containers
     *
     * @return the application CamelContext class loader
     */
    ClassLoader getApplicationContextClassLoader();

    /**
     * Sets the application CamelContext class loader
     *
     * @param classLoader the class loader
     */
    void setApplicationContextClassLoader(ClassLoader classLoader);

    /**
     * Gets the current shutdown strategy.
     * <p/>
     * The shutdown strategy is <b>not</b> intended for Camel end users to use for stopping routes. Instead use
     * {@link RouteController} via {@link CamelContext}.
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
     * Sets a custom {@link org.apache.camel.spi.ExecutorServiceManager}
     *
     * @param executorServiceManager the custom manager
     */
    void setExecutorServiceManager(ExecutorServiceManager executorServiceManager);

    /**
     * Gets the current {@link org.apache.camel.spi.MessageHistoryFactory}
     *
     * @return the factory
     */
    MessageHistoryFactory getMessageHistoryFactory();

    /**
     * Sets a custom {@link org.apache.camel.spi.MessageHistoryFactory}
     *
     * @param messageHistoryFactory the custom factory
     */
    void setMessageHistoryFactory(MessageHistoryFactory messageHistoryFactory);

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
     * Gets the current {@link Tracer}
     *
     * @return the tracer
     */
    Tracer getTracer();

    /**
     * Sets a custom {@link Tracer}
     */
    void setTracer(Tracer tracer);

    /**
     * Whether to set tracing on standby. If on standby then the tracer is installed and made available. Then the tracer
     * can be enabled later at runtime via JMX or via {@link Tracer#setEnabled(boolean)}.
     */
    void setTracingStandby(boolean tracingStandby);

    /**
     * Whether to set tracing on standby. If on standby then the tracer is installed and made available. Then the tracer
     * can be enabled later at runtime via JMX or via {@link Tracer#setEnabled(boolean)}.
     */
    boolean isTracingStandby();

    /**
     * Whether to set backlog tracing on standby. If on standby then the backlog tracer is installed and made available.
     * Then the backlog tracer can be enabled later at runtime via JMX or via Java API.
     */
    void setBacklogTracingStandby(boolean backlogTracingStandby);

    /**
     * Whether to set backlog tracing on standby. If on standby then the backlog tracer is installed and made available.
     * Then the backlog tracer can be enabled later at runtime via JMX or via Java API.
     */
    boolean isBacklogTracingStandby();

    /**
     * Whether backlog tracing should trace inner details from route templates (or kamelets). Turning this off can
     * reduce the verbosity of tracing when using many route templates, and allow to focus on tracing your own Camel
     * routes only.
     */
    void setBacklogTracingTemplates(boolean backlogTracingTemplates);

    /**
     * Whether backlog tracing should trace inner details from route templates (or kamelets). Turning this on increases
     * the verbosity of tracing by including events from internal routes in the templates or kamelets.
     */
    boolean isBacklogTracingTemplates();

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
     * Whether to load custom type converters by scanning classpath. This is used for backwards compatibility with Camel
     * 2.x. Its recommended to migrate to use fast type converter loading by setting <tt>@Converter(loader = true)</tt>
     * on your custom type converter classes.
     */
    Boolean isLoadTypeConverters();

    /**
     * Whether to load custom type converters by scanning classpath. This is used for backwards compatibility with Camel
     * 2.x. Its recommended to migrate to use fast type converter loading by setting <tt>@Converter(loader = true)</tt>
     * on your custom type converter classes.
     *
     * @param loadTypeConverters whether to load custom type converters using classpath scanning.
     */
    void setLoadTypeConverters(Boolean loadTypeConverters);

    /**
     * Whether to load custom health checks by scanning classpath.
     */
    Boolean isLoadHealthChecks();

    /**
     * Whether to load custom health checks by scanning classpath.
     */
    void setLoadHealthChecks(Boolean loadHealthChecks);

    /**
     * Whether to capture precise source location:line-number for all EIPs in Camel routes.
     *
     * Enabling this will impact parsing Java based routes (also Groovy, Kotlin, etc.) on startup as this uses
     * {@link StackTraceElement} to calculate the location from the Camel route, which comes with a performance cost.
     * This only impact startup, not the performance of the routes at runtime.
     */
    Boolean isSourceLocationEnabled();

    /**
     * Whether to capture precise source location:line-number for all EIPs in Camel routes.
     *
     * Enabling this will impact parsing Java based routes (also Groovy, Kotlin, etc.) on startup as this uses
     * {@link StackTraceElement} to calculate the location from the Camel route, which comes with a performance cost.
     * This only impact startup, not the performance of the routes at runtime.
     */
    void setSourceLocationEnabled(Boolean sourceLocationEnabled);

    /**
     * Whether camel-k style modeline is also enabled when not using camel-k. Enabling this allows to use a camel-k like
     * experience by being able to configure various settings using modeline directly in your route source code.
     */
    Boolean isModeline();

    /**
     * Whether camel-k style modeline is also enabled when not using camel-k. Enabling this allows to use a camel-k like
     * experience by being able to configure various settings using modeline directly in your route source code.
     */
    void setModeline(Boolean modeline);

    /**
     * Whether to enable developer console (requires camel-console on classpath).
     *
     * The developer console is only for assisting during development. This is NOT for production usage.
     */
    Boolean isDevConsole();

    /**
     * Whether to enable developer console (requires camel-console on classpath)
     *
     * The developer console is only for assisting during development. This is NOT for production usage.
     */
    void setDevConsole(Boolean loadDevConsoles);

    /**
     * Whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled. <b>Notice:</b> If enabled then there is a
     * slight performance impact under very heavy load.
     *
     * @return <tt>true</tt> if enabled, <tt>false</tt> if disabled (default).
     */
    Boolean isTypeConverterStatisticsEnabled();

    /**
     * Sets whether or not type converter statistics is enabled.
     * <p/>
     * By default the type converter utilization statistics is disabled. <b>Notice:</b> If enabled then there is a
     * slight performance impact under very heavy load.
     * <p/>
     * You can enable/disable the statistics at runtime using the
     * {@link org.apache.camel.spi.TypeConverterRegistry#getStatistics()#setTypeConverterStatisticsEnabled(Boolean)}
     * method, or from JMX on the {@link org.apache.camel.api.management.mbean.ManagedTypeConverterRegistryMBean} mbean.
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
     * Gets the pattern used for determine which custom MDC keys to propagate during message routing when the routing
     * engine continues routing asynchronously for the given message. Setting this pattern to <tt>*</tt> will propagate
     * all custom keys. Or setting the pattern to <tt>foo*,bar*</tt> will propagate any keys starting with either foo or
     * bar. Notice that a set of standard Camel MDC keys are always propagated which starts with <tt>camel.</tt> as key
     * name.
     * <p/>
     * The match rules are applied in this order (case insensitive):
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     */
    String getMDCLoggingKeysPattern();

    /**
     * Sets the pattern used for determine which custom MDC keys to propagate during message routing when the routing
     * engine continues routing asynchronously for the given message. Setting this pattern to <tt>*</tt> will propagate
     * all custom keys. Or setting the pattern to <tt>foo*,bar*</tt> will propagate any keys starting with either foo or
     * bar. Notice that a set of standard Camel MDC keys are always propagated which starts with <tt>camel.</tt> as key
     * name.
     * <p/>
     * The match rules are applied in this order (case insensitive):
     * <ul>
     * <li>exact match, returns true</li>
     * <li>wildcard match (pattern ends with a * and the name starts with the pattern), returns true</li>
     * <li>regular expression match, returns true</li>
     * <li>otherwise returns false</li>
     * </ul>
     *
     * @param pattern the pattern
     */
    void setMDCLoggingKeysPattern(String pattern);

    /**
     * To use a custom tracing logging format.
     *
     * The default format (arrow, routeId, label) is: %-4.4s [%-12.12s] [%-33.33s]
     */
    String getTracingLoggingFormat();

    /**
     * To use a custom tracing logging format.
     *
     * The default format (arrow, routeId, label) is: %-4.4s [%-12.12s] [%-33.33s]
     *
     * @param format the logging format
     */
    void setTracingLoggingFormat(String format);

    /**
     * Whether tracing should trace inner details from route templates (or kamelets). Turning this on increases the
     * verbosity of tracing by including events from internal routes in the templates or kamelets.
     */
    void setTracingTemplates(boolean tracingTemplates);

    /**
     * Whether tracing should trace inner details from route templates (or kamelets). Turning this off can reduce the
     * verbosity of tracing when using many route templates, and allow to focus on tracing your own Camel routes only.
     */
    boolean isTracingTemplates();

    /**
     * If dumping is enabled then Camel will during startup dump all loaded routes (incl rests and route templates)
     * represented as XML DSL into the log. This is intended for trouble shooting or to assist during development.
     *
     * Sensitive information that may be configured in the route endpoints could potentially be included in the dump
     * output and is therefore not recommended to be used for production usage.
     *
     * This requires to have camel-xml-jaxb on the classpath to be able to dump the routes as XML.
     *
     * @return <tt>xml</tt>, or <tt>yaml</tt> if dumping is enabled
     */
    String getDumpRoutes();

    /**
     * If dumping is enabled then Camel will during startup dump all loaded routes (incl rests and route templates)
     * represented as XML/YAML DSL into the log. This is intended for trouble shooting or to assist during development.
     *
     * Sensitive information that may be configured in the route endpoints could potentially be included in the dump
     * output and is therefore not recommended being used for production usage.
     *
     * This requires to have camel-xml-io/camel-yaml-io on the classpath to be able to dump the routes as XML/YAML.
     *
     * @param format xml or yaml (additional configuration can be specified using query parameters, eg
     *               ?include=all&uriAsParameters=true)
     */
    void setDumpRoutes(String format);

    /**
     * Whether to enable using data type on Camel messages.
     * <p/>
     * Data type are automatic turned on if one or more routes has been explicit configured with input and output types.
     * Otherwise, data type is default off.
     *
     * @return <tt>true</tt> if data type is enabled
     */
    Boolean isUseDataType();

    /**
     * Whether to enable using data type on Camel messages.
     * <p/>
     * Data type are automatic turned on if one or more routes has been explicit configured with input and output types.
     * Otherwise, data type is default off.
     *
     * @param useDataType <tt>true</tt> to enable data type on Camel messages.
     */
    void setUseDataType(Boolean useDataType);

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
     * Gets the {@link StreamCachingStrategy} to use.
     */
    StreamCachingStrategy getStreamCachingStrategy();

    /**
     * Sets a custom {@link StreamCachingStrategy} to use.
     */
    void setStreamCachingStrategy(StreamCachingStrategy streamCachingStrategy);

    /**
     * Gets the {@link org.apache.camel.spi.RuntimeEndpointRegistry} to use, or <tt>null</tt> if none is in use.
     */
    RuntimeEndpointRegistry getRuntimeEndpointRegistry();

    /**
     * Sets a custom {@link org.apache.camel.spi.RuntimeEndpointRegistry} to use.
     */
    void setRuntimeEndpointRegistry(RuntimeEndpointRegistry runtimeEndpointRegistry);

    /**
     * Sets the global SSL context parameters.
     */
    void setSSLContextParameters(SSLContextParameters sslContextParameters);

    /**
     * Gets the global SSL context parameters if configured.
     */
    SSLContextParameters getSSLContextParameters();

    /**
     * Controls the level of information logged during startup (and shutdown) of {@link CamelContext}.
     */
    void setStartupSummaryLevel(StartupSummaryLevel startupSummaryLevel);

    /**
     * Controls the level of information logged during startup (and shutdown) of {@link CamelContext}.
     */
    StartupSummaryLevel getStartupSummaryLevel();

}
