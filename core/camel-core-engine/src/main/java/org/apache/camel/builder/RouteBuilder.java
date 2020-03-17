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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is used to
 * build {@link Route} instances in a {@link CamelContext} for smart routing.
 */
public abstract class RouteBuilder extends BuilderSupport implements RoutesBuilder, Ordered {
    protected Logger log = LoggerFactory.getLogger(getClass());
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private RestsDefinition restCollection = new RestsDefinition();
    private RestConfigurationDefinition restConfiguration;
    private List<TransformerBuilder> transformerBuilders = new ArrayList<>();
    private List<ValidatorBuilder> validatorBuilders = new ArrayList<>();
    private RoutesDefinition routeCollection = new RoutesDefinition();
    private final List<RouteBuilderLifecycleStrategy> lifecycleInterceptors = new ArrayList<>();

    public RouteBuilder() {
        this(null);
    }

    public RouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * Override this method to define ordering of {@link RouteBuilder} classes that are added to
     * Camel from various runtimes such as camel-main, camel-spring-boot. This allows end users
     * to control the ordering if some routes must be added and started before others.
     * <p/>
     * Use low numbers for higher priority. Normally the sorting will start from 0 and move upwards.
     * So if you want to be last then use {@link Integer#MAX_VALUE} or eg {@link #LOWEST}.
     */
    @Override
    public int getOrder() {
        return LOWEST;
    }

    /**
     * Add routes to a context using a lambda expression. It can be used as
     * following:
     *
     * <pre>
     * RouteBuilder.addRoutes(context, rb ->
     *     rb.from("direct:inbound").bean(ProduceTemplateBean.class)));
     * </pre>
     *
     * @param context the camel context to add routes
     * @param rbc a lambda expression receiving the {@code RouteBuilder} to use
     *            to create routes
     * @throws Exception if an error occurs
     */
    public static void addRoutes(CamelContext context, ThrowingConsumer<RouteBuilder, Exception> rbc) throws Exception {
        context.addRoutes(new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                rbc.accept(this);
            }
        });
    }

    @Override
    public String toString() {
        return getRouteCollection().toString();
    }

    /**
     * <b>Called on initialization to build the routes using the fluent builder
     * syntax.</b>
     * <p/>
     * This is a central method for RouteBuilder implementations to implement
     * the routes using the Java fluent builder syntax.
     *
     * @throws Exception can be thrown during configuration
     */
    public abstract void configure() throws Exception;

    /**
     * Binds the bean to the repository (if possible).
     *
     * @param id the id of the bean
     * @param bean the bean
     */
    public void bindToRegistry(String id, Object bean) {
        getContext().getRegistry().bind(id, bean);
    }

    /**
     * Binds the bean to the repository (if possible).
     *
     * @param id the id of the bean
     * @param type the type of the bean to associate the binding
     * @param bean the bean
     */
    public void bindToRegistry(String id, Class<?> type, Object bean) {
        getContext().getRegistry().bind(id, type, bean);
    }

    /**
     * Configures the REST services
     *
     * @return the builder
     */
    public RestConfigurationDefinition restConfiguration() {
        if (restConfiguration == null) {
            restConfiguration = new RestConfigurationDefinition();
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
     * @param path the base path
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
     * @param uri the from uri
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
     * @param uri the String formatted from uri
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
     * @param endpoint the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(endpoint);
        configureRoute(answer);
        return answer;
    }

    public RouteDefinition from(EndpointConsumerBuilder endpointDefinition) {
        getRouteCollection().setCamelContext(getContext());
        RouteDefinition answer = getRouteCollection().from(endpointDefinition);
        configureRoute(answer);
        return answer;
    }

    /**
     * Installs the given
     * <a href="http://camel.apache.org/error-handler.html">error handler</a>
     * builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for
     *            all child routes
     */
    public void errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("errorHandler must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        setErrorHandlerBuilder(errorHandlerBuilder);
    }

    /**
     * Injects a property placeholder value with the given key converted to the
     * given type.
     *
     * @param key the property key
     * @param type the type to convert the value as
     * @return the value, or <tt>null</tt> if value is empty
     * @throws Exception is thrown if property with key not found or error
     *             converting to the given type.
     */
    public <T> T propertyInject(String key, Class<T> type) throws Exception {
        StringHelper.notEmpty(key, "key");
        ObjectHelper.notNull(type, "Class type");

        // the properties component is mandatory
        PropertiesComponent pc = getContext().getPropertiesComponent();
        // resolve property
        Optional<String> value = pc.resolveProperty(key);

        if (value.isPresent()) {
            return getContext().getTypeConverter().mandatoryConvertTo(type, value.get());
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
     * Adds a route for an interceptor that intercepts incoming messages on any
     * inputs in this route
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
     * Adds a route for an interceptor that intercepts incoming messages on the
     * given endpoint.
     *
     * @param uri endpoint uri
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
     * Applies a route for an interceptor if an exchange is send to the given
     * endpoint
     *
     * @param uri endpoint uri
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
     * <a href="http://camel.apache.org/exception-clause.html">Exception
     * clause</a> for catching certain exceptions and handling them.
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
     * <a href="http://camel.apache.org/exception-clause.html">Exception
     * clause</a> for catching certain exceptions and handling them.
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
     * callback for doing custom routing when the
     * {@link org.apache.camel.Exchange} is complete.
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

    @Override
    public void addRoutesToCamelContext(CamelContext context) throws Exception {
        // must configure routes before rests
        configureRoutes(context);
        configureRests(context);

        // but populate rests before routes, as we want to turn rests into
        // routes
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
    public RoutesDefinition configureRoutes(CamelContext context) throws Exception {
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
    public RestsDefinition configureRests(CamelContext context) throws Exception {
        setContext(context);
        restCollection.setCamelContext(context);
        return restCollection;
    }

    @Override
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        super.setErrorHandlerBuilder(errorHandlerBuilder);
        getRouteCollection().setErrorHandlerFactory(getErrorHandlerBuilder());
    }

    /**
     * Adds the given {@link RouteBuilderLifecycleStrategy} to be used.
     */
    public void addLifecycleInterceptor(RouteBuilderLifecycleStrategy interceptor) {
        lifecycleInterceptors.add(interceptor);
    }

    /**
     * Adds the given {@link RouteBuilderLifecycleStrategy}.
     */
    public void removeLifecycleInterceptor(RouteBuilderLifecycleStrategy interceptor) {
        lifecycleInterceptors.remove(interceptor);
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected void checkInitialized() throws Exception {
        if (initialized.compareAndSet(false, true)) {
            // Set the CamelContext ErrorHandler here
            CamelContext camelContext = getContext();
            if (camelContext.adapt(ExtendedCamelContext.class).getErrorHandlerFactory() instanceof ErrorHandlerBuilder) {
                setErrorHandlerBuilder((ErrorHandlerBuilder)camelContext.adapt(ExtendedCamelContext.class).getErrorHandlerFactory());
            }

            for (RouteBuilderLifecycleStrategy interceptor : lifecycleInterceptors) {
                interceptor.beforeConfigure(this);
            }

            configure();
            // mark all route definitions as custom prepared because
            // a route builder prepares the route definitions correctly already
            for (RouteDefinition route : getRouteCollection().getRoutes()) {
                route.markPrepared();
            }

            for (RouteBuilderLifecycleStrategy interceptor : lifecycleInterceptors) {
                interceptor.afterConfigure(this);
            }
        }
    }

    protected void populateRoutes() throws Exception {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        getRouteCollection().setCamelContext(camelContext);
        camelContext.getExtension(Model.class).addRouteDefinitions(getRouteCollection().getRoutes());
    }

    protected void populateRests() throws Exception {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        getRestCollection().setCamelContext(camelContext);

        // setup rest configuration before adding the rests
        if (restConfiguration != null) {
            restConfiguration.asRestConfiguration(getContext(), camelContext.getRestConfiguration());
        }

        // cannot add rests as routes yet as we need to initialize this
        // specially
        camelContext.getExtension(Model.class).addRestDefinitions(getRestCollection().getRests(), false);

        // convert rests api-doc into routes so they are routes for runtime
        RestConfiguration config = camelContext.getRestConfiguration();

        if (config.getApiContextPath() != null) {
            // avoid adding rest-api multiple times, in case multiple
            // RouteBuilder classes is added
            // to the CamelContext, as we only want to setup rest-api once
            // so we check all existing routes if they have rest-api route
            // already added
            boolean hasRestApi = false;
            for (RouteDefinition route : camelContext.getExtension(Model.class).getRouteDefinitions()) {
                FromDefinition from = route.getInput();
                if (from.getEndpointUri() != null && from.getEndpointUri().startsWith("rest-api:")) {
                    hasRestApi = true;
                }
            }
            if (!hasRestApi) {
                RouteDefinition route = RestDefinition.asRouteApiDefinition(camelContext, config);
                log.debug("Adding routeId: {} as rest-api route", route.getId());
                getRouteCollection().route(route);
            }
        }

        // add rest as routes and have them prepared as well via
        // routeCollection.route method
        getRestCollection().getRests().forEach(rest -> rest.asRouteDefinition(getContext()).forEach(route -> getRouteCollection().route(route)));
    }

    protected void populateTransformers() {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        for (TransformerBuilder tdb : transformerBuilders) {
            tdb.configure(camelContext);
        }
    }

    protected void populateValidators() {
        CamelContext camelContext = getContext();
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

    public RestConfigurationDefinition getRestConfiguration() {
        return restConfiguration;
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

    protected void configureRest(RestDefinition rest) {
        // noop
    }

    protected void configureRoute(RouteDefinition route) {
        // noop
    }

}
