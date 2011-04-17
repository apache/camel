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
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.AdviceWithTask;
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
 * @version 
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"inputs", "outputs"})
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RouteDefinition extends ProcessorDefinition<RouteDefinition> {
    private final AtomicBoolean prepared = new AtomicBoolean(false);
    private List<FromDefinition> inputs = new ArrayList<FromDefinition>();
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    private String group;
    private String streamCache;
    private String trace;
    private String handleFault;
    private String delayer;
    private String autoStartup;
    private Integer startupOrder;
    private List<RoutePolicy> routePolicies;
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

    /**
     * Prepares the route definition to be ready to be added to {@link CamelContext}
     *
     * @param context the camel context
     */
    public void prepare(CamelContext context) {
        if (prepared.compareAndSet(false, true)) {
            RouteDefinitionHelper.prepareRoute(context, this);
        }
    }

    /**
     * Marks the route definition as prepared.
     * <p/>
     * This is needed if routes have been created by components such as
     * <tt>camel-spring</tt> or <tt>camel-blueprint</tt>.
     * Usually they share logic in the <tt>camel-core-xml</tt> module which prepares the routes.
     */
    public void markPrepared() {
        prepared.set(true);
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
     * You can use a regular {@link RouteBuilder} but the specialized {@link org.apache.camel.builder.AdviceWithRouteBuilder}
     * has additional features when using the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
     * We therefore suggest you to use the {@link org.apache.camel.builder.AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured
     * from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param camelContext the camel context
     * @param builder      the route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     * @see AdviceWithRouteBuilder
     */
    public RouteDefinition adviceWith(CamelContext camelContext, RouteBuilder builder) throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notNull(builder, "RouteBuilder");

        log.debug("AdviceWith route before: {}", this);

        // inject this route into the advice route builder so it can access this route
        // and offer features to manipulate the route directly
        if (builder instanceof AdviceWithRouteBuilder) {
            ((AdviceWithRouteBuilder) builder).setOriginalRoute(this);
        }

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        log.debug("AdviceWith routes: {}", routes);

        // we can only advice with a route builder without any routes
        if (!builder.getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("You can only advice from a RouteBuilder which has no existing routes."
                    + " Remove all routes from the route builder.");
        }
        // we can not advice with error handlers (if you added a new error handler in the route builder)
        // we must check the error handler on builder is not the same as on camel context, as that would be the default
        // context scoped error handler, in case no error handlers was configured
        if (builder.getRouteCollection().getErrorHandlerBuilder() != null
                && camelContext.getErrorHandlerBuilder() != builder.getRouteCollection().getErrorHandlerBuilder() ) {
            throw new IllegalArgumentException("You can not advice with error handlers. Remove the error handlers from the route builder.");
        }

        // stop and remove this existing route
        camelContext.removeRouteDefinition(this);

        // any advice with tasks we should execute first?
        if (builder instanceof AdviceWithRouteBuilder) {
            List<AdviceWithTask> tasks = ((AdviceWithRouteBuilder) builder).getAdviceWithTasks();
            for (AdviceWithTask task : tasks) {
                task.task();
            }
        }

        // now merge which also ensures that interceptors and the likes get mixed in correctly as well
        RouteDefinition merged = routes.route(this);

        // add the new merged route
        camelContext.getRouteDefinitions().add(0, merged);

        // log the merged route at info level to make it easier to end users to spot any mistakes they may have made
        log.info("AdviceWith route after: " + merged);

        // and start it
        camelContext.startRoute(merged);
        return merged;
    }

    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Creates an input to the route
     *
     * @param uri the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        getInputs().add(new FromDefinition(uri));
        return this;
    }

    /**
     * Creates an input to the route
     *
     * @param endpoint the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getInputs().add(new FromDefinition(endpoint));
        return this;
    }

    /**
     * Creates inputs to the route
     *
     * @param uris the from uris
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
     * @param endpoints the from endpoints
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
     * @param name the group name
     * @return the builder
     */
    public RouteDefinition group(String name) {
        setGroup(name);
        return this;
    }

    /**
     * Set the route id for this route
     *
     * @param id the route id
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
        setStreamCache("false");
        StreamCaching.noStreamCaching(getInterceptStrategies());
        return this;
    }

    /**
     * Enable stream caching for this route.
     *
     * @return the builder
     */
    public RouteDefinition streamCaching() {
        setStreamCache("true");
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
        setTrace("false");
        return this;
    }

    /**
     * Enable tracing for this route.
     *
     * @return the builder
     */
    public RouteDefinition tracing() {
        setTrace("true");
        return this;
    }

    /**
     * Disable handle fault for this route.
     *
     * @return the builder
     */
    public RouteDefinition noHandleFault() {
        setHandleFault("false");
        return this;
    }

    /**
     * Enable handle fault for this route.
     *
     * @return the builder
     */
    public RouteDefinition handleFault() {
        setHandleFault("true");
        return this;
    }

    /**
     * Disable delayer for this route.
     *
     * @return the builder
     */
    public RouteDefinition noDelayer() {
        setDelayer("0");
        return this;
    }

    /**
     * Enable delayer for this route.
     *
     * @param delay delay in millis
     * @return the builder
     */
    public RouteDefinition delayer(long delay) {
        setDelayer("" + delay);
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
        setAutoStartup("false");
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
     * Configures route policies for this route
     *
     * @param policies the route policies
     * @return the builder
     */
    public RouteDefinition routePolicy(RoutePolicy... policies) {
        if (routePolicies == null) {
            routePolicies = new ArrayList<RoutePolicy>();
        }
        for (RoutePolicy policy : policies) {
            routePolicies.add(policy);
        }
        return this;
    }

    /**
     * Configures a route policy for this route
     *
     * @param routePolicyRef reference to a {@link RoutePolicy} to lookup and use.
     *                       You can specify multiple references by separating using comma.
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

    public boolean isOutputSupported() {
        return true;
    }

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class
     * or be explicitly configured in the XML.
     * <p/>
     * May be null.
     */
    public String getGroup() {
        return group;
    }

    @XmlAttribute
    public void setGroup(String group) {
        this.group = group;
    }

    public String getStreamCache() {
        return streamCache;
    }

    @XmlAttribute
    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    public String getTrace() {
        return trace;
    }

    @XmlAttribute
    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getHandleFault() {
        return handleFault;
    }

    @XmlAttribute
    public void setHandleFault(String handleFault) {
        this.handleFault = handleFault;
    }

    public String getDelayer() {
        return delayer;
    }

    @XmlAttribute
    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    public String getAutoStartup() {
        return autoStartup;
    }

    public boolean isAutoStartup(CamelContext camelContext) throws Exception {
        if (getAutoStartup() == null) {
            // should auto startup by default
            return true;
        }
        Boolean isAutoStartup = CamelContextHelper.parseBoolean(camelContext, getAutoStartup());
        return isAutoStartup != null && isAutoStartup;
    }

    @XmlAttribute
    public void setAutoStartup(String autoStartup) {
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
    public void setErrorHandlerBuilderIfNull(ErrorHandlerBuilder errorHandlerBuilder) {
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

    public List<RoutePolicy> getRoutePolicies() {
        return routePolicies;
    }

    @XmlTransient
    public void setRoutePolicies(List<RoutePolicy> routePolicies) {
        this.routePolicies = routePolicies;
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
            Boolean isTrace = CamelContextHelper.parseBoolean(camelContext, getTrace());
            if (isTrace != null) {
                routeContext.setTracing(isTrace);
                if (isTrace) {
                    log.debug("Tracing is enabled on route: {}", getId());
                    // tracing is added in the DefaultChannel so we can enable it on the fly
                }
            }
        }

        // configure stream caching
        if (streamCache != null) {
            Boolean isStreamCache = CamelContextHelper.parseBoolean(camelContext, getStreamCache());
            if (isStreamCache != null) {
                routeContext.setStreamCaching(isStreamCache);
                if (isStreamCache) {
                    log.debug("StreamCaching is enabled on route: {}", getId());
                    // only add a new stream cache if not already a global configured on camel context
                    if (StreamCaching.getStreamCaching(camelContext) == null) {
                        addInterceptStrategy(new StreamCaching());
                    }
                }
            }
        }

        // configure handle fault
        if (handleFault != null) {
            Boolean isHandleFault = CamelContextHelper.parseBoolean(camelContext, getHandleFault());
            if (isHandleFault != null) {
                routeContext.setHandleFault(isHandleFault);
                if (isHandleFault) {
                    log.debug("HandleFault is enabled on route: {}", getId());
                    // only add a new handle fault if not already a global configured on camel context
                    if (HandleFault.getHandleFault(camelContext) == null) {
                        addInterceptStrategy(new HandleFault());
                    }
                }
            }
        }

        // configure delayer
        if (delayer != null) {
            Long delayer = CamelContextHelper.parseLong(camelContext, getDelayer());
            if (delayer != null) {
                routeContext.setDelayer(delayer);
                if (delayer > 0) {
                    log.debug("Delayer is enabled with: {} ms. on route: {}", delayer, getId());
                    addInterceptStrategy(new Delayer(delayer));
                } else {
                    log.debug("Delayer is disabled on route: {}", getId());
                }
            }
        }

        // configure route policy
        if (routePolicies != null && !routePolicies.isEmpty()) {
            for (RoutePolicy policy : routePolicies) {
                log.debug("RoutePolicy is enabled: {} on route: {}", policy, getId());
                routeContext.getRoutePolicyList().add(policy);
            }
        }
        if (routePolicyRef != null) {
            StringTokenizer policyTokens = new StringTokenizer(routePolicyRef, ",");
            while (policyTokens.hasMoreTokens()) {
                String ref = policyTokens.nextToken().trim();
                RoutePolicy policy = CamelContextHelper.mandatoryLookup(camelContext, ref, RoutePolicy.class);
                log.debug("RoutePolicy is enabled: {} on route: {}", policy, getId());
                routeContext.getRoutePolicyList().add(policy);
            }
        }

        // configure auto startup
        Boolean isAutoStartup = CamelContextHelper.parseBoolean(camelContext, getAutoStartup());
        if (isAutoStartup != null) {
            log.debug("Using AutoStartup {} on route: {}", isAutoStartup, getId());
            routeContext.setAutoStartup(isAutoStartup);
        }

        // configure shutdown
        if (shutdownRoute != null) {
            log.debug("Using ShutdownRoute {} on route: {}", getShutdownRoute(), getId());
            routeContext.setShutdownRoute(getShutdownRoute());
        }
        if (shutdownRunningTask != null) {
            log.debug("Using ShutdownRunningTask {} on route: {}", getShutdownRunningTask(), getId());
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
