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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExchangeFactoryManager;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.ProcessorExchangeFactory;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteStartupOrder;
import org.apache.camel.spi.StartupStepRecorder;

/**
 * Extended {@link CamelContext} which contains the methods and APIs that are not primary intended for Camel end users
 * but for SPI, custom components, or more advanced used-cases with Camel.
 */
public interface ExtendedCamelContext {

    /**
     * Sets the name (id) of this context.
     * <p/>
     * This operation is mostly only used by different Camel runtimes such as camel-spring, camel-cdi, camel-spring-boot
     * etc. Important: Setting the name should only be set before CamelContext is started.
     *
     * @param name the name
     */
    void setName(String name);

    default String getName() {
        return null;
    }

    /**
     * Sets the description of this Camel application.
     */
    void setDescription(String description);

    default String getDescription() {
        return null;
    }

    /**
     * Sets the registry Camel should use for looking up beans by name or type.
     * <p/>
     * This operation is mostly only used by different Camel runtimes such as camel-spring, camel-cdi, camel-spring-boot
     * etc. Important: Setting the registry should only be set before CamelContext is started.
     *
     * @param registry the registry such as DefaultRegistry or
     */
    void setRegistry(Registry registry);

    /**
     * Sets the assembler to assemble a {@link javax.management.modelmbean.RequiredModelMBean}
     *
     * @param managementMBeanAssembler the assembler to use
     */
    void setManagementMBeanAssembler(ManagementMBeanAssembler managementMBeanAssembler);

    default Registry getRegistry() {
        return null;
    }

    /**
     * Method to signal to {@link CamelContext} that the process to initialize setup routes is in progress.
     *
     * @param done <tt>false</tt> to start the process, call again with <tt>true</tt> to signal its done.
     * @see        #isSetupRoutes()
     */
    void setupRoutes(boolean done);

    /**
     * Indicates whether current thread is setting up route(s) as part of starting Camel.
     * <p/>
     * This can be useful to know by {@link LifecycleStrategy} or the likes, in case they need to react differently.
     * <p/>
     * As the startup procedure of {@link CamelContext} is slightly different when using plain Java versus
     * camel-spring-xml or camel-blueprint, then we need to know when spring/blueprint are setting up the routes, which
     * can happen after the {@link CamelContext} itself is in started state, due the asynchronous event nature of
     * especially blueprint.
     *
     * @return <tt>true</tt> if current thread is setting up route(s), or <tt>false</tt> if not.
     */
    boolean isSetupRoutes();

