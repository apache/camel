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
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.StatefulService;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.AdviceWithTask;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.model.rest.RestBindingDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A Camel route
 *
 * @version
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "route")
@XmlType(propOrder = {"inputs", "inputType", "outputType", "outputs"})
@XmlAccessorType(XmlAccessType.PROPERTY)
// must use XmlAccessType.PROPERTY as there is some custom logic needed to be executed in the setter methods
public class RouteDefinition extends ProcessorDefinition<RouteDefinition> {
    private final AtomicBoolean prepared = new AtomicBoolean(false);
    private List<FromDefinition> inputs = new ArrayList<FromDefinition>();
    private List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();
    private String group;
    private String streamCache;
    private String trace;
    private String messageHistory;
    private String logMask;
    private String handleFault;
    private String delayer;
    private String autoStartup;
    private Integer startupOrder;
    private List<RoutePolicy> routePolicies;
    private String routePolicyRef;
    private ShutdownRoute shutdownRoute;
    private ShutdownRunningTask shutdownRunningTask;
    private String errorHandlerRef;
    private ErrorHandlerFactory errorHandlerBuilder;
    // keep state whether the error handler is context scoped or not
    // (will by default be context scoped of no explicit error handler configured)
    private boolean contextScopedErrorHandler = true;
    private Boolean rest;
    private RestDefinition restDefinition;
    private RestBindingDefinition restBindingDefinition;
    private InputTypeDefinition inputType;
    private OutputTypeDefinition outputType;

    public RouteDefinition() {
    }

    public RouteDefinition(@AsEndpointUri String uri) {
        from(uri);
    }

    public RouteDefinition(Endpoint endpoint) {
        from(endpoint);
    }

    /**
     * This route is created from the REST DSL.
     */
    public void fromRest(@AsEndpointUri String uri) {
        from(uri);
        rest = true;
    }

