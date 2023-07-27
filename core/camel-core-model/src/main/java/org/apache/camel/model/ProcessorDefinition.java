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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.EnrichClause;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ProcessClause;
import org.apache.camel.model.cloud.ServiceCallDefinition;
import org.apache.camel.model.dataformat.CustomDataFormat;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.resume.ConsumerListener;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.spi.AsEndpointUri;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.support.ExpressionAdapter;
import org.slf4j.Logger;

/**
 * Base class for processor types that most XML types extend.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("rawtypes")
public abstract class ProcessorDefinition<Type extends ProcessorDefinition<Type>> extends OptionalIdentifiedDefinition<Type>
        implements Block {
    @XmlTransient
    private static final AtomicInteger COUNTER = new AtomicInteger();
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    protected String disabled;
    @XmlAttribute
    protected Boolean inheritErrorHandler;
    @XmlTransient
    private final Deque<Block> blocks = new LinkedList<>();
    @XmlTransient
    private ProcessorDefinition<?> parent;
    @XmlTransient
    private RouteConfigurationDefinition routeConfiguration;
    @XmlTransient
    private final List<InterceptStrategy> interceptStrategies = new ArrayList<>();
    @XmlTransient
    private final int index;

    protected ProcessorDefinition() {
        // every time we create a definition we should inc the counter
        index = COUNTER.getAndIncrement();
    }

    private static <T extends ExpressionNode> ExpressionClause<T> createAndSetExpression(T result) {
        ExpressionClause<T> clause = new ExpressionClause<>(result);
        result.setExpression(clause);
        return clause;
    }

    /**
     * Gets the unique index number for when this {@link ProcessorDefinition} was created by its constructor.
     * <p/>
     * This can be used to know the order in which the definition was created when assembled as a route.
     *
     * @return the index number
     */
    public int getIndex() {
        return index;
    }

    // else to use an optional attribute in JAXB2
    public abstract List<ProcessorDefinition<?>> getOutputs();

    /**
     * Whether this definition can only be added as top-level directly on the route itself (such as
     * onException,onCompletion,intercept, etc.)
     * <p/>
     * If trying to add a top-level only definition to a nested output would fail in the
     * {@link #addOutput(ProcessorDefinition)} method.
     */
    public boolean isTopLevelOnly() {
        return false;
    }

    /**
     * Whether this model is abstract or not.
     * <p/>
     * An abstract model is something that is used for configuring cross cutting concerns such as error handling,
     * transaction policies, interceptors etc.
     * <p/>
     * Regular definitions is what is part of the route, such as ToDefinition, WireTapDefinition and the likes.
     * <p/>
     * Will by default return <tt>false</tt> to indicate regular definition, so all the abstract definitions must
     * override this method and return <tt>true</tt> instead.
     * <p/>
     * This information is used in camel-spring to let Camel work a bit on the model provided by JAXB from the Spring
     * XML file. This is needed to handle those cross cutting concerns properly. The Java DSL does not have this issue
     * as it can work this out directly using the fluent builder methods.
     *
     * @return <tt>true</tt> for abstract, otherwise <tt>false</tt> for regular.
     */
    public boolean isAbstract() {
        return false;
    }

    /**
     * Whether this definition is wrapping the entire output.
     * <p/>
     * When a definition is wrapping the entire output, the check to ensure that a route definition is empty should be
     * done on the wrapped output.
     *
     * @return <tt>true</tt> when wrapping the entire output.
     */
    public boolean isWrappingEntireOutput() {
        return false;
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        // grab camel context depends on if this is a regular route or a route configuration
        CamelContext context = this.getCamelContext();
        if (context == null) {
            RouteDefinition route = ProcessorDefinitionHelper.getRoute(this);
            if (route != null) {
                context = route.getCamelContext();
            } else {
                RouteConfigurationDefinition rc = this.getRouteConfiguration();
                if (rc != null) {
                    context = rc.getCamelContext();
                }
            }
        }

        // inject context
        CamelContextAware.trySetCamelContext(output, context);

        if (!(this instanceof OutputNode)) {
            getParent().addOutput(output);
            return;
        }

        if (!blocks.isEmpty()) {
            // let the Block deal with the output
            Block block = blocks.getLast();
            block.addOutput(output);
            return;
        }

        // validate that top-level is only added on the route (eg top level) (or
        // still allow if using advice-with)
        boolean parentIsRoute = RouteDefinition.class.isAssignableFrom(this.getClass())
                || AdviceWithDefinition.class.isAssignableFrom(this.getClass());
        if (output.isTopLevelOnly() && !parentIsRoute) {
            throw new IllegalArgumentException(
                    "The output must be added as top-level on the route. Try moving " + output + " to the top of route.");
        }

        output.setParent(this);
        configureChild(output);
        getOutputs().add(output);

        if (context != null && (context.isSourceLocationEnabled() || context.isDebugging() || context.isTracing())) {
            // we want to capture source location:line for every output
            Resource resource = this instanceof ResourceAware ? ((ResourceAware) this).getResource() : null;
            ProcessorDefinitionHelper.prepareSourceLocation(resource, output);
        }
    }

    public void clearOutput() {
        getOutputs().clear();
        blocks.clear();
    }

    /**
     * Strategy to execute any custom logic before the {@link Processor} is created.
     */
    public void preCreateProcessor() {
        // noop
    }

    /**
     * Strategy for children to do any custom configuration
     *
     * @param output the child to be added as output to this
     */
    public void configureChild(ProcessorDefinition<?> output) {
        // noop
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     *
     * @param  uri the endpoint to send to
     * @return     the builder
     */
    public Type to(@AsEndpointUri String uri) {
        addOutput(new ToDefinition(uri));
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @return the builder
     */
    public ToDynamicDefinition toD() {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  uri the dynamic endpoint to send to (resolved using simple language by default)
     * @return     the builder
     */
    public Type toD(@AsEndpointUri String uri) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setUri(uri);
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  endpointProducerBuilder the dynamic endpoint to send to (resolved using simple language by default)
     * @return                         the builder
     */
    public Type toD(@AsEndpointUri EndpointProducerBuilder endpointProducerBuilder) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setEndpointProducerBuilder(endpointProducerBuilder);
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  uri       the dynamic endpoint to send to (resolved using simple language by default)
     * @param  cacheSize sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache} which is used to
     *                   cache and reuse producers.
     * @return           the builder
     */
    public Type toD(@AsEndpointUri String uri, int cacheSize) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setUri(uri);
        answer.setCacheSize(Integer.toString(cacheSize));
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  endpointProducerBuilder the dynamic endpoint to send to (resolved using simple language by default)
     * @param  cacheSize               sets the maximum size used by the {@link org.apache.camel.spi.ProducerCache}
     *                                 which is used to cache and reuse producers.
     * @return                         the builder
     */
    public Type toD(@AsEndpointUri EndpointProducerBuilder endpointProducerBuilder, int cacheSize) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setEndpointProducerBuilder(endpointProducerBuilder);
        answer.setCacheSize(Integer.toString(cacheSize));
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  uri                   the dynamic endpoint to send to (resolved using simple language by default)
     * @param  ignoreInvalidEndpoint ignore the invalidate endpoint exception when try to create a producer with that
     *                               endpoint
     * @return                       the builder
     */
    public Type toD(@AsEndpointUri String uri, boolean ignoreInvalidEndpoint) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setUri(uri);
        answer.setIgnoreInvalidEndpoint(Boolean.toString(ignoreInvalidEndpoint));
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given dynamic endpoint
     *
     * @param  endpointProducerBuilder the dynamic endpoint to send to (resolved using simple language by default)
     * @param  ignoreInvalidEndpoint   ignore the invalidate endpoint exception when try to create a producer with that
     *                                 endpoint
     * @return                         the builder
     */
    public Type toD(@AsEndpointUri EndpointProducerBuilder endpointProducerBuilder, boolean ignoreInvalidEndpoint) {
        ToDynamicDefinition answer = new ToDynamicDefinition();
        answer.setEndpointProducerBuilder(endpointProducerBuilder);
        answer.setIgnoreInvalidEndpoint(Boolean.toString(ignoreInvalidEndpoint));
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param  uri  the String formatted endpoint uri to send to
     * @param  args arguments for the string formatting of the uri
     * @return      the builder
     */
    public Type toF(@AsEndpointUri String uri, Object... args) {
        addOutput(new ToDefinition(String.format(uri, args)));
        return asType();
    }

    /**
     * Calls the service
     *
     * @return the builder
     */
    @Deprecated
    public ServiceCallDefinition serviceCall() {
        ServiceCallDefinition answer = new ServiceCallDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Calls the service
     *
     * @param  name the service name
     * @return      the builder
     */
    @Deprecated
    public Type serviceCall(String name) {
        ServiceCallDefinition answer = new ServiceCallDefinition();
        answer.setName(name);
        addOutput(answer);
        return asType();
    }

    /**
     * Calls the service
     *
     * @param  name the service name
     * @param  uri  the endpoint uri to use for calling the service
     * @return      the builder
     */
    @Deprecated
    public Type serviceCall(String name, @AsEndpointUri String uri) {
        ServiceCallDefinition answer = new ServiceCallDefinition();
        answer.setName(name);
        answer.setUri(uri);
        addOutput(answer);
        return asType();
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param  endpoint the endpoint to send to
     * @return          the builder
     */
    public Type to(Endpoint endpoint) {
        addOutput(new ToDefinition(endpoint));
        return asType();
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param  endpoint the endpoint to send to
     * @return          the builder
     */
    public Type to(@AsEndpointUri EndpointProducerBuilder endpoint) {
        addOutput(new ToDefinition(endpoint));
        return asType();
    }

    /**
     * Sends the exchange with certain exchange pattern to the given endpoint
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern the pattern to use for the message exchange
     * @param  uri     the endpoint to send to
     * @return         the builder
     */
    public Type to(ExchangePattern pattern, @AsEndpointUri String uri) {
        addOutput(new ToDefinition(uri, pattern));
        return asType();
    }

    /**
     * Sends the exchange with certain exchange pattern to the given endpoint
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern  the pattern to use for the message exchange
     * @param  endpoint the endpoint to send to
     * @return          the builder
     */
    public Type to(ExchangePattern pattern, Endpoint endpoint) {
        addOutput(new ToDefinition(endpoint, pattern));
        return asType();
    }

    /**
     * Sends the exchange with certain exchange pattern to the given endpoint
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern  the pattern to use for the message exchange
     * @param  endpoint the endpoint to send to
     * @return          the builder
     */
    public Type to(ExchangePattern pattern, EndpointProducerBuilder endpoint) {
        addOutput(new ToDefinition(endpoint, pattern));
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param  uris list of endpoints to send to
     * @return      the builder
     */
    public Type to(@AsEndpointUri String... uris) {
        for (String uri : uris) {
            addOutput(new ToDefinition(uri));
        }
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param  endpoints list of endpoints to send to
     * @return           the builder
     */
    public Type to(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint));
        }
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param  endpoints list of endpoints to send to
     * @return           the builder
     */
    public Type to(@AsEndpointUri EndpointProducerBuilder... endpoints) {
        for (EndpointProducerBuilder endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint));
        }
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern the pattern to use for the message exchanges
     * @param  uris    list of endpoints to send to
     * @return         the builder
     */
    public Type to(ExchangePattern pattern, @AsEndpointUri String... uris) {
        for (String uri : uris) {
            addOutput(new ToDefinition(uri, pattern));
        }
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern   the pattern to use for the message exchanges
     * @param  endpoints list of endpoints to send to
     * @return           the builder
     */
    public Type to(ExchangePattern pattern, Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint, pattern));
        }
        return asType();
    }

    /**
     * Sends the exchange to a list of endpoints
     * <p/>
     * Notice the existing MEP is preserved
     *
     * @param  pattern   the pattern to use for the message exchanges
     * @param  endpoints list of endpoints to send to
     * @return           the builder
     */
    public Type to(ExchangePattern pattern, @AsEndpointUri EndpointProducerBuilder... endpoints) {
        for (EndpointProducerBuilder endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint, pattern));
        }
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/exchange-pattern.html">ExchangePattern:</a> set the {@link ExchangePattern} into
     * the {@link Exchange}.
     * <p/>
     * The pattern set on the {@link Exchange} will be changed from this point going foward.
     *
     * @param  exchangePattern instance of {@link ExchangePattern}
     * @return                 the builder
     */
    public Type setExchangePattern(ExchangePattern exchangePattern) {
        addOutput(new SetExchangePatternDefinition(exchangePattern));
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/exchange-pattern.html">ExchangePattern:</a> set the {@link ExchangePattern} into
     * the {@link Exchange}.
     * <p/>
     * The pattern set on the {@link Exchange} will be changed from this point going foward.
     *
     * @param  exchangePattern the exchange pattern
     * @return                 the builder
     */
    public Type setExchangePattern(String exchangePattern) {
        addOutput(new SetExchangePatternDefinition(exchangePattern));
        return asType();
    }

    /**
     * Sets the id of this node.
     * <p/>
     * <b>Important:</b> If you want to set the id of the route, then you <b>must</b> use {@link #routeId(String)}
     * instead.
     *
     * @param  id the id
     * @return    the builder
     */
    @Override
    public Type id(String id) {
        if (this instanceof OutputNode && getOutputs().isEmpty()) {
            // set id on this
            setId(id);
        } else {

            // set it on last output as this is what the user means to do
            // for Block(s) with non empty getOutputs() the id probably refers
            // to the last definition in the current Block
            List<ProcessorDefinition<?>> outputs = getOutputs();
            if (!blocks.isEmpty()) {
                if (blocks.getLast() instanceof ProcessorDefinition) {
                    ProcessorDefinition<?> block = (ProcessorDefinition<?>) blocks.getLast();
                    if (!block.getOutputs().isEmpty()) {
                        outputs = block.getOutputs();
                    }
                }
            }
            if (!getOutputs().isEmpty()) {
                outputs.get(outputs.size() - 1).setId(id);
            } else {
                // the output could be empty
                setId(id);
            }
        }

        return asType();
    }

    /**
     * Set the route id for this route.
     * <p/>
     * <b>Important: </b> Each route in the same {@link org.apache.camel.CamelContext} must have an <b>unique</b> route
     * id. If you use the API from {@link org.apache.camel.CamelContext} or {@link ModelCamelContext} to add routes,
     * then any new routes which has a route id that matches an old route, then the old route is replaced by the new
     * route.
     *
     * @param  id the route id, should be unique
     * @return    the builder
     */
    public Type routeId(String id) {
        ProcessorDefinition<?> def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            if (route.hasCustomIdAssigned()) {
                throw new IllegalArgumentException("You can only set routeId one time per route.");
            }
            route.setId(id);
        }

        return asType();
    }

    /**
     * Set the route group for this route.
     *
     * @param  group the route group
     * @return       the builder
     */
    public Type routeGroup(String group) {
        ProcessorDefinition<?> def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            route.setGroup(group);
        }

        return asType();
    }

    /**
     * Set the route description for this route
     *
     * @param  description the route description
     * @return             the builder
     */
    public Type routeDescription(String description) {
        ProcessorDefinition<?> def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            route.setDescription(description);
        }

        return asType();
    }

    /**
     * Sets a prefix to use for all node ids (not route id).
     *
     * @param  prefixId the prefix
     * @return          the builder
     */
    public Type nodePrefixId(String prefixId) {
        ProcessorDefinition<?> def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            route.setNodePrefixId(prefixId);
        }

        return asType();
    }

    /**
     * Disables this EIP from the route during build time. Once an EIP has been disabled then it cannot be enabled later
     * at runtime.
     */
    public Type disabled() {
        return disabled("true");
    }

    /**
     * Whether to disable this EIP from the route during build time. Once an EIP has been disabled then it cannot be
     * enabled later at runtime.
     */
    public Type disabled(boolean disabled) {
        return disabled(disabled ? "true" : "false");
    }

    /**
     * Whether to disable this EIP from the route during build time. Once an EIP has been disabled then it cannot be
     * enabled later at runtime.
     */
    public Type disabled(String disabled) {
        if (this instanceof OutputNode && getOutputs().isEmpty()) {
            // set id on this
            setDisabled(disabled);
        } else {

            // set it on last output as this is what the user means to do
            // for Block(s) with non empty getOutputs() the id probably refers
            // to the last definition in the current Block
            List<ProcessorDefinition<?>> outputs = getOutputs();
            if (!blocks.isEmpty()) {
                if (blocks.getLast() instanceof ProcessorDefinition) {
                    ProcessorDefinition<?> block = (ProcessorDefinition<?>) blocks.getLast();
                    if (!block.getOutputs().isEmpty()) {
                        outputs = block.getOutputs();
                    }
                }
            }
            if (!getOutputs().isEmpty()) {
                outputs.get(outputs.size() - 1).setDisabled(disabled);
            } else {
                // the output could be empty
                setDisabled(disabled);
            }
        }

        return asType();
    }

    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a> Multicasts messages to all its child outputs;
     * so that each processor and destination gets a copy of the original message to avoid the processors interfering
     * with each other.
     *
     * @return the builder
     */
    public MulticastDefinition multicast() {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a> Multicasts messages to all its child outputs;
     * so that each processor and destination gets a copy of the original message to avoid the processors interfering
     * with each other.
     *
     * @param  aggregationStrategy the strategy used to aggregate responses for every part
     * @param  parallelProcessing  if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return                     the builder
     */
    public MulticastDefinition multicast(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(Boolean.toString(parallelProcessing));
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a> Multicasts messages to all its child outputs;
     * so that each processor and destination gets a copy of the original message to avoid the processors interfering
     * with each other.
     *
     * @param  aggregationStrategy the strategy used to aggregate responses for every part
     * @return                     the builder
     */
    public MulticastDefinition multicast(AggregationStrategy aggregationStrategy) {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * Routes the message to a sequence of processors which is grouped together as one logical name.
     *
     * @return the builder
     */
    public StepDefinition step() {
        StepDefinition answer = new StepDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Routes the message to a sequence of processors which is grouped together as one logical name.
     *
     * @param  id unique id of the step within the camel context
     * @return    the builder
     */
    public StepDefinition step(String id) {
        StepDefinition answer = new StepDefinition();
        answer.setId(id);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="https://camel.apache.org/components/latest/eips/pipeline-eip.html">Pipes and Filters EIP:</a> Creates a
     * {@link org.apache.camel.processor.Pipeline} so that the message will get processed by each endpoint in turn and
     * for request/response the output of one endpoint will be the input of the next endpoint
     *
     * @return the builder
     */
    public PipelineDefinition pipeline() {
        PipelineDefinition answer = new PipelineDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="https://camel.apache.org/components/latest/eips/pipeline-eip.html">Pipes and Filters EIP:</a> Creates a
     * {@link org.apache.camel.processor.Pipeline} of the list of endpoints so that the message will get processed by
     * each endpoint in turn and for request/response the output of one endpoint will be the input of the next endpoint
     *
     * @param  uris list of endpoints
     * @return      the builder
     */
    public Type pipeline(@AsEndpointUri String... uris) {
        PipelineDefinition answer = new PipelineDefinition();
        addOutput(answer);
        answer.to(uris);
        return asType();
    }

    /**
     * <a href="https://camel.apache.org/components/latest/eips/pipeline-eip.html">Pipes and Filters EIP:</a> Creates a
     * {@link org.apache.camel.processor.Pipeline} of the list of endpoints so that the message will get processed by
     * each endpoint in turn and for request/response the output of one endpoint will be the input of the next endpoint
     *
     * @param  endpoints list of endpoints
     * @return           the builder
     */
    public Type pipeline(Endpoint... endpoints) {
        PipelineDefinition answer = new PipelineDefinition();
        addOutput(answer);
        answer.to(endpoints);
        return asType();
    }

    /**
     * Continues processing the {@link org.apache.camel.Exchange} using asynchronous routing engine.
     *
     * @return the builder
     */
    public ThreadsDefinition threads() {
        ThreadsDefinition answer = new ThreadsDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Continues processing the {@link org.apache.camel.Exchange} using asynchronous routing engine.
     *
     * @param  poolSize the core pool size
     * @return          the builder
     */
    public ThreadsDefinition threads(int poolSize) {
        ThreadsDefinition answer = new ThreadsDefinition();
        answer.setPoolSize(Integer.toString(poolSize));
        addOutput(answer);
        return answer;
    }

    /**
     * Continues processing the {@link org.apache.camel.Exchange} using asynchronous routing engine.
     *
     * @param  poolSize    the core pool size
     * @param  maxPoolSize the maximum pool size
     * @return             the builder
     */
    public ThreadsDefinition threads(int poolSize, int maxPoolSize) {
        ThreadsDefinition answer = new ThreadsDefinition();
        answer.setPoolSize(Integer.toString(poolSize));
        answer.setMaxPoolSize(Integer.toString(maxPoolSize));
        addOutput(answer);
        return answer;
    }

    /**
     * Continues processing the {@link org.apache.camel.Exchange} using asynchronous routing engine.
     *
     * @param  poolSize    the core pool size
     * @param  maxPoolSize the maximum pool size
     * @param  threadName  the thread pool name
     * @return             the builder
     */
    public ThreadsDefinition threads(int poolSize, int maxPoolSize, String threadName) {
        ThreadsDefinition answer = new ThreadsDefinition();
        answer.setPoolSize(Integer.toString(poolSize));
        answer.setMaxPoolSize(Integer.toString(maxPoolSize));
        answer.setThreadName(threadName);
        addOutput(answer);
        return answer;
    }

    /**
     * Ends the current block
     *
     * @return the builder
     */
    public ProcessorDefinition<?> end() {
        // must do this ugly cast to avoid compiler error on AIX/HP-UX
        ProcessorDefinition<?> defn = (ProcessorDefinition<?>) this;

        // when using choice .. when .. otherwise - doTry .. doCatch ..
        // doFinally we should always
        // end the choice/try definition to avoid having to use 2 x end() in the
        // route
        // this is counter intuitive for end users
        // TODO (camel-3.0): this should be done inside of TryDefinition or even
        // better
        // in Block(s) in general, but the api needs to be revisited for that.
        if (defn instanceof TryDefinition || defn instanceof ChoiceDefinition) {
            popBlock();
        }

        if (blocks.isEmpty()) {
            if (parent == null) {
                return this.endParent();
            }
            return parent.endParent();
        }
        popBlock();
        return this.endParent();
    }

    /**
     * Strategy to allow {@link ProcessorDefinition}s to have special logic when using end() in the DSL to return back
     * to the intended parent.
     * <p/>
     * For example a content based router we return back to the {@link ChoiceDefinition} when we end() from a
     * {@link WhenDefinition}.
     *
     * @return the end
     */
    public ProcessorDefinition<?> endParent() {
        return this;
    }

    /**
     * Ends the current block and returns back to the {@link ChoiceDefinition choice()} DSL.
     * <p/>
     * <b>Important:</b> If you want to end the entire choice block, then use {@link #end()} instead. The purpose of
     * {@link #endChoice()} is to return <i>control</i> back to the {@link ChoiceDefinition choice()} DSL, so you can
     * add subsequent <tt>when</tt> and <tt>otherwise</tt> to the choice. There can be situations where you would need
     * to use {@link #endChoice()} often when you add additional EIPs inside the <tt>when</tt>'s, and the DSL
     * <t>looses</t> scope when using a regular {@link #end()}, and you would need to use this {@link #endChoice()} to
     * return back the scope to the {@link ChoiceDefinition choice()} DSL.
     * <p/>
     * For more details and examples see also this FAQ:
     * <a href= "http://camel.apache.org/why-can-i-not-use-when-or-otherwise-in-a-java-camel-route.html">Why can I not
     * use when or otherwise in a Java Camel route </a>.
     *
     * @return the choice builder
     */
    public ChoiceDefinition endChoice() {
        // are we nested choice?
        ProcessorDefinition<?> def = this;
        if (def.getParent() instanceof WhenDefinition) {
            return (ChoiceDefinition) def.getParent().getParent();
        }

        // are we already a choice?
        if (def instanceof ChoiceDefinition) {
            return (ChoiceDefinition) def;
        }

        // okay end this and get back to the choice
        def = end();
        if (def instanceof WhenDefinition) {
            return (ChoiceDefinition) def.getParent();
        } else if (def instanceof OtherwiseDefinition) {
            return (ChoiceDefinition) def.getParent();
        } else {
            return (ChoiceDefinition) def;
        }
    }

    /**
     * Ends the current block and returns back to the {@link TryDefinition doTry()} DSL.
     *
     * @return the builder
     */
    public TryDefinition endDoTry() {
        ProcessorDefinition<?> def = this;

        // are we already a try?
        if (def instanceof TryDefinition) {
            // then we need special logic to end
            TryDefinition td = (TryDefinition) def;
            return (TryDefinition) td.onEndDoTry();
        }

        // okay end this and get back to the try
        def = end();
        return (TryDefinition) def;
    }

    /**
     * Ends the current block and returns back to the {@link CatchDefinition doCatch()} DSL.
     *
     * @return the builder
     */
    public CatchDefinition endDoCatch() {
        ProcessorDefinition<?> def = this;

        // are we already a doCatch?
        if (def instanceof CatchDefinition) {
            return (CatchDefinition) def;
        }

        // okay end this and get back to the try
        def = end();
        return (CatchDefinition) def;
    }

    /**
     * Ends the current block and returns back to the {@link CircuitBreakerDefinition circuitBreaker()} DSL.
     *
     * @return the builder
     */
    public CircuitBreakerDefinition endCircuitBreaker() {
        ProcessorDefinition<?> def = this;

        // are we already a try?
        if (def instanceof CircuitBreakerDefinition) {
            return (CircuitBreakerDefinition) def;
        }

        // okay end this and get back to the try
        def = end();
        return (CircuitBreakerDefinition) def;
    }

    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a> Creates an
     * {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer} using a fluent builder.
     */
    public ExpressionClause<IdempotentConsumerDefinition> idempotentConsumer() {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a> Creates an
     * {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer} to avoid duplicate messages
     *
     * @param  messageIdExpression expression to test of duplicate messages
     * @return                     the builder
     */
    public IdempotentConsumerDefinition idempotentConsumer(Expression messageIdExpression) {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition();
        answer.setExpression(ExpressionNodeHelper.toExpressionDefinition(messageIdExpression));
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a> Creates an
     * {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer} to avoid duplicate messages
     *
     * @param  messageIdExpression  expression to test of duplicate messages
     * @param  idempotentRepository the repository to use for duplicate check
     * @return                      the builder
     */
    public IdempotentConsumerDefinition idempotentConsumer(
            Expression messageIdExpression, IdempotentRepository idempotentRepository) {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition(messageIdExpression, idempotentRepository);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a> Creates a predicate expression
     * which only if it is <tt>true</tt> then the exchange is forwarded to the destination
     *
     * @return the clause used to create the filter expression
     */
    @AsPredicate
    public ExpressionClause<? extends FilterDefinition> filter() {
        FilterDefinition filter = new FilterDefinition();
        addOutput(filter);
        return createAndSetExpression(filter);
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a> Creates a predicate which is
     * applied and only if it is <tt>true</tt> then the exchange is forwarded to the destination
     *
     * @param  predicate predicate to use
     * @return           the builder
     */
    public FilterDefinition filter(@AsPredicate Predicate predicate) {
        FilterDefinition filter = new FilterDefinition(predicate);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a> Creates a predicate expression
     * which only if it is <tt>true</tt> then the exchange is forwarded to the destination
     *
     * @param  expression the predicate expression to use
     * @return            the builder
     */
    public FilterDefinition filter(@AsPredicate ExpressionDefinition expression) {
        FilterDefinition filter = new FilterDefinition(expression);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a> Creates a predicate language
     * expression which only if it is <tt>true</tt> then the exchange is forwarded to the destination
     *
     * @param  language   language for expression
     * @param  expression the expression
     * @return            the builder
     */
    public FilterDefinition filter(String language, @AsPredicate String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    /**
     * Creates a validation expression which only if it is <tt>true</tt> then the exchange is forwarded to the
     * destination. Otherwise a {@link org.apache.camel.support.processor.PredicateValidationException} is thrown.
     *
     * @param  expression the expression
     * @return            the builder
     */
    public ValidateDefinition validate(@AsPredicate Expression expression) {
        ValidateDefinition answer = new ValidateDefinition(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a validation expression which only if it is <tt>true</tt> then the exchange is forwarded to the
     * destination. Otherwise a {@link org.apache.camel.support.processor.PredicateValidationException} is thrown.
     *
     * @param  predicate the predicate
     * @return           the builder
     */
    public ValidateDefinition validate(@AsPredicate Predicate predicate) {
        ValidateDefinition answer = new ValidateDefinition(predicate);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a validation expression which only if it is <tt>true</tt> then the exchange is forwarded to the
     * destination. Otherwise a {@link org.apache.camel.support.processor.PredicateValidationException} is thrown.
     *
     * @return the builder
     */
    @AsPredicate
    public ExpressionClause<ValidateDefinition> validate() {
        ValidateDefinition answer = new ValidateDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * Creates a Circuit Breaker EIP.
     * <p/>
     * This requires having an implementation on the classpath such as camel-microprofile-fault-tolerance.
     *
     * @return the builder
     */
    public CircuitBreakerDefinition circuitBreaker() {
        CircuitBreakerDefinition answer = new CircuitBreakerDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a Kamelet EIP.
     * <p/>
     * This requires having camel-kamelet on the classpath.
     *
     * @return the builder
     */
    public KameletDefinition kamelet(String name) {
        KameletDefinition answer = new KameletDefinition();
        answer.setName(name);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/load-balancer.html">Load Balancer EIP:</a> Creates a loadbalance
     *
     * @return the builder
     */
    public LoadBalanceDefinition loadBalance() {
        LoadBalanceDefinition answer = new LoadBalanceDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/load-balancer.html">Load Balancer EIP:</a> Creates a loadbalance
     *
     * @param  loadBalancer a custom load balancer to use
     * @return              the builder
     */
    public LoadBalanceDefinition loadBalance(LoadBalancer loadBalancer) {
        LoadBalanceDefinition answer = new LoadBalanceDefinition();
        addOutput(answer);
        return answer.loadBalance(loadBalancer);
    }

    /**
     * Creates a log message to be logged at INFO level.
     *
     * @param  message the log message (you can use simple language syntax)
     * @return         the builder
     */
    public Type log(String message) {
        LogDefinition answer = new LogDefinition(message);
        addOutput(answer);
        return asType();
    }

    /**
     * Creates a log message to be logged at the given level.
     *
     * @param  loggingLevel the logging level to use
     * @param  message      the log message (you can use simple language syntax)
     * @return              the builder
     */
    public Type log(LoggingLevel loggingLevel, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel.name());
        addOutput(answer);
        return asType();
    }

    /**
     * Creates a log message to be logged at the given level and name.
     *
     * @param  loggingLevel the logging level to use
     * @param  logName      the log name to use
     * @param  message      the log message (you can use simple language syntax)
     * @return              the builder
     */
    public Type log(LoggingLevel loggingLevel, String logName, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel.name());
        answer.setLogName(logName);
        addOutput(answer);
        return asType();
    }

    /**
     * Creates a log message to be logged at the given level using provided logger.
     *
     * @param  loggingLevel the logging level to use
     * @param  logger       the logger to use
     * @param  message      the log message (you can use simple language syntax)
     * @return              the builder
     */
    public Type log(LoggingLevel loggingLevel, Logger logger, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel.name());
        answer.setLogger(logger);
        addOutput(answer);
        return asType();
    }

    /**
     * Creates a log message to be logged at the given level and name.
     *
     * @param  loggingLevel the logging level to use
     * @param  logName      the log name to use
     * @param  marker       log marker name
     * @param  message      the log message (you can use simple language syntax)
     * @return              the builder
     */
    public Type log(LoggingLevel loggingLevel, String logName, String marker, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel.name());
        answer.setLogName(logName);
        answer.setMarker(marker);
        addOutput(answer);
        return asType();
    }

    /**
     * Creates a log message to be logged at the given level using provided logger.
     *
     * @param  loggingLevel the logging level to use
     * @param  logger       the logger to use
     * @param  marker       log marker name
     * @param  message      the log message (you can use simple language syntax)
     * @return              the builder
     */
    public Type log(LoggingLevel loggingLevel, Logger logger, String marker, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel.name());
        answer.setLogger(logger);
        answer.setMarker(marker);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/content-based-router.html">Content Based Router EIP:</a> Creates a choice of one
     * or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    public ChoiceDefinition choice() {
        ChoiceDefinition answer = new ChoiceDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a try/catch block
     *
     * @return the builder for a tryBlock expression
     */
    public TryDefinition doTry() {
        TryDefinition answer = new TryDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a> Creates a dynamic recipient list
     * allowing you to route messages to a number of dynamically specified recipients.
     * <p/>
     * Will use comma as default delimiter.
     *
     * @param  recipients expression to decide the destinations
     * @return            the builder
     */
    public RecipientListDefinition<Type> recipientList(@AsEndpointUri Expression recipients) {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<>(recipients);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a> Creates a dynamic recipient list
     * allowing you to route messages to a number of dynamically specified recipients
     *
     * @param  recipients expression to decide the destinations
     * @param  delimiter  a custom delimiter to use
     * @return            the builder
     */
    public RecipientListDefinition<Type> recipientList(@AsEndpointUri Expression recipients, String delimiter) {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<>(recipients);
        answer.setDelimiter(delimiter);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a> Creates a dynamic recipient list
     * allowing you to route messages to a number of dynamically specified recipients
     *
     * @param  delimiter a custom delimiter to use
     * @return           the builder
     */
    @AsEndpointUri
    public ExpressionClause<RecipientListDefinition<Type>> recipientList(String delimiter) {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<>();
        answer.setDelimiter(delimiter);
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a> Creates a dynamic recipient list
     * allowing you to route messages to a number of dynamically specified recipients
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    @AsEndpointUri
    public ExpressionClause<RecipientListDefinition<Type>> recipientList() {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<>();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a> Creates a routing slip allowing you to
     * route a message consecutively through a series of processing steps where the sequence of steps is not known at
     * design time and can vary for each message.
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param  expression   to decide the destinations
     * @param  uriDelimiter is the delimiter that will be used to split up the list of URIs in the routing slip.
     * @return              the builder
     */
    public RoutingSlipDefinition<Type> routingSlip(@AsEndpointUri Expression expression, String uriDelimiter) {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<>(expression, uriDelimiter);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a> Creates a routing slip allowing you to
     * route a message consecutively through a series of processing steps where the sequence of steps is not known at
     * design time and can vary for each message.
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param  expression to decide the destinations
     * @return            the builder
     */
    public RoutingSlipDefinition<Type> routingSlip(@AsEndpointUri Expression expression) {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<>(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a> Creates a routing slip allowing you to
     * route a message consecutively through a series of processing steps where the sequence of steps is not known at
     * design time and can vary for each message.
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    public ExpressionClause<RoutingSlipDefinition<Type>> routingSlip() {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<>();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router EIP:</a> Creates a dynamic router allowing
     * you to route a message consecutively through a series of processing steps where the sequence of steps is not
     * known at design time and can vary for each message.
     * <p/>
     * <br/>
     * <b>Important:</b> The expression will be invoked repeatedly until it returns <tt>null</tt>, so be sure it does
     * that, otherwise it will be invoked endlessly.
     *
     * @param  expression to decide the destinations, which will be invoked repeatedly until it evaluates <tt>null</tt>
     *                    to indicate no more destinations.
     * @return            the builder
     */
    public DynamicRouterDefinition<Type> dynamicRouter(@AsEndpointUri Expression expression) {
        DynamicRouterDefinition<Type> answer = new DynamicRouterDefinition<>(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router EIP:</a> Creates a dynamic router allowing
     * you to route a message consecutively through a series of processing steps where the sequence of steps is not
     * known at design time and can vary for each message.
     * <p/>
     * <br/>
     * <b>Important:</b> The expression will be invoked repeatedly until it returns <tt>null</tt>, so be sure it does
     * that, otherwise it will be invoked endlessly.
     *
     * @return the expression clause to configure the expression to decide the destinations, which will be invoked
     *         repeatedly until it evaluates <tt>null</tt> to indicate no more destinations.
     */
    @AsEndpointUri
    public ExpressionClause<DynamicRouterDefinition<Type>> dynamicRouter() {
        DynamicRouterDefinition<Type> answer = new DynamicRouterDefinition<>();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a> Creates a sampling throttler allowing you
     * to extract a sample of exchanges from the traffic on a route. It is configured with a sampling period, during
     * which only a single exchange is allowed to pass through. All other exchanges will be stopped.
     * <p/>
     * Default period is one second.
     *
     * @return the builder
     */
    public SamplingDefinition sample() {
        return sample(Duration.ofSeconds(1));
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a> Creates a sampling throttler allowing you
     * to extract a sample of exchanges from the traffic through a route. It is configured with a sampling period during
     * which only a single exchange is allowed to pass through. All other exchanges will be stopped.
     *
     * @param  samplePeriod this is the sample interval, only one exchange is allowed through in this interval
     * @return              the builder
     */
    public SamplingDefinition sample(Duration samplePeriod) {
        SamplingDefinition answer = new SamplingDefinition(samplePeriod);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a> Creates a sampling throttler allowing you
     * to extract a sample of exchanges from the traffic through a route. It is configured with a sampling period during
     * which only a single exchange is allowed to pass through. All other exchanges will be stopped.
     *
     * @param  samplePeriod this is the sample interval, only one exchange is allowed through in this interval
     * @return              the builder
     */
    public SamplingDefinition sample(String samplePeriod) {
        SamplingDefinition answer = new SamplingDefinition(samplePeriod);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a> Creates a sampling throttler allowing you
     * to extract a sample of exchanges from the traffic through a route. It is configured with a sampling message
     * frequency during which only a single exchange is allowed to pass through. All other exchanges will be stopped.
     *
     * @param  messageFrequency this is the sample message frequency, only one exchange is allowed through for this many
     *                          messages received
     * @return                  the builder
     */
    public SamplingDefinition sample(long messageFrequency) {
        SamplingDefinition answer = new SamplingDefinition(messageFrequency);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a> Creates a splitter allowing you split a message
     * into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the original input message. You can use a custom {@link AggregationStrategy} to
     * control what to respond from the splitter.
     *
     * @return the expression clause builder for the expression on which to split
     */
    public ExpressionClause<SplitDefinition> split() {
        SplitDefinition answer = new SplitDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a> Creates a splitter allowing you split a message
     * into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the original input message. You can use a custom {@link AggregationStrategy} to
     * control what to respond from the splitter.
     *
     * @param  expression the expression on which to split the message
     * @return            the builder
     */
    public SplitDefinition split(Expression expression) {
        SplitDefinition answer = new SplitDefinition(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a> Creates a splitter allowing you split a message
     * into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the original input message. You can use a custom {@link AggregationStrategy} to
     * control what to respond from the splitter.
     *
     * @param  expression the expression on which to split the message
     * @param  delimiter  a custom delimiter to use
     * @return            the builder
     */
    public SplitDefinition split(Expression expression, String delimiter) {
        SplitDefinition answer = new SplitDefinition(expression);
        answer.setDelimiter(delimiter);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a> Creates a splitter allowing you split a message
     * into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param  expression          the expression on which to split
     * @param  aggregationStrategy the strategy used to aggregate responses for every part
     * @return                     the builder
     */
    public SplitDefinition split(Expression expression, AggregationStrategy aggregationStrategy) {
        SplitDefinition answer = new SplitDefinition(expression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a> Creates a resequencer allowing you to
     * reorganize messages based on some comparator.
     *
     * @return the expression clause for the expressions on which to compare messages in order
     */
    public ExpressionClause<ResequenceDefinition> resequence() {
        ResequenceDefinition answer = new ResequenceDefinition();
        ExpressionClause<ResequenceDefinition> clause = new ExpressionClause<>(answer);
        answer.setExpression(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a> Creates a resequencer allowing you to
     * reorganize messages based on some comparator.
     *
     * @param  expression the expression on which to compare messages in order
     * @return            the builder
     */
    public ResequenceDefinition resequence(Expression expression) {
        ResequenceDefinition answer = new ResequenceDefinition(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a> Creates an aggregator allowing you to
     * combine a number of messages together into a single message.
     *
     * @return the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregateDefinition> aggregate() {
        AggregateDefinition answer = new AggregateDefinition();
        ExpressionClause<AggregateDefinition> clause = new ExpressionClause<>(answer);
        answer.setExpression(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a> Creates an aggregator allowing you to
     * combine a number of messages together into a single message.
     *
     * @param  aggregationStrategy the strategy used for the aggregation
     * @return                     the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregateDefinition> aggregate(AggregationStrategy aggregationStrategy) {
        AggregateDefinition answer = new AggregateDefinition();
        ExpressionClause<AggregateDefinition> clause = new ExpressionClause<>(answer);
        answer.setExpression(clause);
        answer.setAggregationStrategy(aggregationStrategy);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a> Creates an aggregator allowing you to
     * combine a number of messages together into a single message.
     *
     * @param  correlationExpression the expression used to calculate the correlation key. For a JMS message this could
     *                               be the expression <code>header("JMSDestination")</code> or
     *                               <code>header("JMSCorrelationID")</code>
     * @return                       the builder
     */
    public AggregateDefinition aggregate(Expression correlationExpression) {
        AggregateDefinition answer = new AggregateDefinition(correlationExpression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a> Creates an aggregator allowing you to
     * combine a number of messages together into a single message.
     *
     * @param  correlationExpression the expression used to calculate the correlation key. For a JMS message this could
     *                               be the expression <code>header("JMSDestination")</code> or
     *                               <code>header("JMSCorrelationID")</code>
     * @param  aggregationStrategy   the strategy used for the aggregation
     * @return                       the builder
     */
    public AggregateDefinition aggregate(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        AggregateDefinition answer = new AggregateDefinition(correlationExpression, aggregationStrategy);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a> Creates a delayer allowing you to delay the
     * delivery of messages to some destination.
     *
     * @param  delay an expression to calculate the delay time in millis
     * @return       the builder
     */
    public DelayDefinition delay(Expression delay) {
        DelayDefinition answer = new DelayDefinition(delay);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a> Creates a delayer allowing you to delay the
     * delivery of messages to some destination.
     *
     * @return the expression clause to create the expression
     */
    public ExpressionClause<DelayDefinition> delay() {
        DelayDefinition answer = new DelayDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a> Creates a delayer allowing you to delay the
     * delivery of messages to some destination.
     *
     * @param  delay the delay in millis
     * @return       the builder
     */
    public DelayDefinition delay(long delay) {
        return delay(ExpressionBuilder.constantExpression(delay));
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a> Creates a throttler using a fluent builder.
     *
     * @return the builder
     */
    public ExpressionClause<ThrottleDefinition> throttle() {
        ThrottleDefinition answer = new ThrottleDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a> Creates a throttler allowing you to ensure
     * that a specific endpoint does not get overloaded, or that we don't exceed an agreed SLA with some external
     * service.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10 will default ensure at
     * most 10 messages per second.
     *
     * @param  maximumRequestCount the maximum messages
     * @return                     the builder
     */
    public ThrottleDefinition throttle(long maximumRequestCount) {
        return throttle(ExpressionBuilder.constantExpression(maximumRequestCount));
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a> Creates a throttler allowing you to ensure
     * that a specific endpoint does not get overloaded, or that we don't exceed an agreed SLA with some external
     * service.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10 will default ensure at
     * most 10 messages per second.
     *
     * @param  maximumRequestCount an expression to calculate the maximum request count
     * @return                     the builder
     */
    public ThrottleDefinition throttle(Expression maximumRequestCount) {
        ThrottleDefinition answer = new ThrottleDefinition(maximumRequestCount);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a> Creates a throttler allowing you to ensure
     * that a specific endpoint does not get overloaded, or that we don't exceed an agreed SLA with some external
     * service. Here another parameter correlationExpressionKey is introduced for the functionality which will throttle
     * based on the key expression to group exchanges. This will make key-based throttling instead of overall
     * throttling.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10 will default ensure at
     * most 10 messages per second.
     *
     * @param  maximumRequestCount      an expression to calculate the maximum request count
     * @param  correlationExpressionKey is a correlation key that can throttle by the given key instead of overall
     *                                  throttling
     * @return                          the builder
     */
    public ThrottleDefinition throttle(Expression maximumRequestCount, long correlationExpressionKey) {
        ThrottleDefinition answer
                = new ThrottleDefinition(maximumRequestCount, ExpressionBuilder.constantExpression(correlationExpressionKey));
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a> Creates a throttler allowing you to ensure
     * that a specific endpoint does not get overloaded, or that we don't exceed an agreed SLA with some external
     * service. Here another parameter correlationExpressionKey is introduced for the functionality which will throttle
     * based on the key expression to group exchanges. This will make key-based throttling instead of overall
     * throttling.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10 will default ensure at
     * most 10 messages per second.
     *
     * @param  maximumRequestCount      an expression to calculate the maximum request count
     * @param  correlationExpressionKey is a correlation key as an expression that can throttle by the given key instead
     *                                  of overall throttling
     * @return                          the builder
     */
    public ThrottleDefinition throttle(Expression maximumRequestCount, Expression correlationExpressionKey) {
        ThrottleDefinition answer = new ThrottleDefinition(maximumRequestCount, correlationExpressionKey);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a> Creates a loop allowing to process the a message a
     * number of times and possibly process them in a different way. Useful mostly for testing.
     *
     * @return the clause used to create the loop expression
     */
    public ExpressionClause<LoopDefinition> loop() {
        LoopDefinition loop = new LoopDefinition();
        addOutput(loop);
        return createAndSetExpression(loop);
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a> Creates a loop allowing to process the a message a
     * number of times and possibly process them in a different way.
     *
     * @param  expression the loop expression
     * @return            the builder
     */
    public LoopDefinition loop(Expression expression) {
        LoopDefinition loop = new LoopDefinition(expression);
        addOutput(loop);
        return loop;
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a> Creates a while loop allowing to process the a message
     * while the predicate matches and possibly process them in a different way.
     *
     * @param  predicate the while loop predicate
     * @return           the builder
     */
    public LoopDefinition loopDoWhile(@AsPredicate Predicate predicate) {
        LoopDefinition loop = new LoopDefinition(predicate);
        addOutput(loop);
        return loop;
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a> Creates a loop allowing to process the a message a
     * number of times and possibly process them in a different way using a fluent builder.
     *
     * @return the builder
     */
    public ExpressionClause<LoopDefinition> loopDoWhile() {
        LoopDefinition loop = new LoopDefinition();
        loop.setDoWhile(Boolean.toString(true));
        addOutput(loop);
        return createAndSetExpression(loop);
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a> Creates a loop allowing to process the a message a
     * number of times and possibly process them in a different way.
     *
     * @param  count the number of times
     * @return       the builder
     */
    public LoopDefinition loop(int count) {
        LoopDefinition loop = new LoopDefinition(new ConstantExpression(Integer.toString(count)));
        addOutput(loop);
        return loop;
    }

    /**
     * Sets the exception on the {@link org.apache.camel.Exchange}
     *
     * @param  exception the exception to throw
     * @return           the builder
     */
    public Type throwException(Exception exception) {
        ThrowExceptionDefinition answer = new ThrowExceptionDefinition();
        answer.setException(exception);
        addOutput(answer);
        return asType();
    }

    /**
     * Sets the exception on the {@link org.apache.camel.Exchange}
     *
     * @param  type    the exception class to use
     * @param  message the given message as caused message (supports simple language)
     * @return         the builder
     */
    public Type throwException(Class<? extends Exception> type, String message) {
        ThrowExceptionDefinition answer = new ThrowExceptionDefinition();
        answer.setExceptionClass(type);
        answer.setMessage(message);
        addOutput(answer);
        return asType();
    }

    /**
     * Marks the exchange for rollback only.
     * <p/>
     * Does <b>not</b> set any exception as opposed to {@link #rollback()} methods.
     *
     * @return the builder
     * @see    #rollback()
     * @see    #rollback(String)
     * @see    #markRollbackOnlyLast()
     */
    public Type markRollbackOnly() {
        RollbackDefinition answer = new RollbackDefinition();
        answer.setMarkRollbackOnly(Boolean.toString(true));
        addOutput(answer);
        return asType();
    }

    /**
     * Marks the exchange for rollback only, but only for the last (current) transaction.
     * <p/>
     * A last rollback is used when you have nested transactions and only want the last local transaction to rollback,
     * where as the outer transaction can still be completed
     * <p/>
     * Does <b>not</b> set any exception as opposed to {@link #rollback()} methods.
     *
     * @return the builder
     * @see    #rollback()
     * @see    #rollback(String)
     * @see    #markRollbackOnly()
     */
    public Type markRollbackOnlyLast() {
        RollbackDefinition answer = new RollbackDefinition();
        answer.setMarkRollbackOnlyLast(Boolean.toString(true));
        addOutput(answer);
        return asType();
    }

    /**
     * Marks the exchange for rollback only and sets an exception with a default message.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange and mark it for
     * rollback.
     *
     * @return the builder
     * @see    #markRollbackOnly()
     */
    public Type rollback() {
        return rollback(null);
    }

    /**
     * Marks the exchange for rollback and sets an exception with the provided message.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange and mark it for
     * rollback.
     *
     * @param  message an optional message used for logging purpose why the rollback was triggered
     * @return         the builder
     * @see            #markRollbackOnly()
     */
    public Type rollback(String message) {
        RollbackDefinition answer = new RollbackDefinition(message);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a> Sends messages to all its child outputs; so that
     * each processor and destination gets a copy of the original message to avoid the processors interfering with each
     * other using {@link ExchangePattern#InOnly}.
     *
     * @param  endpoint the endpoint to wiretap to
     * @return          the builder
     */
    public WireTapDefinition<Type> wireTap(Endpoint endpoint) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(endpoint.getEndpointUri());
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a> Sends messages to all its child outputs; so that
     * each processor and destination gets a copy of the original message to avoid the processors interfering with each
     * other using {@link ExchangePattern#InOnly}.
     *
     * @param  endpoint the endpoint to wiretap to
     * @return          the builder
     */
    public WireTapDefinition<Type> wireTap(@AsEndpointUri EndpointProducerBuilder endpoint) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setEndpointProducerBuilder(endpoint);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a> Sends messages to all its child outputs; so that
     * each processor and destination gets a copy of the original message to avoid the processors interfering with each
     * other using {@link ExchangePattern#InOnly}.
     *
     * @param  uri the dynamic endpoint to wiretap to (resolved using simple language by default)
     * @return     the builder
     */
    public WireTapDefinition<Type> wireTap(@AsEndpointUri String uri) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        addOutput(answer);
        return answer;
    }

    /**
     * Pushes the given block on the stack as current block
     *
     * @param block the block
     */
    void pushBlock(Block block) {
        blocks.add(block);
    }

    /**
     * Pops the block off the stack as current block
     *
     * @return the block
     */
    Block popBlock() {
        return blocks.isEmpty() ? null : blocks.removeLast();
    }

    public Type startupOrder(int startupOrder) {
        ProcessorDefinition<?> def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            route.startupOrder(startupOrder);
        }

        return asType();
    }

    /**
     * Stops continue routing the current {@link org.apache.camel.Exchange} and marks it as completed.
     *
     * @return the builder
     */
    public Type stop() {
        StopDefinition stop = new StopDefinition();
        addOutput(stop);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptionType the exception to catch
     * @return               the exception builder to configure
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exceptionType) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exceptionType);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptions list of exceptions to catch
     * @return            the exception builder to configure
     */
    public OnExceptionDefinition onException(Class<? extends Throwable>... exceptions) {
        OnExceptionDefinition answer = new OnExceptionDefinition(Arrays.asList(exceptions));
        addOutput(answer);
        return answer;
    }

    /**
     * Apply a {@link Policy}.
     * <p/>
     * Policy can be used for transactional policies.
     *
     * @param  policy the policy to apply
     * @return        the policy builder to configure
     */
    public PolicyDefinition policy(Policy policy) {
        PolicyDefinition answer = new PolicyDefinition(policy);
        addOutput(answer);
        return answer;
    }

    /**
     * Apply a {@link Policy}.
     * <p/>
     * Policy can be used for transactional policies.
     *
     * @param  ref reference to lookup a policy in the registry
     * @return     the policy builder to configure
     */
    public PolicyDefinition policy(String ref) {
        PolicyDefinition answer = new PolicyDefinition();
        answer.setRef(ref);
        addOutput(answer);
        return answer;
    }

    /**
     * Marks this route as transacted and uses the default transacted policy found in the registry.
     *
     * @return the policy builder to configure
     */
    public TransactedDefinition transacted() {
        TransactedDefinition answer = new TransactedDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Marks this route as transacted.
     *
     * @param  ref reference to lookup a transacted policy in the registry
     * @return     the policy builder to configure
     */
    public TransactedDefinition transacted(String ref) {
        TransactedDefinition answer = new TransactedDefinition();
        answer.setRef(ref);
        addOutput(answer);
        return answer;
    }

    /**
     * Marks this route as participating in a saga.
     *
     * @return the saga definition
     */
    public SagaDefinition saga() {
        SagaDefinition answer = new SagaDefinition();
        addOutput(answer);
        return answer;
    }

    // Transformers
    // -------------------------------------------------------------------------

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds the custom processor
     * to this destination which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  processor the custom {@link Processor}
     * @return           the builder
     */
    public Type process(Processor processor) {
        ProcessDefinition answer = new ProcessDefinition(processor);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds the custom processor
     * reference to this destination which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  ref reference to a {@link Processor} to lookup in the registry
     * @return     the builder
     */
    public Type process(String ref) {
        ProcessDefinition answer = new ProcessDefinition();
        answer.setRef(ref);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds the custom processor
     * using a fluent builder to this destination which could be a final destination, or could be a transformation in a
     * pipeline
     *
     * @return the builder
     */
    public ProcessClause<ProcessorDefinition<Type>> process() {
        ProcessClause<ProcessorDefinition<Type>> clause = new ProcessClause<>(this);
        ProcessDefinition answer = new ProcessDefinition(clause);

        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean the bean to invoke, or a reference to a bean if the type is a String
     * @return      the builder
     */
    public Type bean(Object bean) {
        BeanDefinition answer = new BeanDefinition();
        if (bean instanceof String) {
            answer.setRef((String) bean);
        } else {
            answer.setBean(bean);
        }
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean the bean to invoke, or a reference to a bean if the type is a String
     * @return      the builder
     */
    public Type bean(Supplier<Object> bean) {
        return bean(bean.get());
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean   the bean to invoke (if String then a reference to a bean, prefix with type: to specify FQN java
     *                class)
     * @param  method the method name to invoke on the bean (can be used to avoid ambiguity)
     * @return        the builder
     */
    public Type bean(Object bean, String method) {
        BeanDefinition answer = new BeanDefinition();
        if (bean instanceof String) {
            String str = (String) bean;
            if (str.startsWith("type:")) {
                answer.setBeanType(str.substring(5));
            } else {
                answer.setRef(str);
            }
        } else {
            answer.setBean(bean);
        }
        answer.setMethod(method);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean   the bean to invoke, or a reference to a bean if the type is a String
     * @param  method the method name to invoke on the bean (can be used to avoid ambiguity)
     * @return        the builder
     */
    public Type bean(Supplier<Object> bean, String method) {
        return bean(bean.get(), method);
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean   the bean to invoke, or a reference to a bean if the type is a String
     * @param  method the method name to invoke on the bean (can be used to avoid ambiguity)
     * @param  scope  bean scope to use (singleton, request, prototype)
     * @return        the builder
     */
    public Type bean(Supplier<Object> bean, String method, BeanScope scope) {
        return bean(bean.get(), method, scope);
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean  the bean to invoke, or a reference to a bean if the type is a String
     * @param  scope bean scope to use (singleton, request, prototype)
     * @return       the builder
     */
    public Type bean(Object bean, BeanScope scope) {
        BeanDefinition answer = new BeanDefinition();
        if (bean instanceof String) {
            answer.setRef((String) bean);
        } else {
            answer.setBean(bean);
        }
        answer.setScope(scope);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  bean   the bean to invoke, or a reference to a bean if the type is a String
     * @param  method the method name to invoke on the bean (can be used to avoid ambiguity)
     * @param  scope  bean scope to use (singleton, request, prototype)
     * @return        the builder
     */
    public Type bean(Object bean, String method, BeanScope scope) {
        BeanDefinition answer = new BeanDefinition();
        if (bean instanceof String) {
            answer.setRef((String) bean);
        } else {
            answer.setBean(bean);
        }
        answer.setMethod(method);
        answer.setScope(scope);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType the bean class, Camel will instantiate an object at runtime
     * @return          the builder
     */
    public Type bean(Class<?> beanType) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType the bean class, Camel will instantiate an object at runtime
     * @param  scope    bean scope to use (singleton, request, prototype)
     * @return          the builder
     */
    public Type bean(Class<?> beanType, BeanScope scope) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        answer.setScope(scope);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType the bean class, Camel will instantiate an object at runtime
     * @param  method   the method name to invoke on the bean (can be used to avoid ambiguity)
     * @return          the builder
     */
    public Type bean(Class<?> beanType, String method) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        answer.setMethod(method);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a bean which is
     * invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType the bean class, Camel will instantiate an object at runtime
     * @param  method   the method name to invoke on the bean (can be used to avoid ambiguity)
     * @param  scope    bean scope to use (singleton, request, prototype)
     * @return          the builder
     */
    public Type bean(Class<?> beanType, String method, BeanScope scope) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        answer.setMethod(method);
        answer.setScope(scope);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the IN message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorDefinition<Type>> setBody() {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<>(this);
        SetBodyDefinition answer = new SetBodyDefinition(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the IN message
     *
     * @param  expression the expression used to set the body
     * @return            the builder
     */
    public Type setBody(Expression expression) {
        SetBodyDefinition answer = new SetBodyDefinition(expression);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the IN message
     *
     * @param  function the function that provides a value to the IN message body
     * @return          the builder
     */
    public <Result> Type setBody(Function<Exchange, Result> function) {
        SetBodyDefinition answer = new SetBodyDefinition(new ExpressionAdapter() {
            @Override
            public Result evaluate(Exchange exchange) {
                return function.apply(exchange);
            }
        });
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the OUT message
     *
     * @param  expression the expression used to set the body
     * @return            the builder
     */
    public Type transform(Expression expression) {
        TransformDefinition answer = new TransformDefinition(expression);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the OUT message according to a data type transformation.
     *
     * @param  fromType the data type representing the input of the transformation
     * @param  toType   the data type representing the output of the transformation.
     * @return          the builder
     */
    public Type transform(DataType fromType, DataType toType) {
        TransformDefinition answer = new TransformDefinition(fromType, toType);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the OUT message according to a data type transformation.
     *
     * @param  toType the data type representing the output of the transformation.
     * @return        the builder
     */
    public Type transform(DataType toType) {
        TransformDefinition answer = new TransformDefinition(DataType.ANY, toType);
        addOutput(answer);
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a> Adds a processor which sets
     * the body on the OUT message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorDefinition<Type>> transform() {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<>(this);
        TransformDefinition answer = new TransformDefinition(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Executes a script (do not change the message body).
     *
     * @param  expression the expression used as the script.
     * @return            the builder
     */
    public Type script(Expression expression) {
        ScriptDefinition answer = new ScriptDefinition(expression);
        addOutput(answer);
        return asType();
    }

    /**
     * Executes a script (do not change the message body).
     *
     * @return a expression builder clause to use as script.
     */
    public ExpressionClause<ProcessorDefinition<Type>> script() {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<>(this);
        ScriptDefinition answer = new ScriptDefinition(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the header on the IN message
     *
     * @param  name the header name
     * @return      a expression builder clause to set the header
     */
    public ExpressionClause<ProcessorDefinition<Type>> setHeader(String name) {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<>(this);
        SetHeaderDefinition answer = new SetHeaderDefinition(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the header on the IN message
     *
     * @param  name       the header name
     * @param  expression the expression used to set the header
     * @return            the builder
     */
    public Type setHeader(String name, Expression expression) {
        SetHeaderDefinition answer = new SetHeaderDefinition(name, expression);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which sets the header on the IN message
     *
     * @param  name     the header name
     * @param  supplier the supplier used to set the header
     * @return          the builder
     */
    public Type setHeader(String name, final Supplier<Object> supplier) {
        SetHeaderDefinition answer = new SetHeaderDefinition(name, new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return supplier.get();
            }
        });

        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which sets the exchange property
     *
     * @param  name       the property name
     * @param  expression the expression used to set the property
     * @return            the builder
     */
    public Type setProperty(String name, Expression expression) {
        SetPropertyDefinition answer = new SetPropertyDefinition(name, expression);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which sets the exchange property
     *
     * @param  name     the property name
     * @param  supplier the supplier used to set the property
     * @return          the builder
     */
    public Type setProperty(String name, final Supplier<Object> supplier) {
        SetPropertyDefinition answer = new SetPropertyDefinition(name, new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return supplier.get();
            }
        });

        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which sets the exchange property
     *
     * @param  name the property name
     * @return      a expression builder clause to set the property
     */
    public ExpressionClause<ProcessorDefinition<Type>> setProperty(String name) {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<>(this);
        SetPropertyDefinition answer = new SetPropertyDefinition(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which removes the header on the IN message
     *
     * @param  name the header name
     * @return      the builder
     */
    public Type removeHeader(String name) {
        RemoveHeaderDefinition answer = new RemoveHeaderDefinition(name);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which removes the headers on the IN message
     *
     * @param  pattern a pattern to match header names to be removed
     * @return         the builder
     */
    public Type removeHeaders(String pattern) {
        RemoveHeadersDefinition answer = new RemoveHeadersDefinition(pattern);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which removes the headers on the IN message
     *
     * @param  pattern         a pattern to match header names to be removed
     * @param  excludePatterns one or more pattern of header names that should be excluded (= preserved)
     * @return                 the builder
     */
    public Type removeHeaders(String pattern, String... excludePatterns) {
        RemoveHeadersDefinition answer = new RemoveHeadersDefinition(pattern, excludePatterns);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which removes the exchange property
     *
     * @param  name the property name
     * @return      the builder
     */
    public Type removeProperty(String name) {
        RemovePropertyDefinition answer = new RemovePropertyDefinition(name);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which removes the properties in the exchange
     *
     * @param  pattern a pattern to match properties names to be removed
     * @return         the builder
     */
    public Type removeProperties(String pattern) {
        RemovePropertiesDefinition answer = new RemovePropertiesDefinition(pattern);
        addOutput(answer);
        return asType();
    }

    /**
     * Adds a processor which removes the properties in the exchange
     *
     * @param  pattern         a pattern to match properties names to be removed
     * @param  excludePatterns one or more pattern of properties names that should be excluded (= preserved)
     * @return                 the builder
     */
    public Type removeProperties(String pattern, String... excludePatterns) {
        RemovePropertiesDefinition answer = new RemovePropertiesDefinition(pattern, excludePatterns);
        addOutput(answer);
        return asType();
    }

    /**
     * Converts the IN message body to the specified type
     *
     * @param  type the type to convert to
     * @return      the builder
     */
    public Type convertBodyTo(Class<?> type) {
        addOutput(new ConvertBodyDefinition(type));
        return asType();
    }

    /**
     * Converts the IN message body to the specified type
     *
     * @param  type      the type to convert to
     * @param  mandatory whether to use mandatory type conversion or not
     * @return           the builder
     */
    public Type convertBodyTo(Class<?> type, boolean mandatory) {
        addOutput(new ConvertBodyDefinition(type, mandatory));
        return asType();
    }

    /**
     * Converts the IN message body to the specified type
     *
     * @param  type    the type to convert to
     * @param  charset the charset to use by type converters (not all converters support specific charset)
     * @return         the builder
     */
    public Type convertBodyTo(Class<?> type, String charset) {
        addOutput(new ConvertBodyDefinition(type, charset));
        return asType();
    }

    /**
     * Sorts the expression using a default sorting based on toString representation.
     *
     * @param  expression the expression, must be convertable to {@link List}
     * @return            the builder
     */
    public Type sort(Expression expression) {
        return sort(expression, null);
    }

    /**
     * Sorts the expression using the given comparator
     *
     * @param  expression the expression, must be convertable to {@link List}
     * @param  comparator the comparator to use for sorting
     * @return            the builder
     */
    public <T> Type sort(Expression expression, Comparator<T> comparator) {
        addOutput(new SortDefinition<>(expression, comparator));
        return asType();
    }

    /**
     * Sorts the expression
     *
     * @return the builder
     */
    public <T> ExpressionClause<SortDefinition<T>> sort() {
        SortDefinition<T> answer = new SortDefinition<>();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * The <a href="http://camel.apache.org/claim-check.html">Claim Check EIP</a> allows you to replace message content
     * with a claim check (a unique key), which can be used to retrieve the message content at a later time.
     */
    public ClaimCheckDefinition claimCheck() {
        ClaimCheckDefinition answer = new ClaimCheckDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * The <a href="http://camel.apache.org/claim-check.html">Claim Check EIP</a> allows you to replace message content
     * with a claim check (a unique key), which can be used to retrieve the message content at a later time.
     *
     * @param operation the claim check operation to use.
     */
    public Type claimCheck(ClaimCheckOperation operation) {
        ClaimCheckDefinition answer = new ClaimCheckDefinition();
        answer.setOperation(operation.name());
        addOutput(answer);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/claim-check.html">Claim Check EIP</a> allows you to replace message content
     * with a claim check (a unique key), which can be used to retrieve the message content at a later time.
     *
     * @param operation the claim check operation to use.
     * @param key       the unique key to use for the get and set operations, can be <tt>null</tt> for push/pop
     *                  operations
     */
    public Type claimCheck(ClaimCheckOperation operation, String key) {
        return claimCheck(operation, key, null);
    }

    /**
     * The <a href="http://camel.apache.org/claim-check.html">Claim Check EIP</a> allows you to replace message content
     * with a claim check (a unique key), which can be used to retrieve the message content at a later time.
     *
     * @param operation the claim check operation to use.
     * @param key       the unique key to use for the get and set operations, can be <tt>null</tt> for push/pop
     *                  operations
     * @param filter    describes what data to include/exclude when merging data back when using get or pop operations.
     */
    public Type claimCheck(ClaimCheckOperation operation, String key, String filter) {
        ClaimCheckDefinition answer = new ClaimCheckDefinition();
        answer.setOperation(operation.name());
        answer.setKey(key);
        answer.setFilter(filter);
        addOutput(answer);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     * <p/>
     * The difference between this and {@link #pollEnrich(String)} is that this uses a producer to obatin the additional
     * data, where as pollEnrich uses a polling consumer.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.Enricher
     */
    public Type enrich(@AsEndpointUri String resourceUri) {
        return enrich(resourceUri, null);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.Enricher
     */
    public Type enrich(@AsEndpointUri String resourceUri, AggregationStrategy aggregationStrategy) {
        return enrich(resourceUri, aggregationStrategy, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     * <p/>
     * The difference between this and {@link #pollEnrich(String)} is that this uses a producer to obatin the additional
     * data, where as pollEnrich uses a polling consumer.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.Enricher
     */
    public Type enrich(@AsEndpointUri EndpointProducerBuilder resourceUri) {
        return enrich(resourceUri, null);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.Enricher
     */
    public Type enrich(@AsEndpointUri EndpointProducerBuilder resourceUri, AggregationStrategy aggregationStrategy) {
        return enrich(resourceUri, aggregationStrategy, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder. <blockquote>
     *
     * <pre>
     * {@code
     * fom("direct:start")
     *         .enrichWith("direct:resource")
     *         .body(String.class, (o, n) -> n + o);
     * }
     * </pre>
     *
     * </blockquote>
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.Enricher
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(@AsEndpointUri String resourceUri) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        enrich(resourceUri, clause);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder.
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(@AsEndpointUri String resourceUri, boolean aggregateOnException) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        enrich(resourceUri, clause, aggregateOnException, false);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder.
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(
            @AsEndpointUri String resourceUri, boolean aggregateOnException, boolean shareUnitOfWork) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        enrich(resourceUri, clause, aggregateOnException, shareUnitOfWork);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder. <blockquote>
     *
     * <pre>
     * {@code
     * fom("direct:start")
     *         .enrichWith("direct:resource")
     *         .body(String.class, (o, n) -> n + o);
     * }
     * </pre>
     *
     * </blockquote>
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.Enricher
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(@AsEndpointUri EndpointProducerBuilder resourceUri) {
        return enrichWith(resourceUri.getRawUri());
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder.
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(
            @AsEndpointUri EndpointProducerBuilder resourceUri, boolean aggregateOnException) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        enrich(resourceUri, clause, aggregateOnException, false);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder.
     */
    public EnrichClause<ProcessorDefinition<Type>> enrichWith(
            @AsEndpointUri EndpointProducerBuilder resourceUri, boolean aggregateOnException, boolean shareUnitOfWork) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        enrich(resourceUri, clause, aggregateOnException, shareUnitOfWork);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @return                      the builder
     * @see                         org.apache.camel.processor.Enricher
     */
    public Type enrich(
            @AsEndpointUri String resourceUri, AggregationStrategy aggregationStrategy, boolean aggregateOnException) {
        return enrich(resourceUri, aggregationStrategy, aggregateOnException, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @param  shareUnitOfWork      whether to share unit of work
     * @return                      the builder
     * @see                         org.apache.camel.processor.Enricher
     */
    public Type enrich(
            @AsEndpointUri String resourceUri, AggregationStrategy aggregationStrategy, boolean aggregateOnException,
            boolean shareUnitOfWork) {
        EnrichDefinition answer = new EnrichDefinition();
        answer.setExpression(new ConstantExpression(resourceUri));
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setAggregateOnException(Boolean.toString(aggregateOnException));
        answer.setShareUnitOfWork(Boolean.toString(shareUnitOfWork));
        addOutput(answer);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @return                      the builder
     * @see                         org.apache.camel.processor.Enricher
     */
    public Type enrich(
            @AsEndpointUri EndpointProducerBuilder resourceUri, AggregationStrategy aggregationStrategy,
            boolean aggregateOnException) {
        return enrich(resourceUri, aggregationStrategy, aggregateOnException, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @param  shareUnitOfWork      whether to share unit of work
     * @return                      the builder
     * @see                         org.apache.camel.processor.Enricher
     */
    public Type enrich(
            @AsEndpointUri EndpointProducerBuilder resourceUri, AggregationStrategy aggregationStrategy,
            boolean aggregateOnException, boolean shareUnitOfWork) {
        EnrichDefinition answer = new EnrichDefinition();
        answer.setExpression(new SimpleExpression(resourceUri.getRawUri()));
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setAggregateOnException(Boolean.toString(aggregateOnException));
        answer.setShareUnitOfWork(Boolean.toString(shareUnitOfWork));
        addOutput(answer);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code>.
     * <p/>
     * The difference between this and {@link #pollEnrich(String)} is that this uses a producer to obtain the additional
     * data, where as pollEnrich uses a polling consumer.
     *
     * @return a expression builder clause to set the expression to use for computing the endpoint to use
     * @see    org.apache.camel.processor.PollEnricher
     */
    @AsEndpointUri
    public ExpressionClause<EnrichDefinition> enrich() {
        EnrichDefinition answer = new EnrichDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * This method will <tt>block</tt> until data is available, use the method with timeout if you do not want to risk
     * waiting a long time before data is available from the resourceUri.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri String resourceUri) {
        return pollEnrich(resourceUri, null);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * This method will <b>block</b> until data is available, use the method with timeout if you do not want to risk
     * waiting a long time before data is available from the resourceUri.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri String resourceUri, AggregationStrategy aggregationStrategy) {
        return pollEnrich(resourceUri, -1, aggregationStrategy);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  timeout             timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri String resourceUri, long timeout, AggregationStrategy aggregationStrategy) {
        return pollEnrich(resourceUri, timeout, aggregationStrategy, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri            URI of resource endpoint for obtaining additional data.
     * @param  timeout                timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @return                        the builder
     * @see                           org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri String resourceUri, long timeout, String aggregationStrategyRef) {
        return pollEnrich(resourceUri, timeout, aggregationStrategyRef, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * This method will <tt>block</tt> until data is available, use the method with timeout if you do not want to risk
     * waiting a long time before data is available from the resourceUri.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @return             the builder
     * @see                org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(EndpointConsumerBuilder resourceUri) {
        return pollEnrich(new SimpleExpression(resourceUri.getRawUri()), -1, (String) null, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * This method will <b>block</b> until data is available, use the method with timeout if you do not want to risk
     * waiting a long time before data is available from the resourceUri.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(EndpointConsumerBuilder resourceUri, AggregationStrategy aggregationStrategy) {
        return pollEnrich(resourceUri, -1, aggregationStrategy);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri         URI of resource endpoint for obtaining additional data.
     * @param  timeout             timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategy aggregation strategy to aggregate input data and additional data.
     * @return                     the builder
     * @see                        org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(EndpointConsumerBuilder resourceUri, long timeout, AggregationStrategy aggregationStrategy) {
        return pollEnrich(resourceUri, timeout, aggregationStrategy, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri            URI of resource endpoint for obtaining additional data.
     * @param  timeout                timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @return                        the builder
     * @see                           org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(EndpointConsumerBuilder resourceUri, long timeout, String aggregationStrategyRef) {
        return pollEnrich(resourceUri, timeout, aggregationStrategyRef, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(@AsEndpointUri String resourceUri) {
        return pollEnrichWith(resourceUri, -1);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(@AsEndpointUri String resourceUri, long timeout) {
        return pollEnrichWith(resourceUri, timeout, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(
            @AsEndpointUri String resourceUri, long timeout, boolean aggregateOnException) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        pollEnrich(resourceUri, timeout, clause, aggregateOnException);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(EndpointConsumerBuilder resourceUri) {
        return pollEnrichWith(resourceUri, -1);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(EndpointConsumerBuilder resourceUri, long timeout) {
        return pollEnrichWith(resourceUri, timeout, false);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> and with an aggregation strategy created using a fluent
     * builder using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     */
    public EnrichClause<ProcessorDefinition<Type>> pollEnrichWith(
            EndpointConsumerBuilder resourceUri, long timeout, boolean aggregateOnException) {
        EnrichClause<ProcessorDefinition<Type>> clause = new EnrichClause<>(this);
        pollEnrich(resourceUri, timeout, clause, aggregateOnException);
        return clause;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  timeout              timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @return                      the builder
     * @see                         org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri String resourceUri, long timeout, AggregationStrategy aggregationStrategy,
            boolean aggregateOnException) {
        return pollEnrich(new ConstantExpression(resourceUri), timeout, aggregationStrategy, aggregateOnException);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri            URI of resource endpoint for obtaining additional data.
     * @param  timeout                timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException   whether to call
     *                                {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                                if an exception was thrown.
     * @return                        the builder
     * @see                           org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri String resourceUri, long timeout, String aggregationStrategyRef, boolean aggregateOnException) {
        return pollEnrich(new ConstantExpression(resourceUri), timeout, aggregationStrategyRef, aggregateOnException);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @param  timeout     timeout in millis to wait at most for data to be available.
     * @return             the builder
     * @see                org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri String resourceUri, long timeout) {
        return pollEnrich(resourceUri, timeout, (String) null);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri          URI of resource endpoint for obtaining additional data.
     * @param  timeout              timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @return                      the builder
     * @see                         org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri EndpointConsumerBuilder resourceUri, long timeout, AggregationStrategy aggregationStrategy,
            boolean aggregateOnException) {
        return pollEnrich(new SimpleExpression(resourceUri.getRawUri()), timeout, aggregationStrategy, aggregateOnException);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri            URI of resource endpoint for obtaining additional data.
     * @param  timeout                timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException   whether to call
     *                                {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                                if an exception was thrown.
     * @return                        the builder
     * @see                           org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri EndpointConsumerBuilder resourceUri, long timeout, String aggregationStrategyRef,
            boolean aggregateOnException) {
        return pollEnrich(new SimpleExpression(resourceUri.getRawUri()), timeout, aggregationStrategyRef, aggregateOnException);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  resourceUri URI of resource endpoint for obtaining additional data.
     * @param  timeout     timeout in millis to wait at most for data to be available.
     * @return             the builder
     * @see                org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(@AsEndpointUri EndpointConsumerBuilder resourceUri, long timeout) {
        return pollEnrich(resourceUri, timeout, (String) null);
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  expression             to use an expression to dynamically compute the endpoint to poll from
     * @param  timeout                timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException   whether to call
     *                                {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                                if an exception was thrown.
     * @return                        the builder
     * @see                           org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri Expression expression, long timeout, String aggregationStrategyRef, boolean aggregateOnException) {
        PollEnrichDefinition pollEnrich = new PollEnrichDefinition();
        pollEnrich.setExpression(expression);
        pollEnrich.setTimeout(Long.toString(timeout));
        pollEnrich.setAggregationStrategy(aggregationStrategyRef);
        pollEnrich.setAggregateOnException(Boolean.toString(aggregateOnException));
        addOutput(pollEnrich);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @param  expression           to use an expression to dynamically compute the endpoint to poll from
     * @param  timeout              timeout in millis to wait at most for data to be available.
     * @param  aggregationStrategy  aggregation strategy to aggregate input data and additional data.
     * @param  aggregateOnException whether to call
     *                              {@link AggregationStrategy#aggregate(org.apache.camel.Exchange, org.apache.camel.Exchange)}
     *                              if an exception was thrown.
     * @return                      the builder
     * @see                         org.apache.camel.processor.PollEnricher
     */
    public Type pollEnrich(
            @AsEndpointUri Expression expression, long timeout, AggregationStrategy aggregationStrategy,
            boolean aggregateOnException) {
        PollEnrichDefinition pollEnrich = new PollEnrichDefinition();
        pollEnrich.setExpression(expression);
        pollEnrich.setTimeout(Long.toString(timeout));
        pollEnrich.setAggregationStrategy(aggregationStrategy);
        pollEnrich.setAggregateOnException(Boolean.toString(aggregateOnException));
        addOutput(pollEnrich);
        return asType();
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a> enriches an exchange with
     * additional data obtained from a <code>resourceUri</code> using a {@link org.apache.camel.PollingConsumer} to poll
     * the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer to obtain the additional
     * data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}. If timeout is negative,
     * we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt> otherwise we use
     * <tt>receive(timeout)</tt>.
     *
     * @return a expression builder clause to set the expression to use for computing the endpoint to poll from
     * @see    org.apache.camel.processor.PollEnricher
     */
    @AsEndpointUri
    public ExpressionClause<PollEnrichDefinition> pollEnrich() {
        PollEnrichDefinition answer = new PollEnrichDefinition();
        addOutput(answer);
        return createAndSetExpression(answer);
    }

    /**
     * Adds a onCompletion {@link org.apache.camel.spi.Synchronization} hook that invoke this route as a callback when
     * the {@link org.apache.camel.Exchange} has finished being processed. The hook invoke callbacks for either
     * onComplete or onFailure.
     * <p/>
     * Will by default always trigger when the {@link org.apache.camel.Exchange} is complete (either with success or
     * failed). <br/>
     * You can limit the callback to either onComplete or onFailure but invoking the nested builder method.
     * <p/>
     * For onFailure the caused exception is stored as a property on the {@link org.apache.camel.Exchange} with the key
     * {@link org.apache.camel.Exchange#EXCEPTION_CAUGHT}.
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompletion() {
        OnCompletionDefinition answer = new OnCompletionDefinition();

        // remove all on completions if they are global scoped and we add a route scoped which
        // should override the global
        answer.removeAllOnCompletionDefinition(this);

        // create new block with the onCompletion
        popBlock();
        addOutput(answer);
        pushBlock(answer);
        return answer;
    }

    // DataFormat support
    // -------------------------------------------------------------------------

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using a
     * {@link DataFormat} expression to define the format of the input message and the output will be set on the out
     * message body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorDefinition<Type>> unmarshal() {
        return new DataFormatClause<>(this, DataFormatClause.Operation.Unmarshal);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormatType the dataformat
     * @return                the builder
     */
    public Type unmarshal(DataFormatDefinition dataFormatType) {
        return unmarshal(dataFormatType, false);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormatType the dataformat
     * @param  allowNullBody  {@code true} if {@code null} is allowed as value of a body to unmarshall, {@code false}
     *                        otherwise
     * @return                the builder
     */
    public Type unmarshal(DataFormatDefinition dataFormatType, boolean allowNullBody) {
        addOutput(new UnmarshalDefinition(dataFormatType).allowNullBody(allowNullBody));
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormat the dataformat
     * @return            the builder
     */
    public Type unmarshal(DataFormat dataFormat) {
        return unmarshal(dataFormat, false);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormat    the dataformat
     * @param  allowNullBody {@code true} if {@code null} is allowed as value of a body to unmarshall, {@code false}
     *                       otherwise
     * @return               the builder
     */
    public Type unmarshal(DataFormat dataFormat, boolean allowNullBody) {
        return unmarshal(new DataFormatDefinition(dataFormat), allowNullBody);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} reference in the {@link org.apache.camel.spi.Registry} and sets the output on the out message
     * body.
     *
     * @param  dataTypeRef reference to a {@link DataFormat} to lookup in the registry
     * @return             the builder
     */
    public Type unmarshal(String dataTypeRef) {
        return unmarshal(dataTypeRef, false);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Unmarshals the in body using the specified
     * {@link DataFormat} reference in the {@link org.apache.camel.spi.Registry} and sets the output on the out message
     * body.
     *
     * @param  dataTypeRef reference to a {@link DataFormat} to lookup in the registry
     * @return             the builder
     */
    public Type unmarshal(String dataTypeRef, boolean allowNullBody) {
        return unmarshal(new CustomDataFormat(dataTypeRef), allowNullBody);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Marshals the in body using a
     * {@link DataFormat} expression to define the format of the output which will be added to the out body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorDefinition<Type>> marshal() {
        return new DataFormatClause<>(this, DataFormatClause.Operation.Marshal);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Marshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormatType the dataformat
     * @return                the builder
     */
    public Type marshal(DataFormatDefinition dataFormatType) {
        addOutput(new MarshalDefinition(dataFormatType));
        return asType();
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Marshals the in body using the specified
     * {@link DataFormat} and sets the output on the out message body.
     *
     * @param  dataFormat the dataformat
     * @return            the builder
     */
    public Type marshal(DataFormat dataFormat) {
        return marshal(new DataFormatDefinition(dataFormat));
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a> Marshals the in body the specified
     * {@link DataFormat} reference in the {@link org.apache.camel.spi.Registry} and sets the output on the out message
     * body.
     *
     * @param  dataTypeRef reference to a {@link DataFormat} to lookup in the registry
     * @return             the builder
     */
    public Type marshal(String dataTypeRef) {
        addOutput(new MarshalDefinition(new CustomDataFormat(dataTypeRef)));
        return asType();
    }

    /**
     * Sets whether or not to inherit the configured error handler. <br/>
     * The default value is <tt>true</tt>.
     * <p/>
     * You can use this to disable using the inherited error handler for a given DSL such as a load balancer where you
     * want to use a custom error handler strategy.
     *
     * @param  inheritErrorHandler whether to not to inherit the error handler for this node
     * @return                     the builder
     */
    public Type inheritErrorHandler(boolean inheritErrorHandler) {
        // set on last output
        int size = getOutputs().size();
        if (size == 0) {
            // if no outputs then configure this DSL
            setInheritErrorHandler(inheritErrorHandler);
        } else {
            // configure on last output as its the intended
            ProcessorDefinition<?> output = getOutputs().get(size - 1);
            if (output != null) {
                output.setInheritErrorHandler(inheritErrorHandler);
            }
        }
        return asType();
    }

    @SuppressWarnings("unchecked")
    Type asType() {
        return (Type) this;
    }

    /**
     * This defines the route as resumable, which allows the route to work with the endpoints and components to manage
     * the state of consumers and resume upon restart.
     *
     * @return the builder
     */
    public ResumableDefinition resumable() {
        ResumableDefinition answer = new ResumableDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * This defines the route as resumable, which allows the route to work with the endpoints and components to manage
     * the state of consumers and resume upon restart.
     *
     * @param  resumeStrategy the resume strategy
     * @return                the builder
     */
    public Type resumable(ResumeStrategy resumeStrategy) {
        ResumableDefinition answer = new ResumableDefinition();
        answer.setResumeStrategy(resumeStrategy);
        addOutput(answer);
        return asType();
    }

    /**
     * This defines the route as resumable, which allows the route to work with the endpoints and components to manage
     * the state of consumers and resume upon restart.
     *
     * @param  resumeStrategy the resume strategy
     * @return                the builder
     */
    public Type resumable(String resumeStrategy) {
        ResumableDefinition answer = new ResumableDefinition();
        answer.setResumeStrategy(resumeStrategy);
        addOutput(answer);
        return asType();
    }

    /**
     * This enables pausable consumers, which allows the consumer to pause work until a certain condition allows it to
     * resume operation
     *
     * @return the builder
     */
    public PausableDefinition pausable() {
        PausableDefinition answer = new PausableDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * This enables pausable consumers, which allows the consumer to pause work until a certain condition allows it to
     * resume operation
     *
     * @param  consumerListener the consumer listener to use for consumer events
     * @return                  the builder
     */
    public Type pausable(ConsumerListener consumerListener, java.util.function.Predicate<?> untilCheck) {
        PausableDefinition answer = new PausableDefinition();
        answer.setConsumerListener(consumerListener);
        answer.setUntilCheck(untilCheck);
        addOutput(answer);
        return asType();
    }

    /**
     * This enables pausable consumers, which allows the consumer to pause work until a certain condition allows it to
     * resume operation
     *
     * @param  consumerListenerRef the resume strategy
     * @return                     the builder
     */
    public Type pausable(String consumerListenerRef, java.util.function.Predicate<?> untilCheck) {
        PausableDefinition answer = new PausableDefinition();
        answer.setConsumerListener(consumerListenerRef);
        answer.setUntilCheck(untilCheck);
        addOutput(answer);
        return asType();
    }

    /**
     * This enables pausable consumers, which allows the consumer to pause work until a certain condition allows it to
     * resume operation
     *
     * @param  consumerListenerRef the resume strategy
     * @return                     the builder
     */
    public Type pausable(String consumerListenerRef, String untilCheck) {
        PausableDefinition answer = new PausableDefinition();
        answer.setConsumerListener(consumerListenerRef);
        answer.setUntilCheck(untilCheck);
        addOutput(answer);
        return asType();
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public ProcessorDefinition<?> getParent() {
        return parent;
    }

    public void setParent(ProcessorDefinition<?> parent) {
        this.parent = parent;
    }

    public RouteConfigurationDefinition getRouteConfiguration() {
        return routeConfiguration;
    }

    public void setRouteConfiguration(RouteConfigurationDefinition routeConfiguration) {
        this.routeConfiguration = routeConfiguration;
    }

    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    public void addInterceptStrategy(InterceptStrategy strategy) {
        this.interceptStrategies.add(strategy);
    }

    public Boolean isInheritErrorHandler() {
        return inheritErrorHandler;
    }

    public void setInheritErrorHandler(Boolean inheritErrorHandler) {
        this.inheritErrorHandler = inheritErrorHandler;
    }

    public String getDisabled() {
        return disabled;
    }

    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    /**
     * Returns a label to describe this node such as the expression if some kind of expression node
     */
    @Override
    public String getLabel() {
        return "";
    }
}