    /**
     * Registers a {@link org.apache.camel.spi.EndpointStrategy callback} to allow you to do custom logic when an
     * {@link Endpoint} is about to be registered to the {@link org.apache.camel.spi.EndpointRegistry}.
     * <p/>
     * When a callback is registered it will be executed on the already registered endpoints allowing you to catch-up
     *
     * @param strategy callback to be invoked
     */
    void registerEndpointCallback(EndpointStrategy strategy);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type (scope is prototype). If the name has a
     * singleton endpoint registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created.
     *
     * The endpoint is NOT registered in the {@link org.apache.camel.spi.EndpointRegistry} as its prototype scoped, and
     * therefore expected to be short lived and discarded after use (you must stop and shutdown the endpoint when no
     * longer in use).
     *
     * @param  uri the URI of the endpoint
     * @return     the endpoint
     *
     * @see        CamelContext#getEndpoint(String)
     */
    Endpoint getPrototypeEndpoint(String uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type (scope is prototype). If the name has a
     * singleton endpoint registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created.
     *
     * The endpoint is NOT registered in the {@link org.apache.camel.spi.EndpointRegistry} as its prototype scoped, and
     * therefore expected to be short lived and discarded after use (you must stop and shutdown the endpoint when no
     * longer in use).
     *
     * @param  uri the URI of the endpoint
     * @return     the endpoint
     *
     * @see        CamelContext#getEndpoint(String)
     */
    Endpoint getPrototypeEndpoint(NormalizedEndpointUri uri);

    /**
     * Is the given endpoint already registered in the {@link org.apache.camel.spi.EndpointRegistry}
     *
     * @param  uri the URI of the endpoint
     * @return     the registered endpoint or <tt>null</tt> if not registered
     */
    Endpoint hasEndpoint(NormalizedEndpointUri uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type. If the name has a singleton endpoint
     * registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created and registered in the
     * {@link org.apache.camel.spi.EndpointRegistry}.
     *
     * @param  uri the URI of the endpoint
     * @return     the endpoint
     *
     * @see        #getPrototypeEndpoint(String)
     */
    Endpoint getEndpoint(NormalizedEndpointUri uri);

    /**
     * Resolves the given name to an {@link Endpoint} of the specified type. If the name has a singleton endpoint
     * registered, then the singleton is returned. Otherwise, a new {@link Endpoint} is created and registered in the
     * {@link org.apache.camel.spi.EndpointRegistry}.
     *
     * @param  uri        the URI of the endpoint
     * @param  parameters the parameters to customize the endpoint
     * @return            the endpoint
     *
     * @see               #getPrototypeEndpoint(String)
     */
    Endpoint getEndpoint(NormalizedEndpointUri uri, Map<String, Object> parameters);

    /**
     * Normalizes the given uri.
     *
     * @param  uri the uri
     * @return     a normalized uri
     */
    NormalizedEndpointUri normalizeUri(String uri);

    /**
     * Returns the order in which the route inputs was started.
     * <p/>
     * The order may not be according to the startupOrder defined on the route. For example a route could be started
     * manually later, or new routes added at runtime.
     *
     * @return a list in the order how routes was started
     */
    List<RouteStartupOrder> getRouteStartupOrder();

    /**
     * Adds a {@link BootstrapCloseable} task.
     */
    void addBootstrap(BootstrapCloseable bootstrap);

    /**
     * Returns an unmodifiable list of the services registered currently in this {@link CamelContext}.
     */
    List<Service> getServices();

    /**
     * Gets the exchange factory to use.
     */
    ExchangeFactory getExchangeFactory();

    /**
     * Sets a custom exchange factory to use.
     */
    void setExchangeFactory(ExchangeFactory exchangeFactory);

    /**
     * Gets the exchange factory manager to use.
     */
    ExchangeFactoryManager getExchangeFactoryManager();

    /**
     * Sets a custom exchange factory manager to use.
     */
    void setExchangeFactoryManager(ExchangeFactoryManager exchangeFactoryManager);

    /**
     * Gets the processor exchange factory to use.
     */
    ProcessorExchangeFactory getProcessorExchangeFactory();

    /**
     * Sets a custom processor exchange factory to use.
     */
    void setProcessorExchangeFactory(ProcessorExchangeFactory processorExchangeFactory);

    /**
     * Returns the management mbean assembler
     *
     * @return the mbean assembler
     */
    ManagementMBeanAssembler getManagementMBeanAssembler();

    /**
     * Gets the default error handler builder which is inherited by the routes
     *
     * @return the builder
     */
    ErrorHandlerFactory getErrorHandlerFactory();

    /**
     * Sets the default error handler builder which is inherited by the routes
     *
     * @param errorHandlerFactory the builder
     */
    void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory);

    /**
     * Gets the default FactoryFinder which will be used for the loading the factory class from META-INF
     *
     * @return the default factory finder
     * @see    #getBootstrapFactoryFinder()
     */
    FactoryFinder getDefaultFactoryFinder();

    /**
     * Sets the default FactoryFinder which will be used for the loading the factory class from META-INF
     */
    void setDefaultFactoryFinder(FactoryFinder factoryFinder);

    /**
     * Gets the bootstrap FactoryFinder which will be used for the loading the factory class from META-INF. This
     * bootstrap factory finder is only intended to be used during bootstrap (starting) CamelContext.
     *
     * @return the bootstrap factory finder
     * @see    #getDefaultFactoryFinder()
     */
    FactoryFinder getBootstrapFactoryFinder();

    /**
     * Sets the bootstrap FactoryFinder which will be used for the loading the factory class from META-INF. This
     * bootstrap factory finder is only intended to be used during bootstrap (starting) CamelContext.
     *
     * @see #getDefaultFactoryFinder()
     */
    void setBootstrapFactoryFinder(FactoryFinder factoryFinder);

    /**
     * Gets the bootstrap FactoryFinder which will be used for the loading the factory class from META-INF in the given
     * path. This bootstrap factory finder is only intended to be used during bootstrap (starting) CamelContext.
     *
     * @param  path the META-INF path
     * @return      the bootstrap factory finder
     * @see         #getDefaultFactoryFinder()
     */
    FactoryFinder getBootstrapFactoryFinder(String path);

    /**
     * Gets the FactoryFinder which will be used for the loading the factory class from META-INF in the given path
     *
     * @param  path the META-INF path
     * @return      the factory finder
     */
    FactoryFinder getFactoryFinder(String path);

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
     * Setup management according to whether JMX is enabled or disabled.
     *
     * @param options optional parameters to configure {@link org.apache.camel.spi.ManagementAgent}.
     */
    void setupManagement(Map<String, Object> options);

    /**
     * Gets a list of {@link LogListener} (can be null if empty).
     */
    Set<LogListener> getLogListeners();

    /**
     * Adds a {@link LogListener}.
     */
    void addLogListener(LogListener listener);

    /**
     * Gets the {@link HeadersMapFactory} to use.
     */
    HeadersMapFactory getHeadersMapFactory();

    /**
     * Sets a custom {@link HeadersMapFactory} to be used.
     */
    void setHeadersMapFactory(HeadersMapFactory factory);

    /**
     * Gets the {@link ReactiveExecutor} to use.
     */
    ReactiveExecutor getReactiveExecutor();

    /**
     * Sets a custom {@link ReactiveExecutor} to be used.
     */
    void setReactiveExecutor(ReactiveExecutor reactiveExecutor);

    /**
     * Whether exchange event notification is applicable (possible). This API is used internally in Camel as
     * optimization.
     *
     * This is <b>only</b> for exchange events as this allows Camel to optimize to avoid preparing exchange events if
     * there are no event listeners that are listening for exchange events.
     */
    boolean isEventNotificationApplicable();

    /**
     * Used as internal optimization in Camel to flag whether exchange event notification is applicable or not.
     *
     * This is <b>only</b> for exchange events as this allows Camel to optimize to avoid preparing exchange events if
     * there are no event listeners that are listening for exchange events.
     */
    void setEventNotificationApplicable(boolean eventNotificationApplicable);

    /**
     * Internal {@link RouteController} that are only used internally by Camel to perform basic route operations. Do not
     * use this as end user.
     */
    RouteController getInternalRouteController();

    /**
     * Gets the {@link EndpointUriFactory} for the given component name.
     */
    EndpointUriFactory getEndpointUriFactory(String scheme);

    /**
     * Gets the {@link RuntimeCamelCatalog} if available on the classpath.
     */
    @Deprecated
    default RuntimeCamelCatalog getRuntimeCamelCatalog() {
        return getContextPlugin(RuntimeCamelCatalog.class);
    }

    /**
     * Gets the {@link StartupStepRecorder} to use.
     */
    StartupStepRecorder getStartupStepRecorder();

    /**
     * Sets the {@link StartupStepRecorder} to use.
     */
    void setStartupStepRecorder(StartupStepRecorder startupStepRecorder);

    /**
     * Internal API for adding routes. Do not use this as end user.
     */
    void addRoute(Route route);

    /**
     * Internal API for removing routes. Do not use this as end user.
     */
    void removeRoute(Route route);

    /**
     * Internal API for creating error handler. Do not use this as end user.
     */
    Processor createErrorHandler(Route route, Processor processor) throws Exception;

    /**
     * Danger!!! This will dispose the route model from the {@link CamelContext} which is used for lightweight mode.
     * This means afterwards no new routes can be dynamically added. Any operations on the
     * org.apache.camel.model.ModelCamelContext will return null or be a noop operation.
     */
    void disposeModel();

    /**
     * Used during unit-testing where it is possible to specify a set of routes to exclude from discovery
     */
    String getTestExcludeRoutes();

    /**
     * Parses the given text and resolve any property placeholders - using {{key}}.
     * <p/>
     * <b>Important:</b> If resolving placeholders on an endpoint uri, then you SHOULD use
     * EndpointHelper#resolveEndpointUriPropertyPlaceholders instead.
     *
     * @param  text                     the text such as an endpoint uri or the likes
     * @param  keepUnresolvedOptional   whether to keep placeholders that are optional and was unresolved
     * @return                          the text with resolved property placeholders
     * @throws IllegalArgumentException is thrown if property placeholders was used and there was an error resolving
     *                                  them
     */
    String resolvePropertyPlaceholders(String text, boolean keepUnresolvedOptional);

    /**
     * Package name to use as base (offset) for classpath scanning of RouteBuilder,
     * {@link org.apache.camel.TypeConverter}, {@link CamelConfiguration} classes, and also classes annotated with
     * {@link org.apache.camel.Converter}, or {@link org.apache.camel.BindToRegistry}.
     *
     * @return the base package name (can be null if not configured)
     */
    String getBasePackageScan();

    /**
     * Package name to use as base (offset) for classpath scanning of RouteBuilder,
     * {@link org.apache.camel.TypeConverter}, {@link CamelConfiguration} classes, and also classes annotated with
     * {@link org.apache.camel.Converter}, or {@link org.apache.camel.BindToRegistry}.
     *
     * @param basePackageScan the base package name
     */
    void setBasePackageScan(String basePackageScan);

    /**
     * The {@link CamelContext} have additional phases that are not defined in {@link ServiceStatus} and this method
     * provides the phase ordinal value.
     */
    byte getStatusPhase();

    /**
     * Gets a plugin of the given type.
     *
     * @param  type the type of the extension
     * @return      the extension, or <tt>null</tt> if no extension has been installed.
     */
    <T> T getContextPlugin(Class<T> type);

    /**
     * Allows installation of custom plugins to the Camel context.
     *
     * @param type   the type of the extension
     * @param module the instance of the extension
     */
    <T> void addContextPlugin(Class<T> type, T module);

    /**
     * Allows lazy installation of custom plugins to the Camel context.
     *
     * @param type   the type of the extension
     * @param module the instance of the extension
     */
    <T> void lazyAddContextPlugin(Class<T> type, Supplier<T> module);
}
