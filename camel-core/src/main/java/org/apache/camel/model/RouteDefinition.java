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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.StreamCaching;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;route/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"inputs", "outputs" })
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RouteDefinition extends ProcessorDefinition<RouteDefinition> {
    private List<FromDefinition> inputs = new ArrayList<FromDefinition>();
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    private String group;
    private Boolean streamCache;
    private Boolean trace;
    private Boolean handleFault;
    private Long delayer;
    private Boolean autoStartup = Boolean.TRUE;
    private Integer startupOrder;
    private RoutePolicy routePolicy;
    private String routePolicyRef;
    private ShutdownRoute shutdownRoute;
    private ShutdownRunningTask shutdownRunningTask;

    public RouteDefinition() {
    }

    public RouteDefinition(String uri) {
        from(uri);
    }

    public RouteDefinition(Endpoint endpoint) {
        from(endpoint);
    }

    @Override
    public String toString() {
        return "Route[" + inputs + " -> " + outputs + "]";
    }

    @Override
    public String getShortName() {
        return "route";
    }

    /**
     * Returns the status of the route if it has been registered with a {@link CamelContext}
     */
    public ServiceStatus getStatus(CamelContext camelContext) {
        if (camelContext != null) {
            ServiceStatus answer = camelContext.getRouteStatus(this.getId());
            if (answer == null) {
                answer = ServiceStatus.Stopped;
            }
            return answer;
        }
        return null;
    }

    public boolean isStartable(CamelContext camelContext) {
        ServiceStatus status = getStatus(camelContext);
        if (status == null) {
            return true;
        } else {
            return status.isStartable();
        }
    }

    public boolean isStoppable(CamelContext camelContext) {
        ServiceStatus status = getStatus(camelContext);
        if (status == null) {
            return false;
        } else {
            return status.isStoppable();
        }
    }
    
    public List<RouteContext> addRoutes(CamelContext camelContext, Collection<Route> routes) throws Exception {
        List<RouteContext> answer = new ArrayList<RouteContext>();

        ErrorHandlerBuilder handler = camelContext.getErrorHandlerBuilder();
        if (handler != null) {
            setErrorHandlerBuilderIfNull(handler);
        }

        for (FromDefinition fromType : inputs) {
            RouteContext routeContext;
            try {
                routeContext = addRoutes(camelContext, routes, fromType);
            } catch (FailedToCreateRouteException e) {
                throw e;
            } catch (Exception e) {
                // wrap in exception which provide more details about which route was failing
                throw new FailedToCreateRouteException(getId(), toString(), e);
            }
            answer.add(routeContext);
        }
        return answer;
    }


    public Endpoint resolveEndpoint(CamelContext camelContext, String uri) throws NoSuchEndpointException {
        ObjectHelper.notNull(camelContext, "CamelContext");
        return CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured
     * from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param camelContext  the camel context
     * @param builder       the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     */
    public RouteDefinition adviceWith(CamelContext camelContext, RouteBuilder builder) throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notNull(builder, "RouteBuilder");

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        // we can only advice with a route builder without any routes
        if (!routes.getRoutes().isEmpty()) {
            throw new IllegalArgumentException("You can only advice from a RouteBuilder which has no existing routes."
                    + " Remove all routes from the route builder.");
        }

        // stop and remove this existing route
        camelContext.removeRouteDefinition(this);

        // now merge which also ensures that interceptors and the likes get mixed in correctly as well
        RouteDefinition merged = routes.route(this);

        // add the new merged route
        camelContext.getRouteDefinitions().add(0, merged);

        // and start it
        camelContext.startRoute(merged);
        return merged;
    }

    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Creates an input to the route
     *
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        getInputs().add(new FromDefinition(uri));
        return this;
    }

    /**
     * Creates an input to the route
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getInputs().add(new FromDefinition(endpoint));
        return this;
    }

    /**
     * Creates inputs to the route
     *
     * @param uris  the from uris
     * @return the builder
     */
    public RouteDefinition from(String... uris) {
        for (String uri : uris) {
            getInputs().add(new FromDefinition(uri));
        }
        return this;
    }

    /**
     * Creates inputs to the route
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            getInputs().add(new FromDefinition(endpoint));
        }
        return this;
    }

    /**
     * Set the group name for this route
     *
     * @param name  the group name
     * @return the builder
     */
    public RouteDefinition group(String name) {
        setGroup(name);
        return this;
    }

    /**
     * Set the route id for this route
     *
     * @param id  the route id
     * @return the builder
     */
    public RouteDefinition routeId(String id) {
        setId(id);
        return this;
    }

    /**
     * Disable stream caching for this route.
     * 
     * @return the builder
     */
    public RouteDefinition noStreamCaching() {
        setStreamCache(Boolean.FALSE);
        StreamCaching.noStreamCaching(getInterceptStrategies());
        return this;
    }

    /**
     * Enable stream caching for this route.
     * 
     * @return the builder
     */
    public RouteDefinition streamCaching() {
        setStreamCache(Boolean.TRUE);
        StreamCaching cache = StreamCaching.getStreamCaching(getInterceptStrategies());
        if (cache == null) {
            cache = new StreamCaching();
        }

        getInterceptStrategies().add(cache);
        return this;
    }

    /**
     * Disable tracing for this route.
     * 
     * @return the builder
     */
    public RouteDefinition noTracing() {
        setTrace(false);
        return this;
    }

    /**
     * Enable tracing for this route.
     * 
     * @return the builder
     */
    public RouteDefinition tracing() {
        setTrace(true);
        return this;
    }

    /**
     * Disable handle fault for this route.
     * 
     * @return the builder
     */
    public RouteDefinition noHandleFault() {
        setHandleFault(false);
        return this;
    }

    /**
     * Enable handle fault for this route.
     * 
     * @return the builder
     */
    public RouteDefinition handleFault() {
        setHandleFault(true);
        return this;
    }

    /**
     * Disable delayer for this route.
     * 
     * @return the builder
     */
    public RouteDefinition noDelayer() {
        setDelayer(0L);
        return this;
    }

    /**
     * Enable delayer for this route.
     *
     * @param delay delay in millis
     * @return the builder
     */
    public RouteDefinition delayer(long delay) {
        setDelayer(delay);
        return this;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder.
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteDefinition errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Disables this route from being auto started when Camel starts.
     * 
     * @return the builder
     */
    public RouteDefinition noAutoStartup() {
        setAutoStartup(Boolean.FALSE);
        return this;
    }

    /**
     * Configures the startup order for this route
     * <p/>
     * Camel will reorder routes and star them ordered by 0..N where 0 is the lowest number and N the highest number.
     * Camel will stop routes in reverse order when its stopping.
     *
     * @param order the order represented as a number
     * @return the builder
     */
    public RouteDefinition startupOrder(int order) {
        setStartupOrder(order);
        return this;
    }

    /**
     * Configures a route policy for this route
     *
     * @param routePolicy the route policy
     * @return the builder
     */ 
    public RouteDefinition routePolicy(RoutePolicy routePolicy) {
        setRoutePolicy(routePolicy);
        return this;
    }

    /**
     * Configures a route policy for this route
     *
     * @param routePolicyRef reference to a {@link RoutePolicy} to lookup and use.
     * @return the builder
     */
    public RouteDefinition routePolicyRef(String routePolicyRef) {
        setRoutePolicyRef(routePolicyRef);
        return this;
    }

    /**
     * Configures a shutdown route option.
     *
     * @param shutdownRoute the option to use when shutting down this route
     * @return the builder
     */
    public RouteDefinition shutdownRoute(ShutdownRoute shutdownRoute) {
        setShutdownRoute(shutdownRoute);
        return this;
    }

    /**
     * Configures a shutdown running task option.
     *
     * @param shutdownRunningTask the option to use when shutting down and how to act upon running tasks.
     * @return the builder
     */
    public RouteDefinition shutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        setShutdownRunningTask(shutdownRunningTask);
        return this;
    }

    // Properties
    // -----------------------------------------------------------------------

    public List<FromDefinition> getInputs() {
        return inputs;
    }

    @XmlElementRef
    public void setInputs(List<FromDefinition> inputs) {
        this.inputs = inputs;
    }

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;

        if (outputs != null) {
            for (ProcessorDefinition output : outputs) {
                configureChild(output);
            }
        }
    }

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class
     * or be explicitly configured in the XML.
     *
     * May be null.
     */
    public String getGroup() {
        return group;
    }

    @XmlAttribute
    public void setGroup(String group) {
        this.group = group;
    }

    public Boolean isStreamCache() {
        return streamCache;
    }

    @XmlAttribute
    public void setStreamCache(Boolean streamCache) {
        this.streamCache = streamCache;
    }

    public Boolean isTrace() {
        return trace;
    }

    @XmlAttribute
    public void setTrace(Boolean trace) {
        this.trace = trace;
    }

    public Boolean isHandleFault() {
        return handleFault;
    }

    @XmlAttribute
    public void setHandleFault(Boolean handleFault) {
        this.handleFault = handleFault;
    }

    public Long getDelayer() {
        return delayer;
    }

    @XmlAttribute
    public void setDelayer(Long delayer) {
        this.delayer = delayer;
    }

    public Boolean isAutoStartup() {
        return autoStartup;
    }

    @XmlAttribute
    public void setAutoStartup(Boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public Integer getStartupOrder() {
        return startupOrder;
    }

    @XmlAttribute
    public void setStartupOrder(Integer startupOrder) {
        this.startupOrder = startupOrder;
    }

    /**
     * Sets the bean ref name of the error handler builder to use on this route
     */
    @XmlAttribute
    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
        // we use an specific error handler ref (from Spring DSL) then wrap that
        // with a error handler build ref so Camel knows its not just the default one
        setErrorHandlerBuilder(new ErrorHandlerBuilderRef(errorHandlerRef));
    }

    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    /**
     * Sets the error handler if one is not already set
     */
    protected void setErrorHandlerBuilderIfNull(ErrorHandlerBuilder errorHandlerBuilder) {
        if (this.errorHandlerBuilder == null) {
            setErrorHandlerBuilder(errorHandlerBuilder);
        }
    }

    @XmlAttribute
    public void setRoutePolicyRef(String routePolicyRef) {
        this.routePolicyRef = routePolicyRef;
    }

    public String getRoutePolicyRef() {
        return routePolicyRef;
    }

    @XmlTransient
    public void setRoutePolicy(RoutePolicy routePolicy) {
        this.routePolicy = routePolicy;
    }

    public RoutePolicy getRoutePolicy() {
        return routePolicy;
    }

    public ShutdownRoute getShutdownRoute() {
        return shutdownRoute;
    }

    @XmlAttribute
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    @XmlAttribute
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    protected RouteContext addRoutes(CamelContext camelContext, Collection<Route> routes, FromDefinition fromType) throws Exception {
        RouteContext routeContext = new DefaultRouteContext(camelContext, this, fromType, routes);

        // configure tracing
        if (trace != null) {
            routeContext.setTracing(isTrace());
            if (isTrace()) {
                if (log.isDebugEnabled()) {
                    log.debug("Tracing is enabled on route: " + this);
                }
                // tracing is added in the DefaultChannel so we can enable it on the fly
            }
        }

        // configure stream caching
        if (streamCache != null) {
            routeContext.setStreamCaching(isStreamCache());
            if (isStreamCache()) {
                if (log.isDebugEnabled()) {
                    log.debug("StreamCaching is enabled on route: " + this);
                }
                // only add a new stream cache if not already a global configured on camel context
                if (StreamCaching.getStreamCaching(camelContext) == null) {
                    addInterceptStrategy(new StreamCaching());
                }
            }
        }

        // configure handle fault
        if (handleFault != null) {
            routeContext.setHandleFault(isHandleFault());
            if (isHandleFault()) {
                if (log.isDebugEnabled()) {
                    log.debug("HandleFault is enabled on route: " + this);
                }
                // only add a new handle fault if not already a global configured on camel context
                if (HandleFault.getHandleFault(camelContext) == null) {
                    addInterceptStrategy(new HandleFault());
                }
            }
        }

        // configure delayer
        if (delayer != null) {
            routeContext.setDelayer(getDelayer());
            if (getDelayer() != null) {
                long millis = getDelayer();
                if (millis > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Delayer is enabled with: " + millis + " ms. on route: " + this);
                    }
                    addInterceptStrategy(new Delayer(millis));
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Delayer is disabled on route: " + this);
                    }
                }
            }
        }

        // configure route policy
        if (routePolicy != null) {
            if (log.isDebugEnabled()) {
                log.debug("RoutePolicy is enabled: " + routePolicy + " on route: " + this);
            }
            routeContext.setRoutePolicy(getRoutePolicy());
        } else if (routePolicyRef != null) {
            RoutePolicy policy = CamelContextHelper.mandatoryLookup(camelContext, routePolicyRef, RoutePolicy.class);
            if (log.isDebugEnabled()) {
                log.debug("RoutePolicy is enabled: " + policy + " on route: " + this);
            }
            routeContext.setRoutePolicy(policy);
        }

        // configure auto startup
        if (autoStartup != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using AutoStartup " + isAutoStartup() + " on route: " + this);
            }
            routeContext.setAutoStartup(isAutoStartup());
        }

        // configure shutdown
        if (shutdownRoute != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using ShutdownRoute " + getShutdownRoute() + " on route: " + this);
            }
            routeContext.setShutdownRoute(getShutdownRoute());
        }
        if (shutdownRunningTask != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using ShutdownRunningTask " + getShutdownRunningTask() + " on route: " + this);
            }
            routeContext.setShutdownRunningTask(getShutdownRunningTask());
        }

        // should inherit the intercept strategies we have defined
        routeContext.setInterceptStrategies(this.getInterceptStrategies());
        // force endpoint resolution
        routeContext.getEndpoint();
        if (camelContext != null) {
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onRouteContextCreate(routeContext);
            }
        }

        // validate route has output processors
        if (!ProcessorDefinitionHelper.hasOutputs(outputs, true)) {
            RouteDefinition route = routeContext.getRoute();
            String at = fromType.toString();
            Exception cause = new IllegalArgumentException("Route " + route.getId() + " has no output processors."
                    + " You need to add outputs to the route such as to(\"log:foo\").");
            throw new FailedToCreateRouteException(route.getId(), route.toString(), at, cause);
        }

        List<ProcessorDefinition> list = new ArrayList<ProcessorDefinition>(outputs);
        for (ProcessorDefinition output : list) {
            try {
                output.addRoutes(routeContext, routes);
            } catch (Exception e) {
                RouteDefinition route = routeContext.getRoute();
                throw new FailedToCreateRouteException(route.getId(), route.toString(), output.toString(), e);
            }
        }

        routeContext.commit();
        return routeContext;
    }

}
