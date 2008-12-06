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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.util.CollectionHelper;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.ErrorHandlerWrappingStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for processor types that most XML types extend.
 *
 * @version $Revision$
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class ProcessorType<Type extends ProcessorType> extends OptionalIdentifiedType<Type> implements Block {
    private static final transient Log LOG = LogFactory.getLog(ProcessorType.class);
    private ErrorHandlerBuilder errorHandlerBuilder;
    private Boolean inheritErrorHandlerFlag;
    private NodeFactory nodeFactory;
    private LinkedList<Block> blocks = new LinkedList<Block>();
    private ProcessorType<? extends ProcessorType> parent;
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    private String errorHandlerRef;

    // else to use an optional attribute in JAXB2
    public abstract List<ProcessorType<?>> getOutputs();


    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new UnsupportedOperationException("Not implemented yet for class: " + getClass().getName());
    }

    public Processor createOutputsProcessor(RouteContext routeContext) throws Exception {
        Collection<ProcessorType<?>> outputs = getOutputs();
        return createOutputsProcessor(routeContext, outputs);
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Processor processor = makeProcessor(routeContext);
        if (!routeContext.isRouteAdded()) {
            routeContext.addEventDrivenProcessor(processor);
        }
    }

    /**
     * Wraps the child processor in whatever necessary interceptors and error
     * handlers
     */
    public Processor wrapProcessor(RouteContext routeContext, Processor processor) throws Exception {
        processor = wrapProcessorInInterceptors(routeContext, processor);
        return wrapInErrorHandler(routeContext, processor);
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sends the exchange to the given endpoint
     *
     * @param uri  the endpoint to send to
     * @return the builder
     */
    public Type to(String uri) {
        addOutput(new ToType(uri));
        return (Type) this;
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint  the endpoint to send to
     * @return the builder
     */
    public Type to(Endpoint endpoint) {
        addOutput(new ToType(endpoint));
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param uris  list of endpoints to send to
     * @return the builder
     */
    public Type to(String... uris) {
        for (String uri : uris) {
            addOutput(new ToType(uri));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type to(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToType(endpoint));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type to(Collection<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToType(endpoint));
        }
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @return the builder
     */
    public MulticastType multicast() {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public MulticastType multicast(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @return the builder
     */
    public MulticastType multicast(AggregationStrategy aggregationStrategy) {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/pipes-nd-filters.html">Pipes and Filters EIP:</a>
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     *
     * @param uris  list of endpoints
     * @return the builder
     */
    public Type pipeline(String... uris) {
        return to(uris);
    }

    /**
     * <a href="http://activemq.apache.org/camel/pipes-nd-filters.html">Pipes and Filters EIP:</a>
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     *
     * @param endpoints  list of endpoints
     * @return the builder
     */
    public Type pipeline(Endpoint... endpoints) {
        return to(endpoints);
    }

    /**
     * <a href="http://activemq.apache.org/camel/pipes-nd-filters.html">Pipes and Filters EIP:</a>
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     *
     * @param endpoints  list of endpoints
     * @return the builder
     */
    public Type pipeline(Collection<Endpoint> endpoints) {
        return to(endpoints);
    }

    /**
     * Ends the current block
     *
     * @return the builder
     */
    public ProcessorType<? extends ProcessorType> end() {
        if (blocks.isEmpty()) {
            if (parent == null) {
                throw new IllegalArgumentException("Root node with no active block");
            }
            return parent;
        }
        popBlock();
        return this;
    }

    /**
     * Causes subsequent processors to be called asynchronously
     *
     * @param coreSize the number of threads that will be used to process
     *                 messages in subsequent processors.
     * @return a ThreadType builder that can be used to further configure the
     *         the thread pool.
     */
    public ThreadType thread(int coreSize) {
        ThreadType answer = new ThreadType(coreSize);
        addOutput(answer);
        return answer;
    }

    /**
     * Causes subsequent processors to be called asynchronously
     *
     * @param executor the executor that will be used to process
     *                 messages in subsequent processors.
     * @return a ThreadType builder that can be used to further configure the
     *         the thread pool.
     */
    public ProcessorType<Type> thread(Executor executor) {
        ThreadType answer = new ThreadType(executor);
        addOutput(answer);
        return this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/idempotent-consumer.html">Idempotent consumer EIP:</a>
     * Creates an {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer}
     * to avoid duplicate messages
     *
     * @param messageIdExpression  expression to test of duplicate messages
     * @param idempotentRepository  the repository to use for duplicate chedck
     * @return the builder
     */
    public IdempotentConsumerType idempotentConsumer(Expression messageIdExpression,
            IdempotentRepository idempotentRepository) {
        IdempotentConsumerType answer = new IdempotentConsumerType(messageIdExpression, idempotentRepository);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/idempotent-consumer.html">Idempotent consumer EIP:</a>
     * Creates an {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer}
     * to avoid duplicate messages
     *
     * @param idempotentRepository the repository to use for duplicate chedck
     * @return the builder used to create the expression
     */
    public ExpressionClause<IdempotentConsumerType> idempotentConsumer(IdempotentRepository idempotentRepository) {
        IdempotentConsumerType answer = new IdempotentConsumerType();
        answer.setMessageIdRepository(idempotentRepository);
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @return the clause used to create the filter expression
     */
    public ExpressionClause<FilterType> filter() {
        FilterType filter = new FilterType();
        addOutput(filter);
        return ExpressionClause.createAndSetExpression(filter);
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate which is applied and only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param predicate  predicate to use
     * @return the builder 
     */
    public FilterType filter(Predicate predicate) {
        FilterType filter = new FilterType(predicate);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param expression  the predicate expression to use
     * @return the builder
     */
    public FilterType filter(ExpressionType expression) {
        FilterType filter = getNodeFactory().createFilter();
        filter.setExpression(expression);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate language expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param language     language for expression
     * @param expression   the expression
     * @return the builder
     */
    public FilterType filter(String language, String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    /**
     * <a href="http://activemq.apache.org/camel/load-balancer.html">Load Balancer EIP:</a>
     * Creates a loadbalance
     *
     * @return  the builder
     */
    public LoadBalanceType loadBalance() {
        LoadBalanceType answer = new LoadBalanceType();
        addOutput(answer);
        return answer;
    }


    /**
     * <a href="http://activemq.apache.org/camel/content-based-router.html">Content Based Router EIP:</a>
     * Creates a choice of one or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    public ChoiceType choice() {
        ChoiceType answer = new ChoiceType();
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a try/catch block
     *
     * @return the builder for a tryBlock expression
     */
    public TryType tryBlock() {
        TryType answer = new TryType();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @param recipients expression to decide the destinations
     * @return the builder
     */
    public Type recipientList(Expression recipients) {
        RecipientListType answer = new RecipientListType(recipients);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    public ExpressionClause<ProcessorType<Type>> recipientList() {
        RecipientListType answer = new RecipientListType();
        addOutput(answer);
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        answer.setExpression(clause);
        return clause;
    }

    /**
     * <a href="http://activemq.apache.org/camel/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @param uriDelimiter  is the delimiter that will be used to split up
     *                      the list of URIs in the routing slip.
     * @return the buiider
     */
    public Type routingSlip(String header, String uriDelimiter) {
        RoutingSlipType answer = new RoutingSlipType(header, uriDelimiter);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipType#DEFAULT_DELIMITER}
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @return the builder
     */
    public Type routingSlip(String header) {
        RoutingSlipType answer = new RoutingSlipType(header);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p>
     * The header will use the default header {@link RoutingSlipType#DEFAULT_DELIMITER}
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipType#DEFAULT_DELIMITER}
     *
     * @return the builder
     * @deprecated will be removed in Camel 2.0
     */
    public Type routingSlip() {
        RoutingSlipType answer = new RoutingSlipType();
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @param expression  the expression on which to split the message
     * @return the builder
     */
    public SplitterType split(Expression expression) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @return the expression clause builder for the expression on which to split
     */
    public ExpressionClause<SplitterType> split() {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression  the expression on which to split
     * @param aggregationStrategy  the strategy used to aggregate responses for every part
     * @return the builder
     */
    public SplitterType split(Expression expression, AggregationStrategy aggregationStrategy) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param aggregationStrategy  the strategy used to aggregate responses for every part
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> split(AggregationStrategy aggregationStrategy) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression the expression on which to split
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public SplitterType split(Expression expression, boolean parallelProcessing) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression the expression on which to split
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @param executor override the default {@link Executor}
     * @return the builder
     */
    public SplitterType split(Expression expression, boolean parallelProcessing,
                                 Executor executor) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        answer.setExecutor(executor);
        return answer;
    }    
    
    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> split(boolean parallelProcessing) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     /**
      * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
      * Creates a splitter allowing you split a message into a number of pieces and process them individually.
      * <p>
      * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @param executor override the default {@link Executor}
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> split(boolean parallelProcessing, Executor executor) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        answer.setExecutor(executor);
        return ExpressionClause.createAndSetExpression(answer);
    }    
    
    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression the expression on which to split
     * @param aggregationStrategy the strategy used to aggregate responses for every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public SplitterType split(Expression expression, AggregationStrategy aggregationStrategy,
                                 boolean parallelProcessing) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression the expression on which to split
     * @param aggregationStrategy the strategy used to aggregate responses for every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @param executor override the default {@link Executor}
     * @return the builder
     */
    public SplitterType split(Expression expression, AggregationStrategy aggregationStrategy,
                                 boolean parallelProcessing, Executor executor) {
        SplitterType answer = new SplitterType(expression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        answer.setExecutor(executor);
        return answer;
    }    
    
    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> split(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://activemq.apache.org/camel/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @param executor override the default {@link Executor}
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> split(AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                                                   Executor executor) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        answer.setExecutor(executor);
        return ExpressionClause.createAndSetExpression(answer);
    }   
    
    /**
     * <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @return the expression clause for the expressions on which to compare messages in order
     */
    public ExpressionClause<ResequencerType> resequence() {
        ResequencerType answer = new ResequencerType();
        addOutput(answer);
        ExpressionClause<ResequencerType> clause = new ExpressionClause<ResequencerType>(answer);
        answer.expression(clause);
        return clause;
    }

    /**
     * <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @param expression the expression on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequence(Expression expression) {
        return resequence(Collections.<Expression>singletonList(expression));
    }

    /**
     * <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @param expressions the list of expressions on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequence(List<Expression> expressions) {
        ResequencerType answer = new ResequencerType(expressions);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer EIP:</a>
     * Creates a splitter allowing you to reorganise messages based on some comparator.
     *
     * @param expressions the list of expressions on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequencer(Expression... expressions) {
        List<Expression> list = new ArrayList<Expression>();
        list.addAll(Arrays.asList(expressions));
        return resequence(list);
    }

    /**
     * <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @return the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregatorType> aggregate() {
        if (CollectionHelper.filterList(getOutputs(), ExceptionType.class).isEmpty()) {
            LOG.warn("Aggregator must be the only output added to the route: " + this);
        }
        AggregatorType answer = new AggregatorType();
        addOutput(answer);
        return answer.createAndSetExpression();
    }

    /**
     * <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param aggregationStrategy the strategy used for the aggregation
     * @return the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregatorType> aggregate(AggregationStrategy aggregationStrategy) {
        if (CollectionHelper.filterList(getOutputs(), ExceptionType.class).isEmpty()) {
            LOG.warn("Aggregator must be the only output added to the route: " + this);
        }
        AggregatorType answer = new AggregatorType();
        answer.setAggregationStrategy(aggregationStrategy);
        addOutput(answer);
        return answer.createAndSetExpression();
    }

    /**
     * <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param aggregationCollection the collection used to perform the aggregation
     * @return the builder
     */
    public AggregatorType aggregate(AggregationCollection aggregationCollection) {
        if (CollectionHelper.filterList(getOutputs(), ExceptionType.class).isEmpty()) {
            LOG.warn("Aggregator must be the only output added to the route: " + this);
        }
        AggregatorType answer = new AggregatorType();
        answer.setAggregationCollection(aggregationCollection);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     * @return the builder
     */
    public AggregatorType aggregate(Expression correlationExpression) {
        if (CollectionHelper.filterList(getOutputs(), ExceptionType.class).isEmpty()) {
            LOG.warn("Aggregator must be the only output added to the route: " + this);
        }
        AggregatorType answer = new AggregatorType(correlationExpression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     * @param aggregationStrategy the strategy used for the aggregation
     * @return the builder
     */
    public AggregatorType aggregate(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        if (CollectionHelper.filterList(getOutputs(), ExceptionType.class).isEmpty()) {
            LOG.warn("Aggregator must be the only output added to the route: " + this);
        }
        AggregatorType answer = new AggregatorType(correlationExpression, aggregationStrategy);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param processAtExpression  an expression to calculate the time at which the messages should be processed,
     *                             should be convertable to long as time in millis
     * @return the builder
     */
    public DelayerType delay(Expression processAtExpression) {
        return delay(processAtExpression, 0L);
    }

    /**
     * <a href="http://activemq.apache.org/camel/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param processAtExpression  an expression to calculate the time at which the messages should be processed,
     *                             should be convertable to long as time in millis
     * @param delay                the delay in milliseconds which is added to the processAtExpression
     * @return the builder
     */
    public DelayerType delay(Expression processAtExpression, long delay) {
        DelayerType answer = new DelayerType(processAtExpression, delay);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @return the expression clause to create the expression
     */
    public ExpressionClause<DelayerType> delay() {
        DelayerType answer = new DelayerType();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://activemq.apache.org/camel/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param delay  the default delay in millis
     * @return the builder
     */
    public DelayerType delay(long delay) {
        return delay(null, delay);
    }

    /**
     * <a href="http://activemq.apache.org/camel/throttler.html">Throttler EIP:</a>
     * Creates a throttler allowing you to ensure that a specific endpoint does not get overloaded,
     * or that we don't exceed an agreed SLA with some external service.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10
     * will default ensure at most 10 messages per second. 
     *
     * @param maximumRequestCount  the maximum messages 
     * @return the builder
     */
    public ThrottlerType throttle(long maximumRequestCount) {
        ThrottlerType answer = new ThrottlerType(maximumRequestCount);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://activemq.apache.org/camel/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @return the clause used to create the loop expression
     */
    public ExpressionClause<LoopType> loop() {
        LoopType loop = new LoopType();
        addOutput(loop);
        return ExpressionClause.createAndSetExpression(loop);
    }

    /**
     * <a href="http://activemq.apache.org/camel/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @param expression the loop expression
     * @return the builder
     */
    public LoopType loop(Expression expression) {
        LoopType loop = getNodeFactory().createLoop();
        loop.setExpression(expression);
        addOutput(loop);
        return loop;
    }

    /**
     * <a href="http://activemq.apache.org/camel/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @param count  the number of times
     * @return the builder
     */
    public LoopType loop(int count) {
        LoopType loop = getNodeFactory().createLoop();
        loop.setExpression(new ConstantExpression(Integer.toString(count)));
        addOutput(loop);
        return loop;
    }

    /**
     * Creates a fault message based on the given throwable.
     *
     * @param fault   the fault
     * @return the builder
     */
    public Type throwFault(Throwable fault) {
        ThrowFaultType answer = new ThrowFaultType();
        answer.setFault(fault);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a fault message based on the given message.
     *
     * @param message  the fault message
     * @return the builder
     */
    public Type throwFault(String message) {
        return throwFault(new CamelException(message));
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param ref  a reference in the registry to lookup the interceptor that must be of type {@link DelegateProcessor}
     * @return the builder
     */
    public Type interceptor(String ref) {
        InterceptorRef interceptor = new InterceptorRef(ref);
        intercept(interceptor);
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param refs  a list of reference in the registry to lookup the interceptor that must
     *              be of type {@link DelegateProcessor}
     * @return the builder
     */
    public Type interceptors(String... refs) {
        for (String ref : refs) {
            interceptor(ref);
        }
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param interceptor  the interceptor
     * @return the builder
     */
    public Type intercept(DelegateProcessor interceptor) {
        intercept(new InterceptorRef(interceptor));
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @return the intercept builder to configure
     */
    public InterceptType intercept() {
        InterceptType answer = new InterceptType();
        addOutput(answer);
        return answer;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param  interceptor  the interceptor
     */
    public void intercept(InterceptorType interceptor) {
        addOutput(interceptor);
        pushBlock(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor  the interceptor
     */
    public void addInterceptor(InterceptorType interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor  the interceptor
     */
    public void addInterceptor(DelegateProcessor interceptor) {
        addInterceptor(new InterceptorRef(interceptor));
    }

    /**
     * Pushes the given block on the stack as current block
     * @param block  the block
     */
    public void pushBlock(Block block) {
        blocks.add(block);
    }

    /**
     * Pops the block off the stack as current block
     * @return the block
     */
    public Block popBlock() {
        return blocks.isEmpty() ? null : blocks.removeLast();
    }

    /**
     * Procceeds the given intercepted route.
     * <p/>
     * Proceed is used in conjunction with intercept where calling proceed will route the message through the
     * original route path from the point of interception. This can be used to implement the
     * <a href="http://www.enterpriseintegrationpatterns.com/Detour.html">detour</a> pattern.
     *
     * @return the builder
     * @see ProcessorType#proceed()
     */
    public Type proceed() {
        ProceedType proceed = null;
        ProcessorType currentProcessor = this;

        if (currentProcessor instanceof InterceptType) {
            proceed = ((InterceptType) currentProcessor).getProceed();
            LOG.info("proceed() is the implied and hence not needed for an intercept()");
        }
        if (proceed == null) {
            for (ProcessorType node = parent; node != null; node = node.getParent()) {
                if (node instanceof InterceptType) {
                    InterceptType intercept = (InterceptType)node;
                    proceed = intercept.getProceed();
                    break;
                }
            }

            if (proceed == null) {
                throw new IllegalArgumentException("Cannot use proceed() without being within an intercept() block");
            }

        }

        addOutput(proceed);
        return (Type) this;
    }

    /**
     * Stops the given intercepted route.
     * <p/>
     * As opposed to {@link #proceed()} calling stop will stop the message route and <b>not</b> continue
     * from the interepted origin.
     *
     * @return the builder
     * @see #proceed()
     */
    public Type stop() {
        ProcessorType currentProcessor = this;

        if (currentProcessor instanceof InterceptType) {
            ((InterceptType) currentProcessor).stopIntercept();
        } else {
            ProcessorType node;
            for (node = parent; node != null; node = node.getParent()) {
                if (node instanceof InterceptType) {
                    ((InterceptType) node).stopIntercept();
                    break;
                }
            }
            if (node == null) {
                throw new IllegalArgumentException("Cannot use stop() without being within an intercept() block");
            }
        }

        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/exception-clause.html">Exception clause</a>
     * for cathing certain exceptions and handling them.
     *
     * @param exceptionType  the exception to catch
     * @return the exception builder to configure
     */
    public ExceptionType onException(Class exceptionType) {
        ExceptionType answer = new ExceptionType(exceptionType);
        addOutput(answer);
        return answer;
    }

    /**
     * Apply an interceptor route if the predicate is true.
     *
     * @param predicate the predicate to test
     * @return  the choice builder to configure
     */
    public ChoiceType intercept(Predicate predicate) {
        InterceptType answer = new InterceptType();
        addOutput(answer);
        return answer.when(predicate);
    }

    /**
     * Creates a policy.
     * <p/>
     * Policy can be used for transactional policies.
     *
     * @return the policy builder to configure
     */
    public PolicyRef policies() {
        PolicyRef answer = new PolicyRef();
        addOutput(answer);
        return answer;
    }

    /**
     * Apply a {@link Policy}.
     * <p/>
     * Policy can be used for transactional policies.
     *
     * @param policy  the policy to apply
     * @return the policy builder to configure
     */
    public PolicyRef policy(Policy policy) {
        PolicyRef answer = new PolicyRef(policy);
        addOutput(answer);
        return answer;
    }

    /**
     * Forces handling of faults as exceptions
     *
     * @return the current builder with the fault handler configured
     */
    public Type handleFault() {
        intercept(new HandleFaultType());
        return (Type) this;
    }

    /**
     * Installs the given <a href="http://activemq.apache.org/camel/error-handler.html">error handler</a> builder.
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public Type errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return (Type) this;
    }

    /**
     * Configures whether or not the <a href="http://activemq.apache.org/camel/error-handler.html">error handler</a>
     * is inherited by every processing node (or just the top most one)
     *
     * @param condition the flag as to whether error handlers should be inherited or not
     * @return the current builder
     */
    public Type inheritErrorHandler(boolean condition) {
        setInheritErrorHandlerFlag(condition);
        return (Type) this;
    }

    // Transformers
    // -------------------------------------------------------------------------

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds the custom processor to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     *
     * @param processor  the custom {@link Processor}
     * @return the builder
     */
    public Type process(Processor processor) {
        ProcessorRef answer = new ProcessorRef(processor);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds the custom processor reference to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     *
     * @param ref   reference to a {@link Processor} to lookup in the registry
     * @return the builder
     */
    public Type processRef(String ref) {
        ProcessorRef answer = new ProcessorRef();
        answer.setRef(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param bean  the bean to invoke
     * @return the builder
     */
    public Type bean(Object bean) {
        BeanRef answer = new BeanRef();
        answer.setBean(bean);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param bean  the bean to invoke
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    public Type bean(Object bean, String method) {
        BeanRef answer = new BeanRef();
        answer.setBean(bean);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType  the bean class, Camel will instantiate an object at runtime
     * @return the builder
     */
    public Type bean(Class beanType) {
        BeanRef answer = new BeanRef();
        answer.setBeanType(beanType);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType  the bean class, Camel will instantiate an object at runtime
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    public Type bean(Class beanType, String method) {
        BeanRef answer = new BeanRef();
        answer.setBeanType(beanType);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param ref  reference to a bean to lookup in the registry
     * @return the builder
     */
    public Type beanRef(String ref) {
        BeanRef answer = new BeanRef(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param ref  reference to a bean to lookup in the registry
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    public Type beanRef(String ref, String method) {
        BeanRef answer = new BeanRef(ref, method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the IN message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorType<Type>> setBody() {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetBodyType answer = new SetBodyType(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the IN message
     *
     * @param expression   the expression used to set the body
     * @return the builder
     */
    public Type setBody(Expression expression) {
        SetBodyType answer = new SetBodyType(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the OUT message
     *
     * @param expression   the expression used to set the body
     * @return the builder
     */
    public Type transform(Expression expression) {
        TransformType answer = new TransformType(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the OUT message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorType<Type>> transform() {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        TransformType answer = new TransformType(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the body on the FAULT message
     *
     * @param expression   the expression used to set the body
     * @return the builder
     */
    public Type setFaultBody(Expression expression) {
        return process(ProcessorBuilder.setFaultBody(expression));
    }

    /**
     * Adds a processor which sets the header on the IN message
     *
     * @param name  the header name
     * @return a expression builder clause to set the header
     */
    public ExpressionClause<ProcessorType<Type>> setHeader(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetHeaderType answer = new SetHeaderType(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the header on the IN message
     *
     * @param name  the header name
     * @param expression  the expression used to set the header
     * @return the builder
     */
    public Type setHeader(String name, Expression expression) {
        SetHeaderType answer = new SetHeaderType(name, expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     *
     * @param name  the header name
     * @return a expression builder clause to set the header
     */
    public ExpressionClause<ProcessorType<Type>> setOutHeader(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetOutHeaderType answer = new SetOutHeaderType(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     *
     * @param name  the header name
     * @param expression  the expression used to set the header
     * @return the builder
     */
    public Type setOutHeader(String name, Expression expression) {
        SetOutHeaderType answer = new SetOutHeaderType(name, expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the header on the FAULT message
     *
     * @param name  the header name
     * @param expression  the expression used to set the header
     * @return the builder
     */
    public Type setFaultHeader(String name, Expression expression) {
        return process(ProcessorBuilder.setFaultHeader(name, expression));
    }

    /**
     * Adds a processor which sets the exchange property
     *
     * @param name  the property name
     * @param expression  the expression used to set the property
     * @return the builder
     */
    public Type setProperty(String name, Expression expression) {
        SetPropertyType answer = new SetPropertyType(name, expression);
        addOutput(answer);
        return (Type) this;
    }


    /**
     * Adds a processor which sets the exchange property
     *
     * @param name  the property name
     * @return a expression builder clause to set the property
     */
    public ExpressionClause<ProcessorType<Type>> setProperty(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetPropertyType answer = new SetPropertyType(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which removes the header on the IN message
     *
     * @param name  the header name
     * @return the builder
     */
    public Type removeHeader(String name) {
        RemoveHeaderType answer = new RemoveHeaderType(name);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which removes the header on the FAULT message
     *
     * @param name  the header name
     * @return the builder
     */
    public Type removeFaultHeader(String name) {
        return process(ProcessorBuilder.removeFaultHeader(name));
    }

    /**
     * Adds a processor which removes the exchange property
     *
     * @param name  the property name
     * @return the builder
     */
    public Type removeProperty(String name) {
        RemovePropertyType answer = new RemovePropertyType(name);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Converts the IN message body to the specified type
     *
     * @param type the type to convert to
     * @return the builder
     */
    public Type convertBodyTo(Class type) {
        addOutput(new ConvertBodyType(type));
        return (Type) this;
    }
    
    /**
     * Converts the IN message body to the specified class type
     *
     * @param typeString the type to convert to as a fully qualified classname
     * @return the builder
     */
    public Type convertBodyTo(String typeString) {
        addOutput(new ConvertBodyType(typeString));
        return (Type) this;
    }

    // DataFormat support
    // -------------------------------------------------------------------------

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Unmarshals the in body using a {@link DataFormat} expression to define
     * the format of the input message and the output will be set on the out message body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorType<Type>> unmarshal() {
        return new DataFormatClause<ProcessorType<Type>>(this, DataFormatClause.Operation.Unmarshal);
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormatType  the dataformat
     * @return the builder
     */
    public Type unmarshal(DataFormatType dataFormatType) {
        addOutput(new UnmarshalType(dataFormatType));
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormat  the dataformat
     * @return the builder
     */
    public Type unmarshal(DataFormat dataFormat) {
        return unmarshal(new DataFormatType(dataFormat));
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * reference in the {@link org.apache.camel.spi.Registry} and sets
     * the output on the out message body.
     *
     * @param dataTypeRef  reference to a {@link DataFormat} to lookup in the registry
     * @return the builder
     */
    public Type unmarshal(String dataTypeRef) {
        addOutput(new UnmarshalType(dataTypeRef));
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Marshals the in body using a {@link DataFormat} expression to define
     * the format of the output which will be added to the out body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorType<Type>> marshal() {
        return new DataFormatClause<ProcessorType<Type>>(this, DataFormatClause.Operation.Marshal);
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormatType  the dataformat
     * @return the builder
     */
    public Type marshal(DataFormatType dataFormatType) {
        addOutput(new MarshalType(dataFormatType));
        return (Type) this;
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormat  the dataformat
     * @return the builder
     */
    public Type marshal(DataFormat dataFormat) {
        return marshal(new DataFormatType(dataFormat));
    }

    /**
     * <a href="http://activemq.apache.org/camel/data-format.html">DataFormat:</a>
     * Marshals the in body the specified {@link DataFormat}
     * reference in the {@link org.apache.camel.spi.Registry} and sets
     * the output on the out message body.
     *
     * @param dataTypeRef  reference to a {@link DataFormat} to lookup in the registry
     * @return the builder
     */
    public Type marshal(String dataTypeRef) {
        addOutput(new MarshalType(dataTypeRef));
        return (Type) this;
    }

    // Properties
    // -------------------------------------------------------------------------
    @XmlTransient
    public ProcessorType<? extends ProcessorType> getParent() {
        return parent;
    }

    public void setParent(ProcessorType<? extends ProcessorType> parent) {
        this.parent = parent;
    }

    @XmlTransient
    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        if (errorHandlerBuilder == null) {
            errorHandlerBuilder = createErrorHandlerBuilder();
        }
        return errorHandlerBuilder;
    }

    /**
     * Sets the error handler to use with processors created by this builder
     */
    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    /**
     * Sets the error handler if one is not already set
     */
    protected void setErrorHandlerBuilderIfNull(ErrorHandlerBuilder errorHandlerBuilder) {
        if (this.errorHandlerBuilder == null) {
            setErrorHandlerBuilder(errorHandlerBuilder);
        }
    }

    public String getErrorHandlerRef() {
        return errorHandlerRef;
    }

    /**
     * Sets the bean ref name of the error handler builder to use on this route
     */
    @XmlAttribute(required = false)
    public void setErrorHandlerRef(String errorHandlerRef) {
        this.errorHandlerRef = errorHandlerRef;
        setErrorHandlerBuilder(new ErrorHandlerBuilderRef(errorHandlerRef));
    }

    @XmlTransient
    public boolean isInheritErrorHandler() {
        return isInheritErrorHandler(getInheritErrorHandlerFlag());
    }

    /**
     * Lets default the inherit value to be true if there is none specified
     */
    public static boolean isInheritErrorHandler(Boolean value) {
        return value == null || value.booleanValue();
    }

    @XmlAttribute(name = "inheritErrorHandler", required = false)
    public Boolean getInheritErrorHandlerFlag() {
        return inheritErrorHandlerFlag;
    }

    public void setInheritErrorHandlerFlag(Boolean inheritErrorHandlerFlag) {
        this.inheritErrorHandlerFlag = inheritErrorHandlerFlag;
    }

    @XmlTransient
    public NodeFactory getNodeFactory() {
        if (nodeFactory == null) {
            nodeFactory = new NodeFactory();
        }
        return nodeFactory;
    }

    public void setNodeFactory(NodeFactory nodeFactory) {
        this.nodeFactory = nodeFactory;
    }

    /**
     * Returns a label to describe this node such as the expression if some kind of expression node
     */
    public String getLabel() {
        return "";
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Creates the processor and wraps it in any necessary interceptors and
     * error handlers
     */
    protected Processor makeProcessor(RouteContext routeContext) throws Exception {
        Processor processor = createProcessor(routeContext);
        return wrapProcessor(routeContext, processor);
    }

    /**
     * A strategy method which allows derived classes to wrap the child
     * processor in some kind of interceptor
     *
     * @param routeContext the route context
     * @param target       the processor which can be wrapped
     * @return the original processor or a new wrapped interceptor
     */
    protected Processor wrapProcessorInInterceptors(RouteContext routeContext, Processor target) throws Exception {
        // The target is required.
        if (target == null) {
            throw new IllegalArgumentException("target not provided on node: " + this);
        }

        List<InterceptStrategy> strategies = new ArrayList<InterceptStrategy>();
        CamelContext camelContext = routeContext.getCamelContext();
        if (camelContext instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) camelContext;
            strategies.addAll(defaultCamelContext.getInterceptStrategies());
        }
        strategies.addAll(routeContext.getInterceptStrategies());
        for (InterceptStrategy strategy : strategies) {
            if (strategy != null) {
                target = strategy.wrapProcessorInInterceptors(this, target);
            }
        }

        List<InterceptorType> list = routeContext.getRoute().getInterceptors();
        if (interceptors != null) {
            list.addAll(interceptors);
        }
        // lets reverse the list so we apply the inner interceptors first
        Collections.reverse(list);
        Set<Processor> interceptors = new HashSet<Processor>();
        interceptors.add(target);
        for (InterceptorType interceptorType : list) {
            DelegateProcessor interceptor = interceptorType.createInterceptor(routeContext);
            if (!interceptors.contains(interceptor)) {
                interceptors.add(interceptor);
                if (interceptor.getProcessor() != null) {
                    LOG.warn("Interceptor " + interceptor + " currently wraps target "
                            + interceptor.getProcessor()
                            + " is attempting to change target " + target
                            + " new wrapping has been denied.");
                } else {
                    interceptor.setProcessor(target);
                    target = interceptor;
                }
            }
        }
        return target;
    }

    /**
     * A strategy method to allow newly created processors to be wrapped in an
     * error handler.
     */
    protected Processor wrapInErrorHandler(RouteContext routeContext, Processor target) throws Exception {
        // The target is required.
        if (target == null) {
            throw new IllegalArgumentException("target not provided on node: " + this);
        }

        ErrorHandlerWrappingStrategy strategy = routeContext.getErrorHandlerWrappingStrategy();

        if (strategy != null) {
            return strategy.wrapProcessorInErrorHandler(routeContext, this, target);
        }

        return getErrorHandlerBuilder().createErrorHandler(routeContext, target);
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        if (errorHandlerRef != null) {
            return new ErrorHandlerBuilderRef(errorHandlerRef);
        }
        if (isInheritErrorHandler()) {
            return new DeadLetterChannelBuilder();
        } else {
            return new NoErrorHandlerBuilder();
        }
    }

    protected void configureChild(ProcessorType output) {
        output.setNodeFactory(getNodeFactory());
    }

    public void addOutput(ProcessorType processorType) {
        processorType.setParent(this);
        configureChild(processorType);
        if (blocks.isEmpty()) {
            getOutputs().add(processorType);
        } else {
            Block block = blocks.getLast();
            block.addOutput(processorType);
        }
    }

    /**
     * Creates a new instance of some kind of composite processor which defaults
     * to using a {@link Pipeline} but derived classes could change the
     * behaviour
     */
    protected Processor createCompositeProcessor(RouteContext routeContext, List<Processor> list) {
        return new Pipeline(list);
    }

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorType<?>> outputs)
        throws Exception {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorType output : outputs) {
            Processor processor = output.createProcessor(routeContext);
            // if the ProceedType create processor is null we keep on going
            if (output instanceof ProceedType && processor == null) {
                continue;
            }
            processor = output.wrapProcessorInInterceptors(routeContext, processor);

            ProcessorType currentProcessor = this;
            if (!(currentProcessor instanceof ExceptionType || currentProcessor instanceof TryType)) {
                processor = output.wrapInErrorHandler(routeContext, processor);
            }

            list.add(processor);
        }
        Processor processor = null;
        if (!list.isEmpty()) {
            if (list.size() == 1) {
                processor = list.get(0);
            } else {
                processor = createCompositeProcessor(routeContext, list);
            }
        }
        return processor;
    }

    public void clearOutput() {
        getOutputs().clear();
        blocks.clear();
    }
}
