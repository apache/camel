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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build {@link org.apache.camel.impl.DefaultRoute} instances in a {@link CamelContext} for smart routing.
 *
 * @version 
 */
public abstract class RouteBuilder extends BuilderSupport implements RoutesBuilder {
    protected Logger log = LoggerFactory.getLogger(getClass());
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private RestsDefinition restCollection = new RestsDefinition();
    private Map<String, RestConfigurationDefinition> restConfigurations;
    private List<TransformerBuilder> transformerBuilders = new ArrayList<>();
    private List<ValidatorBuilder> validatorBuilders = new ArrayList<>();
    private RoutesDefinition routeCollection = new RoutesDefinition();

    public RouteBuilder() {
        this(null);
    }

    public RouteBuilder(CamelContext context) {
        super(context);
    }

    @Override
    public String toString() {
        return getRouteCollection().toString();
    }

    /**
     * <b>Called on initialization to build the routes using the fluent builder syntax.</b>
     * <p/>
     * This is a central method for RouteBuilder implementations to implement
     * the routes using the Java fluent builder syntax.
     *
     * @throws Exception can be thrown during configuration
     */
    public abstract void configure() throws Exception;

    /**
     * Configures the REST services
     *
     * @return the builder
     */
    public RestConfigurationDefinition restConfiguration() {
        return restConfiguration("");
    }

    /**
     * Configures the REST service for the given component
     *
     * @return the builder
     */
    public RestConfigurationDefinition restConfiguration(String component) {
        if (restConfigurations == null) {
            restConfigurations = new HashMap<String, RestConfigurationDefinition>();
        }
        RestConfigurationDefinition restConfiguration = restConfigurations.get(component);
        if (restConfiguration == null) {
            restConfiguration = new RestConfigurationDefinition();
            if (!component.isEmpty()) {
                restConfiguration.component(component);
            }
            restConfigurations.put(component, restConfiguration);
        }
        return restConfiguration;
    }
    /**
     * Creates a new REST service
     *
     * @return the builder
     */
    public RestDefinition rest() {
        getRestCollection().setCamelContext(getContext());
        RestDefinition answer = getRestCollection().rest();
        configureRest(answer);
        return answer;
    }

    /**
     * Creates a new REST service
     *
     * @param path  the base path
     * @return the builder
     */
    public RestDefinition rest(String path) {
        getRestCollection().setCamelContext(getContext());
        RestDefinition answer = getRestCollection().rest(path);
        configureRest(answer);
        return answer;
    }

    /**
     * Create a new {@code TransformerBuilder}.
     * 
     * @return the builder
     */
    public TransformerBuilder transformer() {
        TransformerBuilder tdb = new TransformerBuilder();
        transformerBuilders.add(tdb);
        return tdb;
    }

