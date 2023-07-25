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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.resume.ConsumerListener;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RouteError;
import org.apache.camel.spi.RoutePolicy;

/**
 * A <a href="http://camel.apache.org/routes.html">Route</a> defines the processing used on an inbound message exchange
 * from a specific {@link org.apache.camel.Endpoint} within a {@link org.apache.camel.CamelContext}.
 * <p/>
 * Use the API from {@link org.apache.camel.CamelContext} to control the lifecycle of a route, such as starting and
 * stopping using the {@link org.apache.camel.spi.RouteController#startRoute(String)} and
 * {@link org.apache.camel.spi.RouteController#stopRoute(String)} methods.
 */
public interface Route extends RuntimeConfiguration {

    String ID_PROPERTY = "id";
    String CUSTOM_ID_PROPERTY = "customId";
    String PARENT_PROPERTY = "parent";
    String GROUP_PROPERTY = "group";
    String REST_PROPERTY = "rest";
    String TEMPLATE_PROPERTY = "template";
    String DESCRIPTION_PROPERTY = "description";
    String CONFIGURATION_ID_PROPERTY = "configurationId";
    String SUPERVISED = "supervised";

    /**
     * Gets the route id
     *
     * @return the route id
     */
    String getId();

    /**
     * Whether the route id is custom assigned or auto assigned
     *
     * @return true if custom id, false if auto assigned id
     */
    boolean isCustomId();

    /**
     * Gets the route group
     *
     * @return the route group
     */
    String getGroup();

    /**
     * Gets the uptime in a human-readable format
     *
     * @return the uptime in days/hours/minutes
     */
    String getUptime();

    /**
     * Gets the uptime in milliseconds
     *
     * @return the uptime in milliseconds
     */
    long getUptimeMillis();

    /**
     * Gets the inbound {@link Consumer}
     *
     * @return the inbound consumer
     */
    Consumer getConsumer();

    /**
     * Gets the {@link Processor}
     *
     * @return the processor
     */
    Processor getProcessor();

    /**
     * Sets the {@link Processor}
     */
    void setProcessor(Processor processor);

    /**
     * Whether or not the route supports suspension (suspend and resume)
     *
     * @return <tt>true</tt> if this route supports suspension
     */
    boolean supportsSuspension();

    /**
     * This property map is used to associate information about the route.
     *
     * @return properties
     */
    Map<String, Object> getProperties();

    /**
     * Gets the route description (if any has been configured).
     * <p/>
     * The description is configured using the {@link #DESCRIPTION_PROPERTY} as key in the {@link #getProperties()}.
     *
     * @return the description, or <tt>null</tt> if no description has been configured.
     */
    String getDescription();

    /**
     * Gets the route configuration id(s) the route has been applied with. Multiple ids is separated by comma.
     * <p/>
     * The configuration ids is configured using the {@link #CONFIGURATION_ID_PROPERTY} as key in the
     * {@link #getProperties()}.
     *
     * @return the configuration, or <tt>null</tt> if no configuration has been configured.
     */
    String getConfigurationId();

    /**
     * Gets the source resource that this route is located from
     *
     * @return the source, or null if this route is not loaded from a resource
     */
    Resource getSourceResource();

    /**
     * The source:line-number where the route input is located in the source code
     */
    String getSourceLocation();

    /**
     * The source:line-number in short format that can be used for logging or summary purposes.
     */
    String getSourceLocationShort();

    /**
     * Gets the camel context
     *
     * @return the camel context
     */
    CamelContext getCamelContext();

    /**
     * Gets the input endpoint for this route.
     *
     * @return the endpoint
     */
    Endpoint getEndpoint();

    /**
     * A strategy callback allowing special initialization when services are initializing.
     *
     * @throws Exception is thrown in case of error
     */
    void initializeServices() throws Exception;

    /**
     * Returns the services for this particular route
     *
     * @return the services
     */
    List<Service> getServices();

    /**
     * Adds a service to this route
     *
     * @param service the service
     */
    void addService(Service service);

    /**
     * Returns a navigator to navigate this route by navigating all the {@link Processor}s.
     *
     * @return a navigator for {@link Processor}.
     */
    Navigate<Processor> navigate();

    /**
     * Returns a list of all the {@link Processor}s from this route that has id's matching the pattern
     *
     * @param  pattern the pattern to match by ids
     * @return         a list of {@link Processor}, is never <tt>null</tt>.
     */
    List<Processor> filter(String pattern);

