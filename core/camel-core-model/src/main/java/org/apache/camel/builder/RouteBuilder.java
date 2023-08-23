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

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
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
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.TemplatedRouteDefinition;
import org.apache.camel.model.TemplatedRoutesDefinition;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.OnCamelContextEvent;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is used to build {@link Route} instances in a
 * {@link CamelContext} for smart routing.
 */
public abstract class RouteBuilder extends BuilderSupport implements RoutesBuilder, Ordered, ResourceAware {
    protected Logger log = LoggerFactory.getLogger(getClass());

    private Resource resource;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final List<RouteBuilderLifecycleStrategy> lifecycleInterceptors = new ArrayList<>();
    private final List<TransformerBuilder> transformerBuilders = new ArrayList<>();
    private final List<ValidatorBuilder> validatorBuilders = new ArrayList<>();
    // XML and YAML DSL allows to define custom beans which we need to capture
    private final List<RegistryBeanDefinition> beans = new ArrayList<>();

    private RestsDefinition restCollection = new RestsDefinition();
    private RestConfigurationDefinition restConfiguration;
    private RoutesDefinition routeCollection = new RoutesDefinition();
    private RouteTemplatesDefinition routeTemplateCollection = new RouteTemplatesDefinition();
    private TemplatedRoutesDefinition templatedRouteCollection = new TemplatedRoutesDefinition();

    public RouteBuilder() {
        this(null);
    }

    public RouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * The {@link Resource} which is the source code for this route (such as XML, YAML, Groovy or Java source file)
     */
    public Resource getResource() {
        return resource;
    }