    /**
     * Prepares the route definition to be ready to be added to {@link CamelContext}
     *
     * @param context the camel context
     */
    public void prepare(ModelCamelContext context) {
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

    /**
     * Marks the route definition as un-prepared.
     * <p/>
     * This is needed if routes have been created by components such as
     * <tt>camel-scala</tt>. To unset the prepare so the routes can be prepared
     * at a later stage when scala has build the routes completely.
     */
    public void markUnprepared() {
        prepared.set(false);
    }

    @Override
    public String toString() {
        if (getId() != null) {
            return "Route(" + getId() + ")[" + inputs + " -> " + outputs + "]";
        } else {
            return "Route[" + inputs + " -> " + outputs + "]";
        }
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

    public List<RouteContext> addRoutes(ModelCamelContext camelContext, Collection<Route> routes) throws Exception {
        List<RouteContext> answer = new ArrayList<RouteContext>();

        @SuppressWarnings("deprecation")
        ErrorHandlerFactory handler = camelContext.getErrorHandlerBuilder();
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

    public RouteDefinition adviceWith(CamelContext camelContext, RouteBuilder builder) throws Exception {
        return adviceWith((ModelCamelContext)camelContext, builder);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is involved.
     * The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is needed to support this properly.
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
    @SuppressWarnings("deprecation")
    public RouteDefinition adviceWith(ModelCamelContext camelContext, RouteBuilder builder) throws Exception {
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
                && camelContext.getErrorHandlerBuilder() != builder.getRouteCollection().getErrorHandlerBuilder()) {
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
        log.info("AdviceWith route after: {}", merged);

        // If the camel context is started then we start the route
        if (camelContext instanceof StatefulService) {
            StatefulService service = (StatefulService) camelContext;
            if (service.isStarted()) {
                camelContext.startRoute(merged);
            }
        }
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
    public RouteDefinition from(@AsEndpointUri String uri) {
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
    public RouteDefinition from(@AsEndpointUri String... uris) {
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
     * Set the route description for this route
     *
     * @param description the route description
     * @return the builder
     */
    public RouteDefinition routeDescription(String description) {
        DescriptionDefinition desc = new DescriptionDefinition();
        desc.setText(description);
        setDescription(desc);
        return this;
    }

    /**
     * Disable stream caching for this route.
     *
     * @return the builder
     */
    public RouteDefinition noStreamCaching() {
        setStreamCache("false");
        return this;
    }

    /**
     * Enable stream caching for this route.
     *
     * @return the builder
     */
    public RouteDefinition streamCaching() {
        setStreamCache("true");
        return this;
    }

    /**
     * Enable stream caching for this route.
     *
     * @param streamCache whether to use stream caching (true or false), the value can be a property placeholder
     * @return the builder
     */
    public RouteDefinition streamCaching(String streamCache) {
        setStreamCache(streamCache);
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
     * Enable tracing for this route.
     *
     * @param tracing whether to use tracing (true or false), the value can be a property placeholder
     * @return the builder
     */
    public RouteDefinition tracing(String tracing) {
        setTrace(tracing);
        return this;
    }

    /**
     * Enable message history for this route.
     *
     * @return the builder
     */
    public RouteDefinition messageHistory() {
        setMessageHistory("true");
        return this;
    }

    /**
     * Enable message history for this route.
     *
     * @param messageHistory whether to use message history (true or false), the value can be a property placeholder
     * @return the builder
     */
    public RouteDefinition messageHistory(String messageHistory) {
        setMessageHistory(messageHistory);
        return this;
    }

    /**
     * Enable security mask for Logging on this route.
     *
     * @return the builder
     */
    public RouteDefinition logMask() {
        setLogMask("true");
        return this;
    }

    /**
     * Sets whether security mask for logging is enabled on this route.
     *
     * @param logMask whether to enable security mask for Logging (true or false), the value can be a property placeholder
     * @return the builder
     */
    public RouteDefinition logMask(String logMask) {
        setLogMask(logMask);
        return this;
    }

    /**
     * Disable message history for this route.
     *
     * @return the builder
     */
    public RouteDefinition noMessageHistory() {
        setMessageHistory("false");
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
    public RouteDefinition errorHandler(ErrorHandlerFactory errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        // we are now using a route scoped error handler
        contextScopedErrorHandler = false;
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
     * Sets the auto startup property on this route.
     *
     * @param autoStartup whether to auto startup (true or false), the value can be a property placeholder
     * @return the builder
     */
    public RouteDefinition autoStartup(String autoStartup) {
        setAutoStartup(autoStartup);
        return this;
    }

    /**
     * Sets the auto startup property on this route.
     *
     * @param autoStartup - boolean indicator
     * @return the builder
     */
    public RouteDefinition autoStartup(boolean autoStartup) {
        setAutoStartup(Boolean.toString(autoStartup));
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

    /**
     * Declare the expected data type of the input message. If the actual message type is different
     * at runtime, camel look for a required {@link Transformer} and apply if exists.
     * The type name consists of two parts, 'scheme' and 'name' connected with ':'. For Java type 'name'
     * is a fully qualified class name. For example {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see org.apache.camel.spi.Transformer
     *
     * @param urn input type URN
     * @return the builder
     */
    public RouteDefinition inputType(String urn) {
        inputType = new InputTypeDefinition();
        inputType.setUrn(urn);
        inputType.setValidate(false);
        return this;
    }

    /**
     * Declare the expected data type of the input message with content validation enabled.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists, and then applies {@link Validator} as well.
     * The type name consists of two parts, 'scheme' and 'name' connected with ':'. For Java type 'name'
     * is a fully qualified class name. For example {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see org.apache.camel.spi.Transformer
     * @see org.apache.camel.spi.Validator
     *
     * @param urn input type URN
     * @return the builder
     */
    public RouteDefinition inputTypeWithValidate(String urn) {
        inputType = new InputTypeDefinition();
        inputType.setUrn(urn);
        inputType.setValidate(true);
        return this;
    }

    /**
     * Declare the expected data type of the input message by Java class.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists.
     *
     * @see org.apache.camel.spi.Transformer
     *
     * @param clazz Class object of the input type
     * @return the builder
     */
    public RouteDefinition inputType(Class clazz) {
        inputType = new InputTypeDefinition();
        inputType.setJavaClass(clazz);
        inputType.setValidate(false);
        return this;
    }

    /**
     * Declare the expected data type of the input message by Java class with content validation enabled.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists, and then applies {@link Validator} as well.
     *
     * @see org.apache.camel.spi.Transformer
     * @see org.apache.camel.spi.Validator
     *
     * @param clazz Class object of the input type
     * @return the builder
     */
    public RouteDefinition inputTypeWithValidate(Class clazz) {
        inputType = new InputTypeDefinition();
        inputType.setJavaClass(clazz);
        inputType.setValidate(true);
        return this;
    }

    /**
     * Declare the expected data type of the output message. If the actual message type is different
     * at runtime, camel look for a required {@link Transformer} and apply if exists.
     * The type name consists of two parts, 'scheme' and 'name' connected with ':'. For Java type 'name'
     * is a fully qualified class name. For example {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see org.apache.camel.spi.Transformer
     *
     * @param urn output type URN
     * @return the builder
     */
    public RouteDefinition outputType(String urn) {
        outputType = new OutputTypeDefinition();
        outputType.setUrn(urn);
        outputType.setValidate(false);
        return this;
    }

    /**
     * Declare the expected data type of the output message with content validation enabled.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists, and then applies {@link Validator} as well.
     * The type name consists of two parts, 'scheme' and 'name' connected with ':'. For Java type 'name'
     * is a fully qualified class name. For example {@code java:java.lang.String}, {@code json:ABCOrder}.
     * 
     * @see org.apache.camel.spi.Transformer
     * @see org.apache.camel.spi.Validator
     *
     * @param urn output type URN
     * @return the builder
     */
    public RouteDefinition outputTypeWithValidate(String urn) {
        outputType = new OutputTypeDefinition();
        outputType.setUrn(urn);
        outputType.setValidate(true);
        return this;
    }

    /**
     * Declare the expected data type of the output message by Java class.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists.
     *
     * @see org.apache.camel.spi.Transformer
     *
     * @param clazz Class object of the output type
     * @return the builder
     */
    public RouteDefinition outputType(Class clazz) {
        outputType = new OutputTypeDefinition();
        outputType.setJavaClass(clazz);
        outputType.setValidate(false);
        return this;
    }

    /**
     * Declare the expected data type of the ouput message by Java class with content validation enabled.
     * If the actual message type is different at runtime, camel look for a required
     * {@link Transformer} and apply if exists, and then applies {@link Validator} as well.
     * 
     * @see org.apache.camel.spi.Transformer
     * @see org.apache.camel.spi.Validator
     * @param clazz Class object of the output type
     * @return the builder
     */
    public RouteDefinition outputTypeWithValidate(Class clazz) {
        outputType = new OutputTypeDefinition();
        outputType.setJavaClass(clazz);
        outputType.setValidate(true);
        return this;
    }

    // Properties
    // -----------------------------------------------------------------------

    public List<FromDefinition> getInputs() {
        return inputs;
    }

    /**
     * Input to the route.
     */
    @XmlElementRef
    public void setInputs(List<FromDefinition> inputs) {
        this.inputs = inputs;
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    /**
     * Outputs are processors that determines how messages are processed by this route.
     */
    @XmlElementRef
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;

        if (outputs != null) {
            for (ProcessorDefinition<?> output : outputs) {
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

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class
     * or be explicitly configured in the XML.
     * <p/>
     * May be null.
     */
    @XmlAttribute
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Whether stream caching is enabled on this route.
     */
    public String getStreamCache() {
        return streamCache;
    }

    /**
     * Whether stream caching is enabled on this route.
     */
    @XmlAttribute
    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    /**
     * Whether tracing is enabled on this route.
     */
    public String getTrace() {
        return trace;
    }

    /**
     * Whether tracing is enabled on this route.
     */
    @XmlAttribute
    public void setTrace(String trace) {
        this.trace = trace;
    }

    /**
     * Whether message history is enabled on this route.
     */
    public String getMessageHistory() {
        return messageHistory;
    }

    /**
     * Whether message history is enabled on this route.
     */
    @XmlAttribute @Metadata(defaultValue = "true")
    public void setMessageHistory(String messageHistory) {
        this.messageHistory = messageHistory;
    }

    /**
     * Whether security mask for Logging is enabled on this route.
     */
    public String getLogMask() {
        return logMask;
    }

    /**
     * Whether security mask for Logging is enabled on this route.
     */
    @XmlAttribute
    public void setLogMask(String logMask) {
        this.logMask = logMask;
    }

    /**
     * Whether handle fault is enabled on this route.
     */
    public String getHandleFault() {
        return handleFault;
    }

    /**
     * Whether handle fault is enabled on this route.
     */
    @XmlAttribute
    public void setHandleFault(String handleFault) {
        this.handleFault = handleFault;
    }

    /**
     * Whether to slow down processing messages by a given delay in msec.
     */
    public String getDelayer() {
        return delayer;
    }

    /**
     * Whether to slow down processing messages by a given delay in msec.
     */
    @XmlAttribute
    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    /**
     * Whether to auto start this route
     */
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

    /**
     * Whether to auto start this route
     */
    @XmlAttribute @Metadata(defaultValue = "true")
    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    /**
     * To configure the ordering of the routes being started
     */
    public Integer getStartupOrder() {
        return startupOrder;
    }

    /**
     * To configure the ordering of the routes being started
     */
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

    /**
     * Sets the bean ref name of the error handler builder to use on this route
     */
    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    /**
     * Sets the error handler if one is not already set
     */
    public void setErrorHandlerBuilderIfNull(ErrorHandlerFactory errorHandlerBuilder) {
        if (this.errorHandlerBuilder == null) {
            setErrorHandlerBuilder(errorHandlerBuilder);
        }
    }

    /**
     * Reference to custom {@link org.apache.camel.spi.RoutePolicy} to use by the route.
     * Multiple policies can be configured by separating values using comma.
     */
    @XmlAttribute
    public void setRoutePolicyRef(String routePolicyRef) {
        this.routePolicyRef = routePolicyRef;
    }

    /**
     * Reference to custom {@link org.apache.camel.spi.RoutePolicy} to use by the route.
     * Multiple policies can be configured by separating values using comma.
     */
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

    /**
     * To control how to shutdown the route.
     */
    @XmlAttribute @Metadata(defaultValue = "Default")
    public void setShutdownRoute(ShutdownRoute shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    /**
     * To control how to shutdown the route.
     */
    public ShutdownRunningTask getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    /**
     * To control how to shutdown the route.
     */
    @XmlAttribute @Metadata(defaultValue = "CompleteCurrentTaskOnly")
    public void setShutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    private ErrorHandlerFactory createErrorHandlerBuilder() {
        if (errorHandlerRef != null) {
            return new ErrorHandlerBuilderRef(errorHandlerRef);
        }

        // return a reference to the default error handler
        return new ErrorHandlerBuilderRef(ErrorHandlerBuilderRef.DEFAULT_ERROR_HANDLER_BUILDER);
    }

    @XmlTransient
    public ErrorHandlerFactory getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerFactory errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    @XmlAttribute
    public Boolean isRest() {
        return rest;
    }

    public RestDefinition getRestDefinition() {
        return restDefinition;
    }

    @XmlTransient
    public void setRestDefinition(RestDefinition restDefinition) {
        this.restDefinition = restDefinition;
    }

    public RestBindingDefinition getRestBindingDefinition() {
        return restBindingDefinition;
    }

    @XmlTransient
    public void setRestBindingDefinition(RestBindingDefinition restBindingDefinition) {
        this.restBindingDefinition = restBindingDefinition;
    }

    @SuppressWarnings("deprecation")
    public boolean isContextScopedErrorHandler(CamelContext context) {
        if (!contextScopedErrorHandler) {
            return false;
        }
        // if error handler ref is configured it may refer to a context scoped, so we need to check this first
        // the XML DSL will configure error handlers using refs, so we need this additional test
        if (errorHandlerRef != null) {
            ErrorHandlerFactory routeScoped = getErrorHandlerBuilder();
            ErrorHandlerFactory contextScoped = context.getErrorHandlerBuilder();
            return routeScoped != null && contextScoped != null && routeScoped == contextScoped;
        }

        return true;
    }

    @XmlElementRef(required = false)
    public void setInputType(InputTypeDefinition inputType) {
        this.inputType = inputType;
    }

    public InputTypeDefinition getInputType() {
        return this.inputType;
    }

    @XmlElementRef(required = false)
    public void setOutputType(OutputTypeDefinition outputType) {
        this.outputType = outputType;
    }

    public OutputTypeDefinition getOutputType() {
        return this.outputType;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
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

        // configure message history
        if (messageHistory != null) {
            Boolean isMessageHistory = CamelContextHelper.parseBoolean(camelContext, getMessageHistory());
            if (isMessageHistory != null) {
                routeContext.setMessageHistory(isMessageHistory);
                if (isMessageHistory) {
                    log.debug("Message history is enabled on route: {}", getId());
                }
            }
        }

        // configure Log EIP mask
        if (logMask != null) {
            Boolean isLogMask = CamelContextHelper.parseBoolean(camelContext, getLogMask());
            if (isLogMask != null) {
                routeContext.setLogMask(isLogMask);
                if (isLogMask) {
                    log.debug("Security mask for Logging is enabled on route: {}", getId());
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
        if (camelContext.getRoutePolicyFactories() != null) {
            for (RoutePolicyFactory factory : camelContext.getRoutePolicyFactories()) {
                RoutePolicy policy = factory.createRoutePolicy(camelContext, getId(), this);
                if (policy != null) {
                    log.debug("RoutePolicy is enabled: {} on route: {}", policy, getId());
                    routeContext.getRoutePolicyList().add(policy);
                }
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
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            strategy.onRouteContextCreate(routeContext);
        }

        // validate route has output processors
        if (!ProcessorDefinitionHelper.hasOutputs(outputs, true)) {
            RouteDefinition route = routeContext.getRoute();
            String at = fromType.toString();
            Exception cause = new IllegalArgumentException("Route " + route.getId() + " has no output processors."
                    + " You need to add outputs to the route such as to(\"log:foo\").");
            throw new FailedToCreateRouteException(route.getId(), route.toString(), at, cause);
        }

        List<ProcessorDefinition<?>> list = new ArrayList<ProcessorDefinition<?>>(outputs);
        for (ProcessorDefinition<?> output : list) {
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


    // ****************************
    // Static helpers
    // ****************************

    public static RouteDefinition fromUri(String uri) {
        return new RouteDefinition().from(uri);
    }

    public static RouteDefinition fromEndpoint(Endpoint endpoint) {
        return new RouteDefinition().from(endpoint);
    }

}