    /**
     * Callback preparing the route to be started, by warming up the route.
     */
    void warmUp();

    /**
     * Gets the last error that happened during changing the route lifecycle, i.e. such as when an exception was thrown
     * during starting the route.
     * <p/>
     * This is only errors for route lifecycle changes, it is not exceptions thrown during routing exchanges by the
     * Camel routing engine.
     *
     * @return the error or <tt>null</tt> if no error
     */
    RouteError getLastError();

    /**
     * Sets the last error that happened during changing the route lifecycle, i.e. such as when an exception was thrown
     * during starting the route.
     * <p/>
     * This is only errors for route lifecycle changes, it is not exceptions thrown during routing exchanges by the
     * Camel routing engine.
     *
     * @param error the error
     */
    void setLastError(RouteError error);

    /**
     * Gets the route startup order
     */
    Integer getStartupOrder();

    /**
     * Sets the route startup order
     */
    void setStartupOrder(Integer startupOrder);

    /**
     * Gets the {@link RouteController} for this route.
     *
     * @return the route controller,
     */
    RouteController getRouteController();

    /**
     * Sets the {@link RouteController} for this route.
     *
     * @param controller the RouteController
     */
    void setRouteController(RouteController controller);

    /**
     * Sets whether the route should automatically start when Camel starts.
     * <p/>
     * Default is <tt>true</tt> to always start up.
     *
     * @param autoStartup whether to start up automatically.
     */
    void setAutoStartup(Boolean autoStartup);

    /**
     * Gets whether the route should automatically start when Camel starts.
     * <p/>
     * Default is <tt>true</tt> to always start up.
     *
     * @return <tt>true</tt> if route should automatically start
     */
    Boolean isAutoStartup();

    /**
     * Gets the route id
     */
    String getRouteId();

    /**
     * Gets the route description
     */
    String getRouteDescription();

    /**
     * Get the route type.
     *
     * Important: is null after the route has been created.
     *
     * @return the route type during creation of the route, is null after the route has been created.
     */
    NamedNode getRoute();

    /**
     * Clears the route model when its no longer needed.
     */
    void clearRouteModel();

    //
    // CREATION TIME
    //

    /**
     * This method retrieves the event driven Processors on this route context.
     */
    List<Processor> getEventDrivenProcessors();

    /**
     * This method retrieves the InterceptStrategy instances this route context.
     *
     * @return the strategy
     */
    List<InterceptStrategy> getInterceptStrategies();

    /**
     * Sets a special intercept strategy for management.
     * <p/>
     * Is by default used to correlate managed performance counters with processors when the runtime route is being
     * constructed
     *
     * @param interceptStrategy the managed intercept strategy
     */
    void setManagementInterceptStrategy(ManagementInterceptStrategy interceptStrategy);

    /**
     * Gets the special managed intercept strategy if any
     *
     * @return the managed intercept strategy, or <tt>null</tt> if not managed
     */
    ManagementInterceptStrategy getManagementInterceptStrategy();

    /**
     * Gets the route policy List
     *
     * @return the route policy list if any
     */
    List<RoutePolicy> getRoutePolicyList();

    // called at completion time
    void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory);

    // called at runtime
    ErrorHandlerFactory getErrorHandlerFactory();

    // called at runtime
    Collection<Processor> getOnCompletions();

    // called at completion time
    void setOnCompletion(String onCompletionId, Processor processor);

    // called at runtime
    Collection<Processor> getOnExceptions();

    // called at runtime
    Processor getOnException(String onExceptionId);

    // called at completion time
    void setOnException(String onExceptionId, Processor processor);

    /**
     * Adds error handler for the given exception type
     *
     * @param factory   the error handler factory
     * @param exception the exception to handle
     */
    void addErrorHandler(ErrorHandlerFactory factory, NamedNode exception);

    /**
     * Gets the error handlers
     *
     * @param factory the error handler factory
     */
    Set<NamedNode> getErrorHandlers(ErrorHandlerFactory factory);

    /**
     * Link the error handlers from a factory to another
     *
     * @param source the source factory
     * @param target the target factory
     */
    void addErrorHandlerFactoryReference(ErrorHandlerFactory source, ErrorHandlerFactory target);

    /**
     * Sets the resume strategy for the route
     */
    void setResumeStrategy(ResumeStrategy resumeStrategy);

    /**
     * Sets the consumer listener for the route
     */
    void setConsumerListener(ConsumerListener<?, ?> consumerListener);

}
