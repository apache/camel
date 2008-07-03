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
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.NoErrorHandlerBuilder;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.idempotent.MessageIdRepository;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.ErrorHandlerWrappingStrategy;
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
    public static final String DEFAULT_TRACE_CATEGORY = "org.apache.camel.TRACE";
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
     * Sends the exchange to the given endpoint URI
     */
    public Type to(String uri) {
        addOutput(new ToType(uri));
        return (Type) this;
    }

    /**
     * Sends the exchange to the given endpoint
     */
    public Type to(Endpoint endpoint) {
        addOutput(new ToType(endpoint));
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints using the
     * {@link MulticastProcessor} pattern
     */
    public Type to(String... uris) {
        for (String uri : uris) {
            addOutput(new ToType(uri));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints using the
     * {@link MulticastProcessor} pattern
     */
    public Type to(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToType(endpoint));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoint using the
     * {@link MulticastProcessor} pattern
     */
    public Type to(Collection<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToType(endpoint));
        }
        return (Type) this;
    }

    /**
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     */
    public MulticastType multicast() {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        return answer;
    }

    /**
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the multicast type
     */
    public MulticastType multicast(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @return the multicast type
     */
    public MulticastType multicast(AggregationStrategy aggregationStrategy) {
        MulticastType answer = new MulticastType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     */
    public Type pipeline(String... uris) {
        // TODO pipeline v mulicast
        return to(uris);
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     */
    public Type pipeline(Endpoint... endpoints) {
        // TODO pipeline v mulicast
        return to(endpoints);
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     */
    public Type pipeline(Collection<Endpoint> endpoints) {
        // TODO pipeline v mulicast
        return to(endpoints);
    }

    /**
     * Ends the current block
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
    public ProcessorType<Type> thread(ThreadPoolExecutor executor) {
        ThreadType answer = new ThreadType(executor);
        addOutput(answer);
        return this;
    }

    /**
     * Creates an {@link IdempotentConsumer} to avoid duplicate messages
     */
    public IdempotentConsumerType idempotentConsumer(Expression messageIdExpression,
            MessageIdRepository messageIdRepository) {
        IdempotentConsumerType answer = new IdempotentConsumerType(messageIdExpression, messageIdRepository);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates an {@link IdempotentConsumer} to avoid duplicate messages
     *
     * @return the builder used to create the expression
     */
    public ExpressionClause<IdempotentConsumerType> idempotentConsumer(MessageIdRepository messageIdRepository) {
        IdempotentConsumerType answer = new IdempotentConsumerType();
        answer.setMessageIdRepository(messageIdRepository);
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates a predicate expression which only if it is true then the
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
     * Creates a predicate which is applied and only if it is true then the
     * exchange is forwarded to the destination
     *
     * @return the builder for a predicate
     */
    public FilterType filter(Predicate predicate) {
        FilterType filter = new FilterType(predicate);
        addOutput(filter);
        return filter;
    }

    public FilterType filter(ExpressionType expression) {
        FilterType filter = getNodeFactory().createFilter();
        filter.setExpression(expression);
        addOutput(filter);
        return filter;
    }

    public FilterType filter(String language, String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    public LoadBalanceType loadBalance() {
        LoadBalanceType answer = new LoadBalanceType();
        addOutput(answer);
        return answer;
    }


    /**
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
     * Creates a dynamic <a
     * href="http://activemq.apache.org/camel/recipient-list.html">Recipient
     * List</a> pattern.
     *
     * @param receipients is the builder of the expression used in the
     *                    {@link RecipientList} to decide the destinations
     */
    public Type recipientList(Expression receipients) {
        RecipientListType answer = new RecipientListType(receipients);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a dynamic <a
     * href="http://activemq.apache.org/camel/recipient-list.html">Recipient
     * List</a> pattern.
     *
     * @return the expression clause for the expression used in the
     *                    {@link RecipientList} to decide the destinations
     */
    public ExpressionClause<ProcessorType<Type>> recipientList() {
        RecipientListType answer = new RecipientListType();
        addOutput(answer);
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        answer.setExpression(clause);
        return clause;
    }

    /**
     * Creates a <a
     * href="http://activemq.apache.org/camel/routing-slip.html">Routing
     * Slip</a> pattern.
     *
     * @param header is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     * class will look in for the list of URIs to route the message to.
     * @param uriDelimiter is the delimiter that will be used to split up
     * the list of URIs in the routing slip.
     */
    public Type routingSlip(String header, String uriDelimiter) {
        RoutingSlipType answer = new RoutingSlipType(header, uriDelimiter);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a <a
     * href="http://activemq.apache.org/camel/routing-slip.html">Routing
     * Slip</a> pattern.
     *
     * @param header is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     * class will look in for the list of URIs to route the message to. The list of URIs
     * will be split based on the default delimiter
     * {@link RoutingSlipType#DEFAULT_DELIMITER}.
     */
    public Type routingSlip(String header) {
        RoutingSlipType answer = new RoutingSlipType(header);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a <a
     * href="http://activemq.apache.org/camel/routing-slip.html">Routing
     * Slip</a> pattern with the default header {@link RoutingSlipType#ROUTING_SLIP_HEADER}.
     * The list of URIs in the header will be split based on the default delimiter
     * {@link RoutingSlipType#DEFAULT_DELIMITER}.
     */
    public Type routingSlip() {
        RoutingSlipType answer = new RoutingSlipType();
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @param receipients the expression on which to split
     * @return the builder
     */
    public SplitterType splitter(Expression receipients) {
        SplitterType answer = new SplitterType(receipients);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> splitter() {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * Answer from the splitter is produced using given {@link AggregationStrategy}
     * @param partsExpression the expression on which to split
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @return the builder
     */
    public SplitterType splitter(Expression partsExpression, AggregationStrategy aggregationStrategy) {
        SplitterType answer = new SplitterType(partsExpression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * Answer from the splitter is produced using given {@link AggregationStrategy}
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> splitter(AggregationStrategy aggregationStrategy) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @param receipients the expression on which to split
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public SplitterType splitter(Expression receipients, boolean parallelProcessing) {
        SplitterType answer = new SplitterType(receipients);
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> splitter(boolean parallelProcessing) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setParallelProcessing(parallelProcessing);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * Answer from the splitter is produced using given {@link AggregationStrategy}
     * @param partsExpression the expression on which to split
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public SplitterType splitter(Expression partsExpression,
            AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        SplitterType answer = new SplitterType(partsExpression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/splitter.html">Splitter</a>
     * pattern where an expression is evaluated to iterate through each of the
     * parts of a message and then each part is then send to some endpoint.
     * Answer from the splitter is produced using given {@link AggregationStrategy}
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the expression clause for the expression on which to split
     */
    public ExpressionClause<SplitterType> splitter(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        SplitterType answer = new SplitterType();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return ExpressionClause.createAndSetExpression(answer);
    }


    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
     * pattern where a list of expressions are evaluated to be able to compare
     * the message exchanges to reorder them. e.g. you may wish to sort by some
     * headers
     *
     * @return the expression clause for the expressions on which to compare messages in order
     */
    public ExpressionClause<ResequencerType> resequencer() {
        ResequencerType answer = new ResequencerType();
        addOutput(answer);
        ExpressionClause<ResequencerType> clause = new ExpressionClause<ResequencerType>(answer);
        answer.expression(clause);
        return clause;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
     * pattern where an expression is evaluated to be able to compare the
     * message exchanges to reorder them. e.g. you may wish to sort by some
     * header
     *
     * @param expression the expression on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequencer(Expression<Exchange> expression) {
        return resequencer(Collections.<Expression>singletonList(expression));
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
     * pattern where a list of expressions are evaluated to be able to compare
     * the message exchanges to reorder them. e.g. you may wish to sort by some
     * headers
     *
     * @param expressions the expressions on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequencer(List<Expression> expressions) {
        ResequencerType answer = new ResequencerType(expressions);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a>
     * pattern where a list of expressions are evaluated to be able to compare
     * the message exchanges to reorder them. e.g. you may wish to sort by some
     * headers
     *
     * @param expressions the expressions on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequencer(Expression... expressions) {
        List<Expression> list = new ArrayList<Expression>();
        list.addAll(Arrays.asList(expressions));
        return resequencer(list);
    }

    /**
     * Creates an <a
     * href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a>
     * pattern where a batch of messages are processed (up to a maximum amount
     * or until some timeout is reached) and messages for the same correlation
     * key are combined together using some kind of {@link AggregationStrategy}
     * (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000
     * messages/second and you may want to throttle it right down so that multiple
     * messages for the same stock are combined (or just the latest message is used
     * and older prices are discarded). Another idea is to combine line item messages
     * together into a single invoice message.
     */
    public ExpressionClause<AggregatorType> aggregator() {
        AggregatorType answer = new AggregatorType();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates an <a
     * href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a>
     * pattern where a batch of messages are processed (up to a maximum amount
     * or until some timeout is reached) and messages for the same correlation
     * key are combined together using some kind of {@link AggregationStrategy}
     * (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000
     * messages/second and you may want to throttle it right down so that multiple
     * messages for the same stock are combined (or just the latest message is used
     * and older prices are discarded). Another idea is to combine line item messages
     * together into a single invoice message.
     *
     * @param aggregationStrategy the strategy used for the aggregation
     */
    public ExpressionClause<AggregatorType> aggregator(AggregationStrategy aggregationStrategy) {
        AggregatorType answer = new AggregatorType();
        answer.setAggregationStrategy(aggregationStrategy);
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates an <a
     * href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a>
     * pattern using a custom aggregation collection implementation.
     *
     * @param aggregationCollection the collection used to perform the aggregation
     */
    public ExpressionClause<AggregatorType> aggregator(AggregationCollection aggregationCollection) {
        AggregatorType answer = new AggregatorType();
        answer.setAggregationCollection(aggregationCollection);
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates an <a
     * href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a>
     * pattern where a batch of messages are processed (up to a maximum amount
     * or until some timeout is reached) and messages for the same correlation
     * key are combined together using some kind of {@link AggregationStrategy}
     * (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000
     * messages/second and you may want to throttle it right down so that multiple
     * messages for the same stock are combined (or just the latest message is used
     * and older prices are discarded). Another idea is to combine line item messages
     * together into a single invoice message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     */
    public AggregatorType aggregator(Expression correlationExpression) {
        AggregatorType answer = new AggregatorType(correlationExpression);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates an <a
     * href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a>
     * pattern where a batch of messages are processed (up to a maximum amount
     * or until some timeout is reached) and messages for the same correlation
     * key are combined together using some kind of {@link AggregationStrategy}
     * (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000
     * messages/second and you may want to throttle it right down so that multiple
     * messages for the same stock are combined (or just the latest message is used
     * and older prices are discarded). Another idea is to combine line item messages
     * together into a single invoice message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     */
    public AggregatorType aggregator(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        AggregatorType answer = new AggregatorType(correlationExpression, aggregationStrategy);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will
     * be dispatched on
     *
     * @param processAtExpression an expression to calculate the time at which
     *                            the messages should be processed
     * @return the builder
     */
    public DelayerType delayer(Expression<Exchange> processAtExpression) {
        return delayer(processAtExpression, 0L);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will
     * be dispatched on
     *
     * @param processAtExpression an expression to calculate the time at which
     *                            the messages should be processed
     * @param delay               the delay in milliseconds which is added to the
     *                            processAtExpression to determine the time the message
     *                            should be processed
     * @return the builder
     */
    public DelayerType delayer(Expression<Exchange> processAtExpression, long delay) {
        DelayerType answer = new DelayerType(processAtExpression, delay);
        addOutput(answer);
        return answer;
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will
     * be dispatched on
     * @return the expression clause to create the expression
     */
    public ExpressionClause<DelayerType> delayer() {
        DelayerType answer = new DelayerType();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where a fixed amount of milliseconds are used to delay processing of a
     * message exchange
     *
     * @param delay the default delay in milliseconds
     * @return the builder
     */
    public DelayerType delayer(long delay) {
        return delayer(null, delay);
    }

    /**
     * Creates the <a
     * href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will
     * be dispatched on
     *
     * @return the builder
     */
    public ThrottlerType throttler(long maximumRequestCount) {
        ThrottlerType answer = new ThrottlerType(maximumRequestCount);
        addOutput(answer);
        return answer;
    }


    public Type throwFault(Throwable fault) {
        ThrowFaultType answer = new ThrowFaultType();
        answer.setFault(fault);
        addOutput(answer);
        return (Type) this;
    }

    public Type throwFault(String message) {
        return throwFault(new CamelException(message));
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     */
    public Type interceptor(String ref) {
        InterceptorRef interceptor = new InterceptorRef(ref);
        intercept(interceptor);
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     */
    public Type intercept(DelegateProcessor interceptor) {
        intercept(new InterceptorRef(interceptor));
        //lastInterceptor = interceptor;
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     */
    public InterceptType intercept() {
        InterceptType answer = new InterceptType();
        addOutput(answer);
        return answer;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     */
    public void intercept(InterceptorType interceptor) {
        addOutput(interceptor);
        pushBlock(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor
     */
    public void addInterceptor(InterceptorType interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor
     */
    public void addInterceptor(DelegateProcessor interceptor) {
        addInterceptor(new InterceptorRef(interceptor));
    }

    protected void pushBlock(Block block) {
        blocks.add(block);
    }

    protected Block popBlock() {
        return blocks.isEmpty() ? null : blocks.removeLast();
    }

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

    public ExceptionType exception(Class exceptionType) {
        ExceptionType answer = new ExceptionType(exceptionType);
        addOutput(answer);
        return answer;
    }

    /**
     * Apply an interceptor route if the predicate is true
     */
    public ChoiceType intercept(Predicate predicate) {
        InterceptType answer = new InterceptType();
        addOutput(answer);
        return answer.when(predicate);
    }

    public Type interceptors(String... refs) {
        for (String ref : refs) {
            interceptor(ref);
        }
        return (Type) this;
    }

    /**
     * Trace logs the exchange before it goes to the next processing step using
     * the {@link #DEFAULT_TRACE_CATEGORY} logging category.
     */
    public Type trace() {
        return trace(DEFAULT_TRACE_CATEGORY);
    }

    /**
     * Trace logs the exchange before it goes to the next processing step using
     * the specified logging category.
     *
     * @param category the logging category trace messages will sent to.
     */
    public Type trace(String category) {
        final Log log = LogFactory.getLog(category);
        return intercept(new DelegateProcessor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                log.trace(exchange);
                processNext(exchange);
            }
        });
    }

    public PolicyRef policies() {
        PolicyRef answer = new PolicyRef();
        addOutput(answer);
        return answer;
    }

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
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for
     *                            all child routes
     * @return the current builder with the error handler configured
     */
    public Type errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return (Type) this;
    }

    /**
     * Configures whether or not the error handler is inherited by every
     * processing node (or just the top most one)
     *
     * @param condition the flag as to whether error handlers should be
     *                  inherited or not
     * @return the current builder
     */
    public Type inheritErrorHandler(boolean condition) {
        setInheritErrorHandlerFlag(condition);
        return (Type) this;
    }

    // Transformers
    // -------------------------------------------------------------------------

    /**
     * Adds the custom processor to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     */
    public Type process(Processor processor) {
        ProcessorRef answer = new ProcessorRef(processor);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds the custom processor reference to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     */
    public Type processRef(String ref) {
        ProcessorRef answer = new ProcessorRef();
        answer.setRef(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean which is invoked which could be a final destination, or could
     * be a transformation in a pipeline
     */
    public Type bean(Object bean) {
        BeanRef answer = new BeanRef();
        answer.setBean(bean);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean and method which is invoked which could be a final
     * destination, or could be a transformation in a pipeline
     */
    public Type bean(Object bean, String method) {
        BeanRef answer = new BeanRef();
        answer.setBean(bean);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean by type which is invoked which could be a final destination, or could
     * be a transformation in a pipeline
     */
    public Type bean(Class beanType) {
        BeanRef answer = new BeanRef();
        answer.setBeanType(beanType);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean type and method which is invoked which could be a final
     * destination, or could be a transformation in a pipeline
     */
    public Type bean(Class beanType, String method) {
        BeanRef answer = new BeanRef();
        answer.setBeanType(beanType);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean which is invoked which could be a final destination, or could
     * be a transformation in a pipeline
     */
    public Type beanRef(String ref) {
        BeanRef answer = new BeanRef(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a bean and method which is invoked which could be a final
     * destination, or could be a transformation in a pipeline
     */
    public Type beanRef(String ref, String method) {
        BeanRef answer = new BeanRef(ref, method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the body on the IN message
     */
    public ExpressionClause<ProcessorType<Type>> setBody() {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetBodyType answer = new SetBodyType(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the body on the IN message
     */
    public Type setBody(Expression expression) {
        SetBodyType answer = new SetBodyType(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the body on the OUT message
     *
     * @deprecated Please use {@link #transform(Expression)} instead. Will be removed in Camel 2.0.
     */
    @Deprecated
    public Type setOutBody(Expression expression) {
        return transform(expression);
    }

    /**
     * Adds a processor which sets the body on the OUT message
     *
     * @deprecated Please use {@link #transform()} instead. Will be removed in Camel 2.0.
     */
    @Deprecated
    public ExpressionClause<ProcessorType<Type>> setOutBody() {
        return transform();
    }

    /**
     * Adds a processor which sets the body on the OUT message
     */
    public Type transform(Expression expression) {
        TransformType answer = new TransformType(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the body on the OUT message
     */
    public ExpressionClause<ProcessorType<Type>> transform() {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        TransformType answer = new TransformType(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the body on the FAULT message
     */
    public Type setFaultBody(Expression expression) {
        return process(ProcessorBuilder.setFaultBody(expression));
    }

    /**
     * Adds a processor which sets the header on the IN message
     */
    public ExpressionClause<ProcessorType<Type>> setHeader(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        SetHeaderType answer = new SetHeaderType(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which sets the header on the IN message
     */
    public Type setHeader(String name, Expression expression) {
        SetHeaderType answer = new SetHeaderType(name, expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the header on the IN message to the given value
     */
    public Type setHeader(String name, String value) {
        SetHeaderType answer = new SetHeaderType(name, value);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     */
    public ExpressionClause<ProcessorType<Type>> setOutHeader(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        process(ProcessorBuilder.setOutHeader(name, clause));
        return clause;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     */
    public Type setOutHeader(String name, Expression expression) {
        return process(ProcessorBuilder.setOutHeader(name, expression));
    }

    /**
     * Adds a processor which sets the header on the OUT message
     */
    public Type setOutHeader(String name, String value) {
        return (Type) setOutHeader(name).constant(value);
    }

    /**
     * Adds a processor which sets the header on the FAULT message
     */
    public Type setFaultHeader(String name, Expression expression) {
        return process(ProcessorBuilder.setFaultHeader(name, expression));
    }

    /**
     * Adds a processor which sets the exchange property
     */
    public Type setProperty(String name, Expression expression) {
        return process(ProcessorBuilder.setProperty(name, expression));
    }


    /**
     * Adds a processor which sets the exchange property
     */
    public ExpressionClause<ProcessorType<Type>> setProperty(String name) {
        ExpressionClause<ProcessorType<Type>> clause = new ExpressionClause<ProcessorType<Type>>((Type) this);
        process(ProcessorBuilder.setProperty(name, clause));
        return clause;
    }

    /**
     * Adds a processor which removes the header on the IN message
     */
    public Type removeHeader(String name) {
        return process(ProcessorBuilder.removeHeader(name));
    }

    /**
     * Adds a processor which removes the header on the OUT message
     */
    public Type removeOutHeader(String name) {
        return process(ProcessorBuilder.removeOutHeader(name));
    }

    /**
     * Adds a processor which removes the header on the FAULT message
     */
    public Type removeFaultHeader(String name) {
        return process(ProcessorBuilder.removeFaultHeader(name));
    }

    /**
     * Adds a processor which removes the exchange property
     */
    public Type removeProperty(String name) {
        return process(ProcessorBuilder.removeProperty(name));
    }

    /**
     * Converts the IN message body to the specified type
     */
    public Type convertBodyTo(Class type) {
        addOutput(new ConvertBodyType(type));
        return (Type) this;
    }

    /**
     * Converts the OUT message body to the specified type
     *
     * @deprecated Please use {@link #convertBodyTo(Class)} instead. Will be removed in Camel 2.0.
     */
    @Deprecated
    public Type convertOutBodyTo(Class type) {
        return process(new ConvertBodyProcessor(type));
    }

    /**
     * Converts the FAULT message body to the specified type
     *
     * @deprecated Please use {@link #convertBodyTo(Class)} instead. Will be removed in Camel 2.0.
     */
    @Deprecated
    public Type convertFaultBodyTo(Class type) {
        return process(new ConvertBodyProcessor(type));
    }

    // DataFormat support
    // -------------------------------------------------------------------------

    /**
     * Unmarshals the in body using a {@link DataFormat} expression to define
     * the format of the input message and the output will be set on the out message body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorType<Type>> unmarshal() {
        return new DataFormatClause<ProcessorType<Type>>(this, DataFormatClause.Operation.Unmarshal);
    }

    /**
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @return this object
     */
    public Type unmarshal(DataFormatType dataFormatType) {
        addOutput(new UnmarshalType(dataFormatType));
        return (Type) this;
    }

    /**
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @return this object
     */
    public Type unmarshal(DataFormat dataFormat) {
        return unmarshal(new DataFormatType(dataFormat));
    }

    /**
     * Unmarshals the in body using the specified {@link DataFormat}
     * reference in the {@link Registry} and sets the output on the out message body.
     *
     * @return this object
     */
    public Type unmarshal(String dataTypeRef) {
        addOutput(new UnmarshalType(dataTypeRef));
        return (Type) this;
    }

    /**
     * Marshals the in body using a {@link DataFormat} expression to define
     * the format of the output which will be added to the out body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorType<Type>> marshal() {
        return new DataFormatClause<ProcessorType<Type>>(this, DataFormatClause.Operation.Marshal);
    }

    /**
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @return this object
     */
    public Type marshal(DataFormatType dataFormatType) {
        addOutput(new MarshalType(dataFormatType));
        return (Type) this;
    }

    /**
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @return this object
     */
    public Type marshal(DataFormat dataFormat) {
        return marshal(new DataFormatType(dataFormat));
    }

    /**
     * Marshals the in body the specified {@link DataFormat}
     * reference in the {@link Registry} and sets the output on the out message body.
     *
     * @return this object
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
     * @param routeContext
     * @param target       the processor which can be wrapped
     * @return the original processor or a new wrapped interceptor
     */
    protected Processor wrapProcessorInInterceptors(RouteContext routeContext, Processor target) throws Exception {
        // The target is required.
        if (target == null) {
            throw new RuntimeCamelException("target not provided.");
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
            throw new RuntimeCamelException("target not provided.");
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
    protected Processor createCompositeProcessor(List<Processor> list) {
        // return new MulticastProcessor(list);
        return new Pipeline(list);
    }

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorType<?>> outputs)
        throws Exception {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorType output : outputs) {
            Processor processor = output.createProcessor(routeContext);
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
                processor = createCompositeProcessor(list);
            }
        }
        return processor;
    }

    public void clearOutput() {
        getOutputs().clear();
        blocks.clear();
    }
}
