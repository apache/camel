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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.NamedRoute;
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.rest.RestBindingDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.spi.RoutePolicy;

/**
 * A Camel route
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "route")
@XmlType(propOrder = { "routeProperties", "input", "inputType", "outputType", "outputs" })
@XmlAccessorType(XmlAccessType.PROPERTY)
// must use XmlAccessType.PROPERTY as there is some custom logic needed to be executed in the setter methods
public class RouteDefinition extends OutputDefinition<RouteDefinition>
        implements NamedRoute, PreconditionContainer, ResourceAware {
    private final AtomicBoolean prepared = new AtomicBoolean();
    private FromDefinition input;
    private String routeConfigurationId;
    private transient Set<String> appliedRouteConfigurationIds;
    private String group;
    private String nodePrefixId;
    private String streamCache;
    private String trace;
    private String messageHistory;
    private String logMask;
    private String delayer;
    private String autoStartup;
    private Integer startupOrder;
    private List<RoutePolicy> routePolicies;
    private String routePolicyRef;
    private String shutdownRoute;
    private String shutdownRunningTask;
    private String errorHandlerRef;
    private ErrorHandlerFactory errorHandlerFactory;
    // keep state whether the error handler is context scoped or not
    // (will by default be context scoped of no explicit error handler
    // configured)
    private boolean contextScopedErrorHandler = true;
    private Boolean rest;
    private Boolean template;
    private RestDefinition restDefinition;
    private RestBindingDefinition restBindingDefinition;
    private InputTypeDefinition inputType;
    private OutputTypeDefinition outputType;
    private List<PropertyDefinition> routeProperties;
    private Map<String, Object> templateParameters;
    private Map<String, Object> templateDefaultParameters;
    private RouteTemplateContext routeTemplateContext;
    private Resource resource;
    private String precondition;

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
        if (uri != null) {
            from(uri);
        }
        rest = true;
    }

    /**
     * Check if the route has been prepared
     *
     * @return whether the route has been prepared or not
     * @see    RouteDefinitionHelper#prepareRoute(CamelContext, RouteDefinition)
     */
    public boolean isPrepared() {
        return prepared.get();
    }

    /**
     * Marks the route definition as prepared.
     * <p/>
     * This is needed if routes have been created by components such as camel-spring-xml or camel-blueprint. Usually
     * they share logic in the camel-core-xml module which prepares the routes.
     */
    public void markPrepared() {
        prepared.set(true);
    }

    /**
     * Marks the route definition as un-prepared.
     */
    public void markUnprepared() {
        prepared.set(false);
    }

    /**
     * Reset internal state before preparing route
     */
    public void resetPrepare() {
        appliedRouteConfigurationIds = null;
    }

    @Override
    public String toString() {
        if (getId() != null) {
            return "Route(" + getId() + ")[" + (input != null ? input : "") + " -> " + outputs + "]";
        } else {
            return "Route[" + input + " -> " + outputs + "]";
        }
    }

    @Override
    public String getShortName() {
        return "route";
    }

    @Override
    public String getLabel() {
        return "Route[" + input.getLabel() + "]";
    }

    @Override
    public String getRouteId() {
        return getId();
    }

    @Override
    public String getEndpointUrl() {
        return input != null ? input.getEndpointUri() : null;
    }

    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Creates an input to the route
     *
     * @param  uri the from uri
     * @return     the builder
     */
    public RouteDefinition from(@AsEndpointUri String uri) {
        setInput(new FromDefinition(uri));
        return this;
    }

    /**
     * Creates an input to the route
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        setInput(new FromDefinition(endpoint));
        return this;
    }

    /**
     * Creates an input to the route
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(EndpointConsumerBuilder endpoint) {
        setInput(new FromDefinition(endpoint));
        return this;
    }

    /**
     * The route configuration id or pattern this route should use for configuration. Multiple id/pattern can be
     * separated by comma.
     *
     * @param  routeConfigurationId id or pattern
     * @return                      the builder
     */
    public RouteDefinition routeConfigurationId(String routeConfigurationId) {
        setRouteConfigurationId(routeConfigurationId);
        return this;
    }

    /**
     * Set the group name for this route
     *
     * @param  name the group name
     * @return      the builder
     */
    public RouteDefinition group(String name) {
        setGroup(name);
        return this;
    }

    /**
     * Set the route group for this route
     *
     * @param  group the route group
     * @return       the builder
     */
    @Override
    public RouteDefinition routeGroup(String group) {
        setGroup(group);
        return this;
    }

    /**
     * Set the route id for this route
     *
     * @param  id the route id
     * @return    the builder
     */
    @Override
    public RouteDefinition routeId(String id) {
        if (hasCustomIdAssigned()) {
            throw new IllegalArgumentException("You can only set routeId one time per route.");
        }
        setId(id);
        return this;
    }

    /**
     * Set the route description for this route
     *
     * @param  description the route description
     * @return             the builder
     */
    @Override
    public RouteDefinition routeDescription(String description) {
        setDescription(description);
        return this;
    }

    /**
     * Sets a prefix to use for all node ids (not route id).
     *
     * @param  prefixId the prefix
     * @return          the builder
     */
    @Override
    public RouteDefinition nodePrefixId(String prefixId) {
        setNodePrefixId(prefixId);
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
     * @param  streamCache whether to use stream caching (true or false), the value can be a property placeholder
     * @return             the builder
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
     * @param  tracing whether to use tracing (true or false), the value can be a property placeholder
     * @return         the builder
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
     * @param  messageHistory whether to use message history (true or false), the value can be a property placeholder
     * @return                the builder
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
     * @param  logMask whether to enable security mask for Logging (true or false), the value can be a property
     *                 placeholder
     * @return         the builder
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
     * @param  delay delay in millis
     * @return       the builder
     */
    public RouteDefinition delayer(long delay) {
        setDelayer(Long.toString(delay));
        return this;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder.
     *
     * @param  ref reference to existing error handler
     * @return     the current builder with the error handler configured
     */
    public RouteDefinition errorHandler(String ref) {
        setErrorHandlerRef(ref);
        // we are now using a route scoped error handler
        contextScopedErrorHandler = false;
        return this;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder.
     *
     * @param  errorHandlerBuilder the error handler to be used by default for all child routes
     * @return                     the current builder with the error handler configured
     */
    public RouteDefinition errorHandler(ErrorHandlerFactory errorHandlerBuilder) {
        setErrorHandlerFactory(errorHandlerBuilder);
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
     * @param  autoStartup whether to auto startup (true or false), the value can be a property placeholder
     * @return             the builder
     */
    public RouteDefinition autoStartup(String autoStartup) {
        setAutoStartup(autoStartup);
        return this;
    }

    /**
     * Sets the auto startup property on this route.
     *
     * @param  autoStartup - boolean indicator
     * @return             the builder
     */
    public RouteDefinition autoStartup(boolean autoStartup) {
        setAutoStartup(Boolean.toString(autoStartup));
        return this;
    }

    /**
     * Sets the predicate of the precondition in simple language to evaluate in order to determine if this route should
     * be included or not.
     *
     * @param  precondition the predicate corresponding to the test to evaluate.
     * @return              the builder
     */
    public RouteDefinition precondition(String precondition) {
        setPrecondition(precondition);
        return this;
    }

    /**
     * Configures the startup order for this route
     * <p/>
     * Camel will reorder routes and star them ordered by 0..N where 0 is the lowest number and N the highest number.
     * Camel will stop routes in reverse order when its stopping.
     *
     * @param  order the order represented as a number
     * @return       the builder
     */
    @Override
    public RouteDefinition startupOrder(int order) {
        setStartupOrder(order);
        return this;
    }

    /**
     * Configures route policies for this route
     *
     * @param  policies the route policies
     * @return          the builder
     */
    public RouteDefinition routePolicy(RoutePolicy... policies) {
        if (routePolicies == null) {
            routePolicies = new ArrayList<>();
        }
        routePolicies.addAll(Arrays.asList(policies));
        return this;
    }

    /**
     * Configures route policy for this route
     *
     * @param  policy route policy
     * @return        the builder
     */
    public RouteDefinition routePolicy(Supplier<RoutePolicy> policy) {
        return routePolicy(policy.get());
    }

    /**
     * Configures a route policy for this route
     *
     * @param  routePolicyRef reference to a {@link RoutePolicy} to lookup and use. You can specify multiple references
     *                        by separating using comma.
     * @return                the builder
     */
    public RouteDefinition routePolicyRef(String routePolicyRef) {
        setRoutePolicyRef(routePolicyRef);
        return this;
    }

    /**
     * Configures a shutdown route option.
     *
     * @param  shutdownRoute the option to use when shutting down this route
     * @return               the builder
     */
    public RouteDefinition shutdownRoute(ShutdownRoute shutdownRoute) {
        return shutdownRoute(shutdownRoute.name());
    }

    /**
     * Configures a shutdown route option.
     *
     * @param  shutdownRoute the option to use when shutting down this route
     * @return               the builder
     */
    public RouteDefinition shutdownRoute(String shutdownRoute) {
        setShutdownRoute(shutdownRoute);
        return this;
    }

    /**
     * Configures a shutdown running task option.
     *
     * @param  shutdownRunningTask the option to use when shutting down and how to act upon running tasks.
     * @return                     the builder
     */
    public RouteDefinition shutdownRunningTask(ShutdownRunningTask shutdownRunningTask) {
        return shutdownRunningTask(shutdownRunningTask.name());
    }

    /**
     * Configures a shutdown running task option.
     *
     * @param  shutdownRunningTask the option to use when shutting down and how to act upon running tasks.
     * @return                     the builder
     */
    public RouteDefinition shutdownRunningTask(String shutdownRunningTask) {
        setShutdownRunningTask(shutdownRunningTask);
        return this;
    }

    /**
     * Declare the expected data type of the input message. If the actual message type is different at runtime, camel
     * look for a required {@link org.apache.camel.spi.Transformer} and apply if exists. The type name consists of two
     * parts, 'scheme' and 'name' connected with ':'. For Java type 'name' is a fully qualified class name. For example
     * {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see        org.apache.camel.spi.Transformer
     * @param  urn input type URN
     * @return     the builder
     */
    public RouteDefinition inputType(String urn) {
        inputType = new InputTypeDefinition().urn(urn).validate(false);
        return this;
    }

    /**
     * Declare the expected data type of the input message with content validation enabled. If the actual message type
     * is different at runtime, camel look for a required {@link org.apache.camel.spi.Transformer} and apply if exists,
     * and then applies {@link org.apache.camel.spi.Validator} as well. The type name consists of two parts, 'scheme'
     * and 'name' connected with ':'. For Java type 'name' is a fully qualified class name. For example
     * {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see        org.apache.camel.spi.Transformer
     * @see        org.apache.camel.spi.Validator
     * @param  urn input type URN
     * @return     the builder
     */
    public RouteDefinition inputTypeWithValidate(String urn) {
        inputType = new InputTypeDefinition().urn(urn).validate(true);
        return this;
    }

    /**
     * Declare the expected data type of the input message by Java class. If the actual message type is different at
     * runtime, camel look for a required {@link org.apache.camel.spi.Transformer} and apply if exists.
     *
     * @see          org.apache.camel.spi.Transformer
     * @param  clazz Class object of the input type
     * @return       the builder
     */
    public RouteDefinition inputType(Class<?> clazz) {
        inputType = new InputTypeDefinition().javaClass(clazz).validate(false);
        return this;
    }

    /**
     * Declare the expected data type of the input message by Java class with content validation enabled. If the actual
     * message type is different at runtime, camel look for a required {@link org.apache.camel.spi.Transformer} and
     * apply if exists, and then applies {@link org.apache.camel.spi.Validator} as well.
     *
     * @see          org.apache.camel.spi.Transformer
     * @see          org.apache.camel.spi.Validator
     * @param  clazz Class object of the input type
     * @return       the builder
     */
    public RouteDefinition inputTypeWithValidate(Class<?> clazz) {
        inputType = new InputTypeDefinition().javaClass(clazz).validate(true);
        return this;
    }

    /**
     * Declare the expected data type of the output message. If the actual message type is different at runtime, camel
     * look for a required {@link org.apache.camel.spi.Transformer} and apply if exists. The type name consists of two
     * parts, 'scheme' and 'name' connected with ':'. For Java type 'name' is a fully qualified class name. For example
     * {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see        org.apache.camel.spi.Transformer
     * @param  urn output type URN
     * @return     the builder
     */
    public RouteDefinition outputType(String urn) {
        outputType = new OutputTypeDefinition().urn(urn).validate(false);
        return this;
    }

    /**
     * Declare the expected data type of the output message with content validation enabled. If the actual message type
     * is different at runtime, Camel look for a required {@link org.apache.camel.spi.Transformer} and apply if exists,
     * and then applies {@link org.apache.camel.spi.Validator} as well. The type name consists of two parts, 'scheme'
     * and 'name' connected with ':'. For Java type 'name' is a fully qualified class name. For example
     * {@code java:java.lang.String}, {@code json:ABCOrder}.
     *
     * @see        org.apache.camel.spi.Transformer
     * @see        org.apache.camel.spi.Validator
     * @param  urn output type URN
     * @return     the builder
     */
    public RouteDefinition outputTypeWithValidate(String urn) {
        outputType = new OutputTypeDefinition().urn(urn).validate(true);
        return this;
    }

    /**
     * Declare the expected data type of the output message by Java class. If the actual message type is different at
     * runtime, camel look for a required {@link org.apache.camel.spi.Transformer} and apply if exists.
     *
     * @see          org.apache.camel.spi.Transformer
     * @param  clazz Class object of the output type
     * @return       the builder
     */
    public RouteDefinition outputType(Class<?> clazz) {
        outputType = new OutputTypeDefinition().javaClass(clazz).validate(false);
        return this;
    }

    /**
     * Declare the expected data type of the output message by Java class with content validation enabled. If the actual
     * message type is different at runtime, camel look for a required {@link org.apache.camel.spi.Transformer} and
     * apply if exists, and then applies {@link org.apache.camel.spi.Validator} as well.
     *
     * @see          org.apache.camel.spi.Transformer
     * @see          org.apache.camel.spi.Validator
     * @param  clazz Class object of the output type
     * @return       the builder
     */
    public RouteDefinition outputTypeWithValidate(Class<?> clazz) {
        outputType = new OutputTypeDefinition().javaClass(clazz).validate(true);
        return this;
    }

    /**
     * Adds a custom property on the route.
     */
    public RouteDefinition routeProperty(String key, String value) {
        if (routeProperties == null) {
            routeProperties = new ArrayList<>();
        }

        PropertyDefinition prop = new PropertyDefinition();
        prop.setKey(key);
        prop.setValue(value);

        routeProperties.add(prop);

        return this;
    }

    public Map<String, Object> getTemplateParameters() {
        return templateParameters;
    }

    @XmlTransient
    public void setTemplateParameters(Map<String, Object> templateParameters) {
        this.templateParameters = templateParameters;
    }

    public Map<String, Object> getTemplateDefaultParameters() {
        return templateDefaultParameters;
    }

    @XmlTransient
    public void setTemplateDefaultParameters(Map<String, Object> templateDefaultParameters) {
        this.templateDefaultParameters = templateDefaultParameters;
    }

    public RouteTemplateContext getRouteTemplateContext() {
        return routeTemplateContext;
    }

    @XmlTransient
    public void setRouteTemplateContext(RouteTemplateContext routeTemplateContext) {
        this.routeTemplateContext = routeTemplateContext;
    }

    public Resource getResource() {
        return resource;
    }

    @XmlTransient
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public FromDefinition getInput() {
        return input;
    }

    /**
     * Input to the route.
     */
    @XmlElementRef(required = false)
    public void setInput(FromDefinition input) {
        if (this.input != null && input != null && this.input != input) {
            throw new IllegalArgumentException("Only one input is allowed per route. Cannot accept input: " + input);
        }
        // required = false: in rest-dsl you can embed an in-lined route which
        // does not have a <from> as it is implied to be the rest endpoint
        this.input = input;

        if (getCamelContext() != null && (getCamelContext().isSourceLocationEnabled() || getCamelContext().isDebugging()
                || getCamelContext().isTracing())) {
            // we want to capture source location:line for every output
            ProcessorDefinitionHelper.prepareSourceLocation(getResource(), input);
        }
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    /**
     * Outputs are processors that determines how messages are processed by this route.
     */
    @XmlElementRef
    @Override
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

    /**
     * The route configuration id or pattern this route should use for configuration. Multiple id/pattern can be
     * separated by comma.
     */
    public String getRouteConfigurationId() {
        return routeConfigurationId;
    }

    /**
     * The route configuration id or pattern this route should use for configuration. Multiple id/pattern can be
     * separated by comma.
     */
    @XmlAttribute
    public void setRouteConfigurationId(String routeConfigurationId) {
        this.routeConfigurationId = routeConfigurationId;
    }

    /**
     * This is used internally by Camel to keep track which route configurations is applied when creating a route from
     * this model.
     *
     * This method is not intended for Camel end users.
     */
    public void addAppliedRouteConfigurationId(String routeConfigurationId) {
        if (appliedRouteConfigurationIds == null) {
            appliedRouteConfigurationIds = new LinkedHashSet<>();
        }
        appliedRouteConfigurationIds.add(routeConfigurationId);
    }

    /**
     * This is used internally by Camel to keep track which route configurations is applied when creating a route from
     * this model.
     *
     * This method is not intended for Camel end users.
     */
    public Set<String> getAppliedRouteConfigurationIds() {
        return appliedRouteConfigurationIds;
    }

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class or be explicitly configured in
     * the XML.
     * <p/>
     * May be null.
     */
    public String getGroup() {
        return group;
    }

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class or be explicitly configured in
     * the XML.
     * <p/>
     * May be null.
     */
    @XmlAttribute
    @Metadata(label = "advanced")
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * Prefix to use for all node ids (not route id).
     */
    public String getNodePrefixId() {
        return nodePrefixId;
    }

    /**
     * Sets a prefix to use for all node ids (not route id).
     */
    @XmlAttribute
    @Metadata(label = "advanced")
    public void setNodePrefixId(String nodePrefixId) {
        this.nodePrefixId = nodePrefixId;
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
    @Metadata(javaType = "java.lang.Boolean")
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
    @Metadata(javaType = "java.lang.Boolean")
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
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
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
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    public void setLogMask(String logMask) {
        this.logMask = logMask;
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
    @Metadata(label = "advanced", javaType = "java.lang.Long")
    public void setDelayer(String delayer) {
        this.delayer = delayer;
    }

    /**
     * Whether to auto start this route
     */
    public String getAutoStartup() {
        return autoStartup;
    }

    /**
     * Whether to auto start this route
     */
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", defaultValue = "true")
    public void setAutoStartup(String autoStartup) {
        this.autoStartup = autoStartup;
    }

    /**
     * The predicate of the precondition in simple language to evaluate in order to determine if this route should be
     * included or not.
     */
    @Override
    public String getPrecondition() {
        return precondition;
    }

    /**
     * The predicate of the precondition in simple language to evaluate in order to determine if this route should be
     * included or not.
     */
    @XmlAttribute
    @Metadata(label = "advanced")
    @Override
    public void setPrecondition(String precondition) {
        this.precondition = precondition;
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
    @Metadata(label = "advanced", javaType = "java.lang.Integer")
    public void setStartupOrder(Integer startupOrder) {
        this.startupOrder = startupOrder;
    }

    /**
     * Sets the bean ref name of the error handler builder to use on this route
     */
    @XmlAttribute
    public void setErrorHandlerRef(String errorHandlerRef) {
        if (errorHandlerRef != null) {
            this.errorHandlerRef = errorHandlerRef;
            // we use an specific error handler ref (from Spring DSL) then wrap that
            // with a error handler build ref so Camel knows its not just the
            // default one
            setErrorHandlerFactory(new RefErrorHandlerDefinition(errorHandlerRef));
        }
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
    @XmlTransient
    public void setErrorHandlerFactoryIfNull(ErrorHandlerFactory errorHandlerFactory) {
        if (this.errorHandlerFactory == null) {
            setErrorHandlerFactory(errorHandlerFactory);
        }
    }

    /**
     * Reference to custom {@link org.apache.camel.spi.RoutePolicy} to use by the route. Multiple policies can be
     * configured by separating values using comma.
     */
    @XmlAttribute
    public void setRoutePolicyRef(String routePolicyRef) {
        this.routePolicyRef = routePolicyRef;
    }

    /**
     * Reference to custom {@link org.apache.camel.spi.RoutePolicy} to use by the route. Multiple policies can be
     * configured by separating values using comma.
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

    public String getShutdownRoute() {
        return shutdownRoute;
    }

    /**
     * To control how to shutdown the route.
     */
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.ShutdownRoute", defaultValue = "Default",
              enums = "Default,Defer")
    public void setShutdownRoute(String shutdownRoute) {
        this.shutdownRoute = shutdownRoute;
    }

    /**
     * To control how to shut down the route.
     */
    public String getShutdownRunningTask() {
        return shutdownRunningTask;
    }

    /**
     * To control how to shut down the route.
     */
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "org.apache.camel.ShutdownRunningTask", defaultValue = "CompleteCurrentTaskOnly",
              enums = "CompleteCurrentTaskOnly,CompleteAllTasks")
    public void setShutdownRunningTask(String shutdownRunningTask) {
        this.shutdownRunningTask = shutdownRunningTask;
    }

    private ErrorHandlerFactory createErrorHandlerBuilder() {
        if (errorHandlerRef != null) {
            return new RefErrorHandlerDefinition(errorHandlerRef);
        }

        // return a reference to the default error handler
        return new RefErrorHandlerDefinition(RefErrorHandlerDefinition.DEFAULT_ERROR_HANDLER_BUILDER);
    }

    public ErrorHandlerFactory getErrorHandlerFactory() {
        if (errorHandlerFactory == null) {
            errorHandlerFactory = createErrorHandlerBuilder();
        }
        return errorHandlerFactory;
    }

    /**
     * Is a custom error handler been set
     */
    boolean isErrorHandlerFactorySet() {
        return errorHandlerFactory != null;
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    @XmlTransient
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
    }

    /**
     * This route is created from REST DSL
     */
    public void setRest(Boolean rest) {
        this.rest = rest;
    }

    @XmlAttribute
    @Metadata(label = "advanced")
    public Boolean isRest() {
        return rest;
    }

    /**
     * This route is created from a route template.
     */
    public void setTemplate(Boolean template) {
        this.template = template;
    }

    @XmlAttribute
    @Metadata(label = "advanced")
    public Boolean isTemplate() {
        return template;
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

    public boolean isContextScopedErrorHandler() {
        return contextScopedErrorHandler;
    }

    @XmlElementRef(required = false)
    @Metadata(label = "advanced")
    public void setInputType(InputTypeDefinition inputType) {
        this.inputType = inputType;
    }

    public InputTypeDefinition getInputType() {
        return this.inputType;
    }

    @XmlElementRef(required = false)
    @Metadata(label = "advanced")
    public void setOutputType(OutputTypeDefinition outputType) {
        this.outputType = outputType;
    }

    public OutputTypeDefinition getOutputType() {
        return this.outputType;
    }

    public List<PropertyDefinition> getRouteProperties() {
        return routeProperties;
    }

    /**
     * To set metadata as properties on the route.
     */
    @XmlElement(name = "routeProperty")
    @Metadata(label = "advanced")
    public void setRouteProperties(List<PropertyDefinition> routeProperties) {
        this.routeProperties = routeProperties;
    }

    @Override
    public boolean isCreatedFromTemplate() {
        return template != null && template;
    }

    @Override
    public boolean isCreatedFromRest() {
        return rest != null && rest;
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