    /**
     * Create a new {@code ValidatorBuilder}.
     * 
     * @return the builder
     */
    public ValidatorBuilder validator() {
        ValidatorBuilder vb = new ValidatorBuilder();
        validatorBuilders.add(vb);
        return vb;
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(uri);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param uri  the String formatted from uri
     * @param args arguments for the string formatting of the uri
     * @return the builder
     */
    public RouteDefinition fromF(String uri, Object... args) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(String.format(uri, args));
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(endpoint);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given URIs input
     *
     * @param uris  the from uris
     * @return the builder
     */
    public RouteDefinition from(String... uris) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(uris);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(endpoints);
        configureRoute(answer);
        return answer;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder
     *
     * @param errorHandlerBuilder  the error handler to be used by default for all child routes
     */
    public void errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("errorHandler must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        setErrorHandlerBuilder(errorHandlerBuilder);
    }

    /**
     * Injects a property placeholder value with the given key converted to the given type.
     *
     * @param key  the property key
     * @param type the type to convert the value as
     * @return the value, or <tt>null</tt> if value is empty
     * @throws Exception is thrown if property with key not found or error converting to the given type.
     */
    public <T> T propertyInject(String key, Class<T> type) throws Exception {
        ObjectHelper.notEmpty(key, "key");
        ObjectHelper.notNull(type, "Class type");

        // the properties component is mandatory
        Component component = getContext().hasComponent("properties");
        if (component == null) {
            throw new IllegalArgumentException("PropertiesComponent with name properties must be defined"
                + " in CamelContext to support property placeholders in expressions");
        }
        PropertiesComponent pc = getContext().getTypeConverter()
            .mandatoryConvertTo(PropertiesComponent.class, component);
        // enclose key with {{ }} to force parsing
        Object value = pc.parseUri(pc.getPrefixToken() + key + pc.getSuffixToken());

        if (value != null) {
            return getContext().getTypeConverter().mandatoryConvertTo(type, value);
        } else {
            return null;
        }
    }

    /**
     * Adds a route for an interceptor that intercepts every processing step.
     *
     * @return the builder
     */
    public InterceptDefinition intercept() {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("intercept must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().intercept();
    }

    /**
     * Adds a route for an interceptor that intercepts incoming messages on any inputs in this route
     *
     * @return the builder
     */
    public InterceptFromDefinition interceptFrom() {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("interceptFrom must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().interceptFrom();
    }

    /**
     * Adds a route for an interceptor that intercepts incoming messages on the given endpoint.
     *
     * @param uri  endpoint uri
     * @return the builder
     */
    public InterceptFromDefinition interceptFrom(String uri) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("interceptFrom must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().interceptFrom(uri);
    }

    /**
     * Applies a route for an interceptor if an exchange is send to the given endpoint
     *
     * @param uri  endpoint uri
     * @return the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("interceptSendToEndpoint must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().interceptSendToEndpoint(uri);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exception exception to catch
     * @return the builder
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exception) {
        // is only allowed at the top currently
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("onException must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().onException(exception);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exceptions list of exceptions to catch
     * @return the builder
     */
    public OnExceptionDefinition onException(Class<? extends Throwable>... exceptions) {
        OnExceptionDefinition last = null;
        for (Class<? extends Throwable> ex : exceptions) {
            last = last == null ? onException(ex) : last.onException(ex);
        }
        return last != null ? last : onException(Exception.class);
    }

    /**
     * <a href="http://camel.apache.org/oncompletion.html">On completion</a>
     * callback for doing custom routing when the {@link org.apache.camel.Exchange} is complete.
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompletion() {
        // is only allowed at the top currently
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("onCompletion must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        return getRouteCollection().onCompletion();
    }
    
    // Properties
    // -----------------------------------------------------------------------
    public ModelCamelContext getContext() {
        ModelCamelContext context = super.getContext();
        if (context == null) {
            context = createContainer();
            setContext(context);
        }
        return context;
    }

    public void addRoutesToCamelContext(CamelContext context) throws Exception {
        // must configure routes before rests
        configureRoutes((ModelCamelContext) context);
        configureRests((ModelCamelContext) context);

        // but populate rests before routes, as we want to turn rests into routes
        populateRests();
        populateTransformers();
        populateValidators();
        populateRoutes();
    }

    /**
     * Configures the routes
     *
     * @param context the Camel context
     * @return the routes configured
     * @throws Exception can be thrown during configuration
     */
    public RoutesDefinition configureRoutes(ModelCamelContext context) throws Exception {
        setContext(context);
        checkInitialized();
        routeCollection.setCamelContext(context);
        return routeCollection;
    }

    /**
     * Configures the rests
     *
     * @param context the Camel context
     * @return the rests configured
     * @throws Exception can be thrown during configuration
     */
    public RestsDefinition configureRests(ModelCamelContext context) throws Exception {
        setContext(context);
        restCollection.setCamelContext(context);
        return restCollection;
    }

    /**
     * Includes the routes from the build to this builder.
     * <p/>
     * This allows you to use other builds as route templates.
     * @param routes other builder with routes to include
     *
     * @throws Exception can be thrown during configuration
     */
    public void includeRoutes(RoutesBuilder routes) throws Exception {
        // TODO: We should support including multiple routes so I think invoking configure()
        // needs to be deferred to later
        if (routes instanceof RouteBuilder) {
            // if its a RouteBuilder then let it use my route collection and error handler
            // then we are integrated seamless
            RouteBuilder builder = (RouteBuilder) routes;
            builder.setContext(this.getContext());
            builder.setRouteCollection(this.getRouteCollection());
            builder.setRestCollection(this.getRestCollection());
            builder.setErrorHandlerBuilder(this.getErrorHandlerBuilder());
            // must invoke configure on the original builder so it adds its configuration to me
            builder.configure();
        } else {
            getContext().addRoutes(routes);
        }
    }

    @Override
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        super.setErrorHandlerBuilder(errorHandlerBuilder);
        getRouteCollection().setErrorHandlerBuilder(getErrorHandlerBuilder());
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    @SuppressWarnings("deprecation")
    protected void checkInitialized() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            // Set the CamelContext ErrorHandler here
            ModelCamelContext camelContext = getContext();
            if (camelContext.getErrorHandlerBuilder() != null) {
                setErrorHandlerBuilder(camelContext.getErrorHandlerBuilder());
            }
            configure();
            // mark all route definitions as custom prepared because
            // a route builder prepares the route definitions correctly already
            for (RouteDefinition route : getRouteCollection().getRoutes()) {
                route.markPrepared();
            }
        }
    }

    protected void populateRoutes() throws Exception {
        ModelCamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        getRouteCollection().setCamelContext(camelContext);
        camelContext.addRouteDefinitions(getRouteCollection().getRoutes());
    }

    protected void populateRests() throws Exception {
        ModelCamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        getRestCollection().setCamelContext(camelContext);

        // setup rest configuration before adding the rests
        if (getRestConfigurations() != null) {
            for (Map.Entry<String, RestConfigurationDefinition> entry : getRestConfigurations().entrySet()) {
                RestConfiguration config = entry.getValue().asRestConfiguration(getContext());
                if ("".equals(entry.getKey())) {
                    camelContext.setRestConfiguration(config);
                } else {
                    camelContext.addRestConfiguration(config);
                }
            }
        }
        camelContext.addRestDefinitions(getRestCollection().getRests());

        // convert rests into routes so we they are routes for runtime
        List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
        for (RestDefinition rest : getRestCollection().getRests()) {
            List<RouteDefinition> list = rest.asRouteDefinition(getContext());
            routes.addAll(list);
        }
        // convert rests api-doc into routes so they are routes for runtime
        for (RestConfiguration config : camelContext.getRestConfigurations()) {
            if (config.getApiContextPath() != null) {
                // avoid adding rest-api multiple times, in case multiple RouteBuilder classes is added
                // to the CamelContext, as we only want to setup rest-api once
                // so we check all existing routes if they have rest-api route already added
                boolean hasRestApi = false;
                for (RouteDefinition route : camelContext.getRouteDefinitions()) {
                    FromDefinition from = route.getInputs().get(0);
                    if (from.getUri() != null && from.getUri().startsWith("rest-api:")) {
                        hasRestApi = true;
                    }
                }
                if (!hasRestApi) {
                    RouteDefinition route = RestDefinition.asRouteApiDefinition(camelContext, config);
                    log.debug("Adding routeId: {} as rest-api route", route.getId());
                    routes.add(route);
                }
            }
        }

        // add the rest routes
        for (RouteDefinition route : routes) {
            getRouteCollection().route(route);
        }
    }

    protected void populateTransformers() {
        ModelCamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        for (TransformerBuilder tdb : transformerBuilders) {
            tdb.configure(camelContext);
        }
    }

    protected void populateValidators() {
        ModelCamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        for (ValidatorBuilder vb : validatorBuilders) {
            vb.configure(camelContext);
        }
    }

    public RestsDefinition getRestCollection() {
        return restCollection;
    }

    public Map<String, RestConfigurationDefinition> getRestConfigurations() {
        return restConfigurations;
    }

    public void setRestCollection(RestsDefinition restCollection) {
        this.restCollection = restCollection;
    }

    public void setRouteCollection(RoutesDefinition routeCollection) {
        this.routeCollection = routeCollection;
    }

    public RoutesDefinition getRouteCollection() {
        return this.routeCollection;
    }

    /**
     * Factory method
     *
     * @return the CamelContext
     */
    protected ModelCamelContext createContainer() {
        return new DefaultCamelContext();
    }

    protected void configureRest(RestDefinition rest) {
        // noop
    }

    protected void configureRoute(RouteDefinition route) {
        // noop
    }

    /**
     * Adds a collection of routes to this context
     *
     * @param routes the routes
     * @throws Exception if the routes could not be created for whatever reason
     * @deprecated will be removed in Camel 3.0. Instead use {@link #includeRoutes(org.apache.camel.RoutesBuilder) includeRoutes} instead.
     */
    @Deprecated
    protected void addRoutes(RoutesBuilder routes) throws Exception {
        includeRoutes(routes);
    }

}