    /**
     * Sets the {@link Resource} which is the source code for this route (such as XML, YAML, Groovy or Java source file)
     */
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    /**
     * Add routes to a context using a lambda expression. It can be used as following:
     *
     * <pre>
     * RouteBuilder.addRoutes(context, rb ->
     *     rb.from("direct:inbound").bean(MyBean.class)));
     * </pre>
     *
     * @param  context   the camel context to add routes
     * @param  rbc       a lambda expression receiving the {@code RouteBuilder} to use to create routes
     * @throws Exception if an error occurs
     */
    public static void addRoutes(CamelContext context, LambdaRouteBuilder rbc) throws Exception {
        context.addRoutes(new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                rbc.accept(this);
            }
        });
    }

    /**
     * Loads {@link RoutesBuilder} from {@link Resource} using the given consumer to create a {@link RouteBuilder}
     * instance.
     *
     * @param  resource the resource to be loaded.
     * @param  consumer the function used to create a {@link RoutesBuilder}
     * @return          a {@link RoutesBuilder}
     */
    public static RouteBuilder loadRoutesBuilder(
            Resource resource, ThrowingBiConsumer<Reader, RouteBuilder, Exception> consumer) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                CamelContextAware.trySetCamelContext(resource, getContext());

                try (Reader reader = resource.getReader()) {
                    consumer.accept(reader, this);
                }
            }
        };
    }

    /**
     * Loads {@link RoutesBuilder} using the given consumer to create a {@link RouteBuilder} instance.
     *
     * @param  consumer the function used to create a {@link RoutesBuilder}
     * @return          a {@link RoutesBuilder}
     */
    public static RouteBuilder loadRoutesBuilder(ThrowingConsumer<RouteBuilder, Exception> consumer) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                consumer.accept(this);
            }
        };
    }

    /**
     * Override this method to define ordering of {@link RouteBuilder} classes that are added to Camel from various
     * runtimes such as camel-main, camel-spring-boot. This allows end users to control the ordering if some routes must
     * be added and started before others.
     * <p/>
     * Use low numbers for higher priority. Normally the sorting will start from 0 and move upwards. So if you want to
     * be last then use {@link Integer#MAX_VALUE} or eg {@link #LOWEST}.
     */
    @Override
    public int getOrder() {
        return LOWEST;
    }

    @Override
    public String toString() {
        return getRouteCollection().toString();
    }

    /**
     * <b>Called on initialization to build the routes using the fluent builder syntax.</b>
     * <p/>
     * This is a central method for RouteBuilder implementations to implement the routes using the Java fluent builder
     * syntax.
     *
     * @throws Exception can be thrown during configuration
     */
    public abstract void configure() throws Exception;

    /**
     * <b>Called on initialization to build routes configuration (global routes configurations) using the fluent builder
     * syntax.</b>
     *
     * @throws Exception can be thrown during configuration
     */
    public void configuration() throws Exception {
        // noop
    }

    /**
     * Binds the bean to the repository (if possible).
     *
     * @param id   the id of the bean
     * @param bean the bean
     */
    public void bindToRegistry(String id, Object bean) {
        getContext().getRegistry().bind(id, bean);
    }

    /**
     * Binds the bean to the repository (if possible).
     *
     * @param id   the id of the bean
     * @param type the type of the bean to associate the binding
     * @param bean the bean
     */
    public void bindToRegistry(String id, Class<?> type, Object bean) {
        getContext().getRegistry().bind(id, type, bean);

    }

    /**
     * A utility method allowing to build any data format using a fluent syntax as shown in the next example:
     *
     * <pre>
     * {@code
     * from("jms:queue:orders")
     *         .marshal(
     *                 dataFormat()
     *                         .swiftMt()
     *                         .writeInJson(true)
     *                         .end())
     *         .to("file:data");
     * }
     * </pre>
     *
     * @return an entry point to the builder of all supported data formats.
     */
    public DataFormatBuilderFactory dataFormat() {
        return new DataFormatBuilderFactory();
    }

    /**
     * A utility method allowing to build any language using a fluent syntax as shown in the next example:
     *
     * <pre>
     * {@code
     * from("file:data")
     *         .split(
     *                 expression()
     *                         .tokenize()
     *                         .token("\n")
     *                         .end())
     *         .process("processEntry");
     * }
     * </pre>
     *
     * @return an entry point to the builder of all supported languages.
     */
    public LanguageBuilderFactory expression() {
        return new LanguageBuilderFactory();
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
     * Creates a new route template
     *
     * @return the builder
     */
    public RouteTemplateDefinition routeTemplate(String id) {
        getRouteTemplateCollection().setCamelContext(getContext());
        RouteTemplateDefinition answer = getRouteTemplateCollection().routeTemplate(id);
        configureRouteTemplate(answer);
        return answer;
    }

    /**
     * Creates a new templated route
     *
     * @return the builder
     */
    public TemplatedRouteDefinition templatedRoute(String routeTemplateId) {
        getTemplatedRouteCollection().setCamelContext(getContext());
        TemplatedRouteDefinition answer = getTemplatedRouteCollection().templatedRoute(routeTemplateId);
        configureTemplatedRoute(answer);
        return answer;
    }

    /**
     * Creates a new REST service
     *
     * @return the builder
     */
    public RestDefinition rest() {
        getRestCollection().setCamelContext(getContext());
        if (resource != null) {
            getRestCollection().setResource(resource);
        }
        RestDefinition answer = getRestCollection().rest();
        configureRest(answer);
        return answer;
    }

    /**
     * Creates a new REST service
     *
     * @param  path the base path
     * @return      the builder
     */
    public RestDefinition rest(String path) {
        getRestCollection().setCamelContext(getContext());
        if (resource != null) {
            getRestCollection().setResource(resource);
        }
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
     * @param  uri the from uri
     * @return     the builder
     */
    public RouteDefinition from(String uri) {
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        RouteDefinition answer = getRouteCollection().from(uri);
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param  uri  the String formatted from uri
     * @param  args arguments for the string formatting of the uri
     * @return      the builder
     */
    public RouteDefinition fromF(String uri, Object... args) {
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        RouteDefinition answer = getRouteCollection().from(String.format(uri, args));
        configureRoute(answer);
        return answer;
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param  endpoint the from endpoint
     * @return          the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        RouteDefinition answer = getRouteCollection().from(endpoint);
        configureRoute(answer);
        return answer;
    }

    public RouteDefinition from(EndpointConsumerBuilder endpointDefinition) {
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        RouteDefinition answer = getRouteCollection().from(endpointDefinition);
        configureRoute(answer);
        return answer;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder
     *
     * @param errorHandlerFactory the error handler to be used by default for all child routes
     */
    public void errorHandler(ErrorHandlerFactory errorHandlerFactory) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("errorHandler must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        setErrorHandlerFactory(errorHandlerFactory);
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder
     *
     * @param ref reference to the error handler to use
     */
    public void errorHandler(String ref) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("errorHandler must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        setErrorHandlerFactory(new RefErrorHandlerDefinition(ref));
    }

    /**
     * Injects a property placeholder value with the given key converted to the given type.
     *
     * @param  key       the property key
     * @param  type      the type to convert the value as
     * @return           the value, or <tt>null</tt> if value is empty
     * @throws Exception is thrown if property with key not found or error converting to the given type.
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
     * Refers to the property placeholder
     *
     * @param  key the property key
     * @return     the reference to the property using syntax {{key}}
     */
    public String property(String key) {
        return PropertiesComponent.PREFIX_TOKEN + key + PropertiesComponent.SUFFIX_TOKEN;
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
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
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
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        return getRouteCollection().interceptFrom();
    }

    /**
     * Adds a route for an interceptor that intercepts incoming messages on the given endpoint.
     *
     * @param  uri endpoint uri
     * @return     the builder
     */
    public InterceptFromDefinition interceptFrom(String uri) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("interceptFrom must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        return getRouteCollection().interceptFrom(uri);
    }

    /**
     * Applies a route for an interceptor if an exchange is send to the given endpoint
     *
     * @param  uri endpoint uri
     * @return     the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("interceptSendToEndpoint must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        return getRouteCollection().interceptSendToEndpoint(uri);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exception exception to catch
     * @return           the builder
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exception) {
        // is only allowed at the top currently
        if (!getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException("onException must be defined before any routes in the RouteBuilder");
        }
        getRouteCollection().setCamelContext(getContext());
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        return getRouteCollection().onException(exception);
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptions list of exceptions to catch
     * @return            the builder
     */
    public OnExceptionDefinition onException(Class<? extends Throwable>... exceptions) {
        OnExceptionDefinition last = null;
        for (Class<? extends Throwable> ex : exceptions) {
            last = last == null ? onException(ex) : last.onException(ex);
        }
        return last != null ? last : onException(Exception.class);
    }

    /**
     * <a href="http://camel.apache.org/oncompletion.html">On completion</a> callback for doing custom routing when the
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
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        return getRouteCollection().onCompletion();
    }

    @Override
    public void addRoutesToCamelContext(CamelContext context) throws Exception {
        // must configure routes before rests
        configureRoutes(context);
        configureRests(context);

        // but populate rests before routes, as we want to turn rests into routes
        populateBeans();
        populateRests();
        populateTransformers();
        populateValidators();
        populateRouteTemplates();

        // ensure routes are prepared before being populated
        for (RouteDefinition route : routeCollection.getRoutes()) {
            routeCollection.prepareRoute(route);
        }
        populateRoutes();

        if (this instanceof OnCamelContextEvent) {
            context.addLifecycleStrategy(LifecycleStrategySupport.adapt((OnCamelContextEvent) this));
        }
    }

    @Override
    public void addTemplatedRoutesToCamelContext(CamelContext context) throws Exception {
        populateTemplatedRoutes(context);
    }

    @Override
    public Set<String> updateRoutesToCamelContext(CamelContext context) throws Exception {
        Set<String> answer = new LinkedHashSet<>();

        // must configure routes before rests
        configureRoutes(context);
        configureRests(context);

        // but populate rests before routes, as we want to turn rests into routes
        populateBeans();
        populateRests();
        populateTransformers();
        populateValidators();
        populateRouteTemplates();

        // ensure routes are prepared before being populated
        for (RouteDefinition route : routeCollection.getRoutes()) {
            routeCollection.prepareRoute(route);
        }

        // trigger update of the routes
        populateOrUpdateRoutes();

        if (this instanceof OnCamelContextEvent) {
            context.addLifecycleStrategy(LifecycleStrategySupport.adapt((OnCamelContextEvent) this));
        }

        for (RouteDefinition route : routeCollection.getRoutes()) {
            String id = route.getRouteId();
            answer.add(id);
        }

        return answer;
    }

    /**
     * Configures the routes
     *
     * @param  context   the Camel context
     * @return           the routes configured
     * @throws Exception can be thrown during configuration
     */
    public RoutesDefinition configureRoutes(CamelContext context) throws Exception {
        setCamelContext(context);
        checkInitialized();
        routeCollection.setCamelContext(context);
        return routeCollection;
    }

    /**
     * Configures the rests
     *
     * @param  context   the Camel context
     * @return           the rests configured
     * @throws Exception can be thrown during configuration
     */
    public RestsDefinition configureRests(CamelContext context) throws Exception {
        setCamelContext(context);
        restCollection.setCamelContext(context);
        return restCollection;
    }

    @Override
    public void setErrorHandlerFactory(ErrorHandlerFactory errorHandlerFactory) {
        super.setErrorHandlerFactory(errorHandlerFactory);
        getRouteCollection().setErrorHandlerFactory(getErrorHandlerFactory());
        getRouteTemplateCollection().setErrorHandlerFactory(getErrorHandlerFactory());
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
            if (camelContext.getCamelContextExtension().getErrorHandlerFactory() != null) {
                setErrorHandlerFactory(
                        camelContext.getCamelContextExtension().getErrorHandlerFactory());
            }

            List<RouteBuilderLifecycleStrategy> strategies = new ArrayList<>(lifecycleInterceptors);
            strategies.addAll(camelContext.getRegistry().findByType(RouteBuilderLifecycleStrategy.class));
            strategies.sort(Comparator.comparing(Ordered::getOrder));

            for (RouteBuilderLifecycleStrategy interceptor : strategies) {
                interceptor.beforeConfigure(this);
            }

            configure();

            // remember the source resource
            getRouteCollection().setResource(getResource());
            getRestCollection().setResource(getResource());
            getRouteTemplateCollection().setResource(getResource());
            for (RegistryBeanDefinition def : beans) {
                def.setResource(getResource());
            }

            for (RouteDefinition route : getRouteCollection().getRoutes()) {
                // ensure the route is prepared after configure method is complete
                getRouteCollection().prepareRoute(route);
            }

            for (RouteBuilderLifecycleStrategy interceptor : strategies) {
                interceptor.afterConfigure(this);
            }
        }
    }

    protected void populateTemplatedRoutes() throws Exception {
        populateTemplatedRoutes(notNullCamelContext());
    }

    private void populateTemplatedRoutes(CamelContext camelContext) throws Exception {
        getTemplatedRouteCollection().setCamelContext(camelContext);
        camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                .addRouteFromTemplatedRoutes(getTemplatedRouteCollection().getTemplatedRoutes());
    }

    /**
     * @return                          the current context if it is not {@code null}
     * @throws IllegalArgumentException if the {@code CamelContext} has not been set.
     */
    private CamelContext notNullCamelContext() {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        return camelContext;
    }

    protected void populateRouteTemplates() throws Exception {
        CamelContext camelContext = notNullCamelContext();
        getRouteTemplateCollection().setCamelContext(camelContext);
        camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                .addRouteTemplateDefinitions(getRouteTemplateCollection().getRouteTemplates());
    }

    protected void populateRoutes() throws Exception {
        CamelContext camelContext = notNullCamelContext();
        getRouteCollection().setCamelContext(camelContext);
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                .addRouteDefinitions(getRouteCollection().getRoutes());
    }

    protected void populateOrUpdateRoutes() throws Exception {
        CamelContext camelContext = notNullCamelContext();
        getRouteCollection().setCamelContext(camelContext);
        if (resource != null) {
            getRouteCollection().setResource(resource);
        }
        // must stop and remove existing running routes
        for (RouteDefinition route : getRouteCollection().getRoutes()) {
            camelContext.getRouteController().stopRoute(route.getRouteId());
            camelContext.removeRoute(route.getRouteId());
        }
        camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                .addRouteDefinitions(getRouteCollection().getRoutes());
    }

    protected void populateRests() throws Exception {
        CamelContext camelContext = notNullCamelContext();
        getRestCollection().setCamelContext(camelContext);

        // setup rest configuration before adding the rests
        if (restConfiguration != null) {
            restConfiguration.asRestConfiguration(getContext(), camelContext.getRestConfiguration());
        }

        // cannot add rests as routes yet as we need to initialize this
        // specially
        camelContext.getCamelContextExtension().getContextPlugin(Model.class).addRestDefinitions(getRestCollection().getRests(),
                false);

        // convert rests api-doc into routes so they are routes for runtime
        RestConfiguration config = camelContext.getRestConfiguration();

        if (config.getApiContextPath() != null) {
            // avoid adding rest-api multiple times, in case multiple
            // RouteBuilder classes is added
            // to the CamelContext, as we only want to setup rest-api once
            // so we check all existing routes if they have rest-api route
            // already added
            boolean hasRestApi = false;
            for (RouteDefinition route : camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                    .getRouteDefinitions()) {
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
        getRestCollection().getRests()
                .forEach(rest -> rest.asRouteDefinition(getContext()).forEach(route -> getRouteCollection().route(route)));
    }

    protected void populateTransformers() {
        CamelContext camelContext = notNullCamelContext();
        for (TransformerBuilder tdb : transformerBuilders) {
            tdb.configure(camelContext);
        }
    }

    protected void populateValidators() {
        CamelContext camelContext = notNullCamelContext();
        for (ValidatorBuilder vb : validatorBuilders) {
            vb.configure(camelContext);
        }
    }

    protected void populateBeans() {
        CamelContext camelContext = notNullCamelContext();

        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
        for (RegistryBeanDefinition def : beans) {
            // add to model
            model.addRegistryBean(def);
        }
    }

    public List<RegistryBeanDefinition> getBeans() {
        return beans;
    }

    public RestsDefinition getRestCollection() {
        return restCollection;
    }

    public void setRestCollection(RestsDefinition restCollection) {
        this.restCollection = restCollection;
    }

    public RestConfigurationDefinition getRestConfiguration() {
        return restConfiguration;
    }

    public RoutesDefinition getRouteCollection() {
        return this.routeCollection;
    }

    public void setRouteCollection(RoutesDefinition routeCollection) {
        this.routeCollection = routeCollection;
    }

    public RouteTemplatesDefinition getRouteTemplateCollection() {
        return routeTemplateCollection;
    }

    public void setRouteTemplateCollection(RouteTemplatesDefinition routeTemplateCollection) {
        this.routeTemplateCollection = routeTemplateCollection;
    }

    public TemplatedRoutesDefinition getTemplatedRouteCollection() {
        return templatedRouteCollection;
    }

    public void setTemplatedRouteCollection(TemplatedRoutesDefinition templatedRouteCollection) {
        this.templatedRouteCollection = templatedRouteCollection;
    }

    protected void configureRest(RestDefinition rest) {
        CamelContextAware.trySetCamelContext(rest, getContext());
    }

    protected void configureRoute(RouteDefinition route) {
        CamelContextAware.trySetCamelContext(route, getContext());
    }

    protected void configureRouteTemplate(RouteTemplateDefinition routeTemplate) {
        CamelContextAware.trySetCamelContext(routeTemplate, getContext());
    }

    protected void configureTemplatedRoute(CamelContextAware templatedRoute) {
        CamelContextAware.trySetCamelContext(templatedRoute, getContext());
    }

    protected void configureRouteConfiguration(RouteConfigurationDefinition routesConfiguration) {
        CamelContextAware.trySetCamelContext(routesConfiguration, getContext());
    }
}
