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
import java.util.Comparator;
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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.DefaultErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.model.dataformat.DataFormatDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
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
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import static org.apache.camel.builder.Builder.body;

/**
 * Base class for processor types that most XML types extend.
 *
 * @version $Revision$
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class ProcessorDefinition<Type extends ProcessorDefinition> extends OptionalIdentifiedType<Type> implements Block {
    private static final transient Log LOG = LogFactory.getLog(ProcessorDefinition.class);
    private ErrorHandlerBuilder errorHandlerBuilder;
    private NodeFactory nodeFactory;
    private LinkedList<Block> blocks = new LinkedList<Block>();
    private ProcessorDefinition parent;
    private List<AbstractInterceptorDefinition> interceptors = new ArrayList<AbstractInterceptorDefinition>();
    private String errorHandlerRef;

    // else to use an optional attribute in JAXB2
    public abstract List<ProcessorDefinition> getOutputs();


    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new UnsupportedOperationException("Not implemented yet for class: " + getClass().getName());
    }

    public Processor createOutputsProcessor(RouteContext routeContext) throws Exception {
        Collection<ProcessorDefinition> outputs = getOutputs();
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
    @SuppressWarnings("unchecked")
    public Type to(String uri) {
        addOutput(new ToDefinition(uri));
        return (Type) this;
    }   
    
    /**
     * Sends the exchange to the given endpoint
     *
     * @param uri  the String formatted endpoint uri to send to
     * @param args arguments for the string formatting of the uri
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type toF(String uri, Object... args) {
        addOutput(new ToDefinition(String.format(uri, args)));
        return (Type) this;
    }


    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint  the endpoint to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(Endpoint endpoint) {
        addOutput(new ToDefinition(endpoint));
        return (Type) this;
    }
    
    /**
     * Sends the exchange with certain exchange pattern to the given endpoint
     *
     * @param pattern the pattern to use for the message exchange
     * @param uri  the endpoint to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(ExchangePattern pattern, String uri) {
        addOutput(new ToDefinition(uri, pattern));
        return (Type) this;
    }   
    

    /**
     * Sends the exchange with certain exchange pattern to the given endpoint
     *
     * @param pattern the pattern to use for the message exchange
     * @param endpoint  the endpoint to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(ExchangePattern pattern, Endpoint endpoint) {
        addOutput(new ToDefinition(endpoint, pattern));
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param uris  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(String... uris) {
        for (String uri : uris) {
            addOutput(new ToDefinition(uri));
        }
        return (Type) this;
    }


    /**
     * Sends the exchange to a list of endpoints
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(Iterable<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint));
        }
        return (Type) this;
    }
    
    
    /**
     * Sends the exchange to a list of endpoints
     *
     * @param pattern the pattern to use for the message exchanges
     * @param uris  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(ExchangePattern pattern, String... uris) {
        for (String uri : uris) {
            addOutput(new ToDefinition(uri, pattern));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param pattern the pattern to use for the message exchanges
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(ExchangePattern pattern, Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint, pattern));
        }
        return (Type) this;
    }

    /**
     * Sends the exchange to a list of endpoints
     *
     * @param pattern the pattern to use for the message exchanges
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type to(ExchangePattern pattern, Iterable<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            addOutput(new ToDefinition(endpoint, pattern));
        }
        return (Type) this;
    }


    /**
     * <a href="http://camel.apache.org/exchange-pattern.html">ExchangePattern:</a>
     * set the ExchangePattern {@link ExchangePattern} into the exchange
     *
     * @param exchangePattern  instance of {@link ExchangePattern}
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type setExchangePattern(ExchangePattern exchangePattern) {
        addOutput(new SetExchangePatternDefinition(exchangePattern));
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/exchange-pattern.html">ExchangePattern:</a>
     * set the exchange's ExchangePattern {@link ExchangePattern} to be InOnly
     *
     *
     * @return the builder
     */
    public Type inOnly() {
        return setExchangePattern(ExchangePattern.InOnly);
    }

    /**
     * Sends the message to the given endpoint using an
     * <a href="http://camel.apache.org/event-message.html">Event Message</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOnly exchange pattern</a>
     *
     * @param uri The endpoint uri which is used for sending the exchange
     * @return the builder
     */
    public Type inOnly(String uri) {
        return to(ExchangePattern.InOnly, uri);
    }

    /**
     * Sends the message to the given endpoint using an
     * <a href="http://camel.apache.org/event-message.html">Event Message</a> or 
     * <a href="http://camel.apache.org/exchange-pattern.html">InOnly exchange pattern</a>
     *
     * @param endpoint The endpoint which is used for sending the exchange
     * @return the builder
     */
    public Type inOnly(Endpoint endpoint) {
        return to(ExchangePattern.InOnly, endpoint);
    }


    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/event-message.html">Event Message</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOnly exchange pattern</a>
     *
     * @param uris  list of endpoints to send to
     * @return the builder
     */
    public Type inOnly(String... uris) {
        return to(ExchangePattern.InOnly, uris);
    }


    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/event-message.html">Event Message</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOnly exchange pattern</a>
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type inOnly(Endpoint... endpoints) {
        return to(ExchangePattern.InOnly, endpoints);
    }

    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/event-message.html">Event Message</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOnly exchange pattern</a>
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type inOnly(Iterable<Endpoint> endpoints) {
        return to(ExchangePattern.InOnly, endpoints);
    }


    /**
     * <a href="http://camel.apache.org/exchange-pattern.html">ExchangePattern:</a>
     * set the exchange's ExchangePattern {@link ExchangePattern} to be InOut
     *
     *
     * @return the builder
     */
    public Type inOut() {
        return setExchangePattern(ExchangePattern.InOut);
    }

    /**
     * Sends the message to the given endpoint using an
     * <a href="http://camel.apache.org/request-reply.html">Request Reply</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOut exchange pattern</a>
     *
     * @param uri The endpoint uri which is used for sending the exchange
     * @return the builder
     */
    public Type inOut(String uri) {
        return to(ExchangePattern.InOut, uri);
    }


    /**
     * Sends the message to the given endpoint using an
     * <a href="http://camel.apache.org/request-reply.html">Request Reply</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOut exchange pattern</a>
     *
     * @param endpoint The endpoint which is used for sending the exchange
     * @return the builder
     */
    public Type inOut(Endpoint endpoint) {
        return to(ExchangePattern.InOut, endpoint);
    }

    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/request-reply.html">Request Reply</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOut exchange pattern</a>
     *
     * @param uris  list of endpoints to send to
     * @return the builder
     */
    public Type inOut(String... uris) {
        return to(ExchangePattern.InOut, uris);
    }


    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/request-reply.html">Request Reply</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOut exchange pattern</a>
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type inOut(Endpoint... endpoints) {
        return to(ExchangePattern.InOut, endpoints);
    }

    /**
     * Sends the message to the given endpoints using an
     * <a href="http://camel.apache.org/request-reply.html">Request Reply</a> or
     * <a href="http://camel.apache.org/exchange-pattern.html">InOut exchange pattern</a>
     *
     * @param endpoints  list of endpoints to send to
     * @return the builder
     */
    public Type inOut(Iterable<Endpoint> endpoints) {
        return to(ExchangePattern.InOut, endpoints);
    }


    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @return the builder
     */
    public MulticastDefinition multicast() {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @param parallelProcessing if is <tt>true</tt> camel will fork thread to call the endpoint producer
     * @return the builder
     */
    public MulticastDefinition multicast(AggregationStrategy aggregationStrategy, boolean parallelProcessing) {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        answer.setParallelProcessing(parallelProcessing);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/multicast.html">Multicast EIP:</a>
     * Multicasts messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other.
     *
     * @param aggregationStrategy the strategy used to aggregate responses for
     *          every part
     * @return the builder
     */
    public MulticastDefinition multicast(AggregationStrategy aggregationStrategy) {
        MulticastDefinition answer = new MulticastDefinition();
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/pipes-nd-filters.html">Pipes and Filters EIP:</a>
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
     * <a href="http://camel.apache.org/pipes-nd-filters.html">Pipes and Filters EIP:</a>
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
     * <a href="http://camel.apache.org/pipes-nd-filters.html">Pipes and Filters EIP:</a>
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
    @SuppressWarnings("unchecked")
    public ProcessorDefinition<? extends ProcessorDefinition> end() {
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
    public ThreadDefinition thread(int coreSize) {
        ThreadDefinition answer = new ThreadDefinition(coreSize);
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
    public ProcessorDefinition<Type> thread(Executor executor) {
        ThreadDefinition answer = new ThreadDefinition(executor);
        addOutput(answer);
        return this;
    }
    
    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a>
     * Creates an {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer}
     * to avoid duplicate messages
     *      
     * @return the builder
     */
    public IdempotentConsumerDefinition idempotentConsumer() {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a>
     * Creates an {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer}
     * to avoid duplicate messages
     *
     * @param messageIdExpression  expression to test of duplicate messages
     * @param idempotentRepository  the repository to use for duplicate chedck
     * @return the builder
     */
    public IdempotentConsumerDefinition idempotentConsumer(Expression messageIdExpression,
            IdempotentRepository idempotentRepository) {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition(messageIdExpression, idempotentRepository);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/idempotent-consumer.html">Idempotent consumer EIP:</a>
     * Creates an {@link org.apache.camel.processor.idempotent.IdempotentConsumer IdempotentConsumer}
     * to avoid duplicate messages
     *
     * @param idempotentRepository the repository to use for duplicate chedck
     * @return the builder used to create the expression
     */
    public ExpressionClause<IdempotentConsumerDefinition> idempotentConsumer(IdempotentRepository idempotentRepository) {
        IdempotentConsumerDefinition answer = new IdempotentConsumerDefinition();
        answer.setMessageIdRepository(idempotentRepository);
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @return the clause used to create the filter expression
     */
    public ExpressionClause<FilterDefinition> filter() {
        FilterDefinition filter = new FilterDefinition();
        addOutput(filter);
        return ExpressionClause.createAndSetExpression(filter);
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate which is applied and only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param predicate  predicate to use
     * @return the builder 
     */
    public FilterDefinition filter(Predicate predicate) {
        FilterDefinition filter = new FilterDefinition(predicate);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param expression  the predicate expression to use
     * @return the builder
     */
    public FilterDefinition filter(ExpressionDefinition expression) {
        FilterDefinition filter = getNodeFactory().createFilter();
        filter.setExpression(expression);
        addOutput(filter);
        return filter;
    }

    /**
     * <a href="http://camel.apache.org/message-filter.html">Message Filter EIP:</a>
     * Creates a predicate language expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination
     *
     * @param language     language for expression
     * @param expression   the expression
     * @return the builder
     */
    public FilterDefinition filter(String language, String expression) {
        return filter(new LanguageExpression(language, expression));
    }

    /**
     * <a href="http://camel.apache.org/load-balancer.html">Load Balancer EIP:</a>
     * Creates a loadbalance
     *
     * @return  the builder
     */
    public LoadBalanceDefinition loadBalance() {
        LoadBalanceDefinition answer = new LoadBalanceDefinition();
        addOutput(answer);
        return answer;
    }


    /**
     * <a href="http://camel.apache.org/content-based-router.html">Content Based Router EIP:</a>
     * Creates a choice of one or more predicates with an otherwise clause
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
    public TryDefinition tryBlock() {
        TryDefinition answer = new TryDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @param recipients expression to decide the destinations
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type recipientList(Expression recipients) {
        RecipientListDefinition answer = new RecipientListDefinition(recipients);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    public ExpressionClause<ProcessorDefinition<Type>> recipientList() {
        RecipientListDefinition answer = new RecipientListDefinition();
        addOutput(answer);
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>(this);
        answer.setExpression(clause);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @param uriDelimiter  is the delimiter that will be used to split up
     *                      the list of URIs in the routing slip.
     * @return the buiider
     */
    @SuppressWarnings("unchecked")
    public Type routingSlip(String header, String uriDelimiter) {
        RoutingSlipDefinition answer = new RoutingSlipDefinition(header, uriDelimiter);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type routingSlip(String header) {
        RoutingSlipDefinition answer = new RoutingSlipDefinition(header);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @return the expression clause builder for the expression on which to split
     */
    public ExpressionClause<SplitDefinition> split() {
        SplitDefinition answer = new SplitDefinition();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * This splitter responds with the latest message returned from destination
     * endpoint.
     *
     * @param expression  the expression on which to split the message
     * @return the builder
     */
    public SplitDefinition split(Expression expression) {
        SplitDefinition answer = new SplitDefinition(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/splitter.html">Splitter EIP:</a>
     * Creates a splitter allowing you split a message into a number of pieces and process them individually.
     * <p>
     * The splitter responds with the answer produced by the given {@link AggregationStrategy}.
     *
     * @param expression  the expression on which to split
     * @param aggregationStrategy  the strategy used to aggregate responses for every part
     * @return the builder
     */
    public SplitDefinition split(Expression expression, AggregationStrategy aggregationStrategy) {
        SplitDefinition answer = new SplitDefinition(expression);
        addOutput(answer);
        answer.setAggregationStrategy(aggregationStrategy);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @return the expression clause for the expressions on which to compare messages in order
     */
    public ExpressionClause<ResequenceDefinition> resequence() {
        ResequenceDefinition answer = new ResequenceDefinition();
        addOutput(answer);
        ExpressionClause<ResequenceDefinition> clause = new ExpressionClause<ResequenceDefinition>(answer);
        answer.expression(clause);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @param expression the expression on which to compare messages in order
     * @return the builder
     */
    public ResequenceDefinition resequence(Expression expression) {
        return resequence(Collections.<Expression>singletonList(expression));
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a>
     * Creates a resequencer allowing you to reorganize messages based on some comparator.
     *
     * @param expressions the list of expressions on which to compare messages in order
     * @return the builder
     */
    public ResequenceDefinition resequence(List<Expression> expressions) {
        ResequenceDefinition answer = new ResequenceDefinition(expressions);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/resequencer.html">Resequencer EIP:</a>
     * Creates a splitter allowing you to reorganise messages based on some comparator.
     *
     * @param expressions the list of expressions on which to compare messages in order
     * @return the builder
     */
    public ResequenceDefinition resequencer(Expression... expressions) {
        List<Expression> list = new ArrayList<Expression>();
        list.addAll(Arrays.asList(expressions));
        return resequence(list);
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @return the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregateDefinition> aggregate() {
        AggregateDefinition answer = new AggregateDefinition();
        addOutput(answer);
        return answer.createAndSetExpression();
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param aggregationStrategy the strategy used for the aggregation
     * @return the expression clause to be used as builder to configure the correlation expression
     */
    public ExpressionClause<AggregateDefinition> aggregate(AggregationStrategy aggregationStrategy) {
        AggregateDefinition answer = new AggregateDefinition();
        answer.setAggregationStrategy(aggregationStrategy);
        addOutput(answer);
        return answer.createAndSetExpression();
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param aggregationCollection the collection used to perform the aggregation
     * @return the builder
     */
    public AggregateDefinition aggregate(AggregationCollection aggregationCollection) {
        AggregateDefinition answer = new AggregateDefinition();
        answer.setAggregationCollection(aggregationCollection);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     * @return the builder
     */
    public AggregateDefinition aggregate(Expression correlationExpression) {
        AggregateDefinition answer = new AggregateDefinition(correlationExpression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/aggregator.html">Aggregator EIP:</a>
     * Creates an aggregator allowing you to combine a number of messages together into a single message.
     *
     * @param correlationExpression the expression used to calculate the
     *                              correlation key. For a JMS message this could be the
     *                              expression <code>header("JMSDestination")</code> or
     *                              <code>header("JMSCorrelationID")</code>
     * @param aggregationStrategy the strategy used for the aggregation
     * @return the builder
     */
    public AggregateDefinition aggregate(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        AggregateDefinition answer = new AggregateDefinition(correlationExpression, aggregationStrategy);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param processAtExpression  an expression to calculate the time at which the messages should be processed,
     *                             should be convertable to long as time in millis
     * @return the builder
     */
    public DelayDefinition delay(Expression processAtExpression) {
        return delay(processAtExpression, 0L);
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param processAtExpression  an expression to calculate the time at which the messages should be processed,
     *                             should be convertable to long as time in millis
     * @param delay                the delay in milliseconds which is added to the processAtExpression
     * @return the builder
     */
    public DelayDefinition delay(Expression processAtExpression, long delay) {
        DelayDefinition answer = new DelayDefinition(processAtExpression, delay);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @return the expression clause to create the expression
     */
    public ExpressionClause<DelayDefinition> delay() {
        DelayDefinition answer = new DelayDefinition();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/delayer.html">Delayer EIP:</a>
     * Creates a delayer allowing you to delay the delivery of messages to some destination.
     *
     * @param delay  the default delay in millis
     * @return the builder
     */
    public DelayDefinition delay(long delay) {
        return delay(null, delay);
    }

    /**
     * <a href="http://camel.apache.org/throttler.html">Throttler EIP:</a>
     * Creates a throttler allowing you to ensure that a specific endpoint does not get overloaded,
     * or that we don't exceed an agreed SLA with some external service.
     * <p/>
     * Will default use a time period of 1 second, so setting the maximumRequestCount to eg 10
     * will default ensure at most 10 messages per second. 
     *
     * @param maximumRequestCount  the maximum messages 
     * @return the builder
     */
    public ThrottleDefinition throttle(long maximumRequestCount) {
        ThrottleDefinition answer = new ThrottleDefinition(maximumRequestCount);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @return the clause used to create the loop expression
     */
    public ExpressionClause<LoopDefinition> loop() {
        LoopDefinition loop = new LoopDefinition();
        addOutput(loop);
        return ExpressionClause.createAndSetExpression(loop);
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @param expression the loop expression
     * @return the builder
     */
    public LoopDefinition loop(Expression expression) {
        LoopDefinition loop = getNodeFactory().createLoop();
        loop.setExpression(expression);
        addOutput(loop);
        return loop;
    }

    /**
     * <a href="http://camel.apache.org/loop.html">Loop EIP:</a>
     * Creates a loop allowing to process the a message a number of times and possibly process them
     * in a different way. Useful mostly for testing.
     *
     * @param count  the number of times
     * @return the builder
     */
    public LoopDefinition loop(int count) {
        LoopDefinition loop = getNodeFactory().createLoop();
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
    @SuppressWarnings("unchecked")
    public Type throwFault(Throwable fault) {
        ThrowFaultDefinition answer = new ThrowFaultDefinition();
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
     * Marks the exchange for rollback only.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange
     * and mark it for rollback.
     *
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type rollback() {
        return rollback(null);
    }

    /**
     * Marks the exchange for rollback only.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange
     * and mark it for rollback.
     *
     * @param message an optional message used for logging purpose why the rollback was triggered
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type rollback(String message) {
        RollbackDefinition answer = new RollbackDefinition(message);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other using {@link ExchangePattern#InOnly}.
     *
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param body expression that creates the body to send
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, Expression body) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setNewExchangeExpression(body);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param processor  processor preparing the new exchange to send
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, Processor processor) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setNewExchangeProcessor(processor);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param ref  a reference in the registry to lookup the interceptor that must be of type {@link DelegateProcessor}
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type interceptor(String ref) {
        InterceptorDefinition interceptor = new InterceptorDefinition(ref);
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
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
    public Type intercept(DelegateProcessor interceptor) {
        intercept(new InterceptorDefinition(interceptor));
        return (Type) this;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @return the intercept builder to configure
     */
    public InterceptDefinition intercept() {
        InterceptDefinition answer = new InterceptDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Intercepts outputs added to this node in the future (i.e. intercepts outputs added after this statement)
     *
     * @param  interceptor  the interceptor
     */
    public void intercept(AbstractInterceptorDefinition interceptor) {
        addOutput(interceptor);
        pushBlock(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor  the interceptor
     */
    public void addInterceptor(AbstractInterceptorDefinition interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * Adds an interceptor around the whole of this nodes processing
     *
     * @param interceptor  the interceptor
     */
    public void addInterceptor(DelegateProcessor interceptor) {
        addInterceptor(new InterceptorDefinition(interceptor));
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
     * @see ProcessorDefinition#proceed()
     */
    @SuppressWarnings("unchecked")
    public Type proceed() {
        ProceedDefinition proceed = null;
        ProcessorDefinition currentProcessor = this;

        if (currentProcessor instanceof InterceptDefinition) {
            proceed = ((InterceptDefinition) currentProcessor).getProceed();
            LOG.info("proceed() is the implied and hence not needed for an intercept()");
        }
        if (proceed == null) {
            for (ProcessorDefinition node = parent; node != null; node = node.getParent()) {
                if (node instanceof InterceptDefinition) {
                    InterceptDefinition intercept = (InterceptDefinition)node;
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
    @SuppressWarnings("unchecked")
    public Type stop() {
        ProcessorDefinition currentProcessor = this;

        if (currentProcessor instanceof InterceptDefinition) {
            ((InterceptDefinition) currentProcessor).stopIntercept();
        } else {
            ProcessorDefinition node;
            for (node = parent; node != null; node = node.getParent()) {
                if (node instanceof InterceptDefinition) {
                    ((InterceptDefinition) node).stopIntercept();
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
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for cathing certain exceptions and handling them.
     *
     * @param exceptionType  the exception to catch
     * @return the exception builder to configure
     */
    public OnExceptionDefinition onException(Class exceptionType) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exceptionType);
        addOutput(answer);
        return answer;
    }

    /**
     * Apply an interceptor route if the predicate is true.
     *
     * @param predicate the predicate to test
     * @return  the choice builder to configure
     */
    public ChoiceDefinition intercept(Predicate predicate) {
        InterceptDefinition answer = new InterceptDefinition();
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
    public PolicyDefinition policies() {
        PolicyDefinition answer = new PolicyDefinition();
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
     * @param ref  reference to lookup a policy in the registry
     * @return the policy builder to configure
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
    public PolicyDefinition transacted() {
        PolicyDefinition answer = new PolicyDefinition();
        answer.setType(TransactedPolicy.class);
        addOutput(answer);
        return answer;
    }

    /**
     * Marks this route as transacted.
     *
     * @param ref  reference to lookup a transacted policy in the registry
     * @return the policy builder to configure
     */
    public PolicyDefinition transacted(String ref) {
        PolicyDefinition answer = new PolicyDefinition();
        answer.setType(TransactedPolicy.class);
        answer.setRef(ref);
        addOutput(answer);
        return answer;
    }

    /**
     * Forces handling of faults as exceptions
     *
     * @return the current builder with the fault handler configured
     */
    @SuppressWarnings("unchecked")
    public Type handleFault() {
        intercept(new HandleFaultDefinition());
        return (Type) this;
    }

    /**
     * Installs the given <a href="http://camel.apache.org/error-handler.html">error handler</a> builder.
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    @SuppressWarnings("unchecked")
    public Type errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return (Type) this;
    }

    // Transformers
    // -------------------------------------------------------------------------

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds the custom processor to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     *
     * @param processor  the custom {@link Processor}
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type process(Processor processor) {
        ProcessDefinition answer = new ProcessDefinition(processor);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds the custom processor reference to this destination which could be a final
     * destination, or could be a transformation in a pipeline
     *
     * @param ref   reference to a {@link Processor} to lookup in the registry
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type processRef(String ref) {
        ProcessDefinition answer = new ProcessDefinition();
        answer.setRef(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param bean  the bean to invoke
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type bean(Object bean) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBean(bean);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param bean  the bean to invoke
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type bean(Object bean, String method) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBean(bean);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType  the bean class, Camel will instantiate an object at runtime
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type bean(Class beanType) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param  beanType  the bean class, Camel will instantiate an object at runtime
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type bean(Class beanType, String method) {
        BeanDefinition answer = new BeanDefinition();
        answer.setBeanType(beanType);
        answer.setMethod(method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param ref  reference to a bean to lookup in the registry
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type beanRef(String ref) {
        BeanDefinition answer = new BeanDefinition(ref);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a bean which is invoked which could be a final destination, or could be a transformation in a pipeline
     *
     * @param ref  reference to a bean to lookup in the registry
     * @param method  the method name to invoke on the bean (can be used to avoid ambiguty)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type beanRef(String ref, String method) {
        BeanDefinition answer = new BeanDefinition(ref, method);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the IN message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorDefinition<Type>> setBody() {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>(this);
        SetBodyDefinition answer = new SetBodyDefinition(clause);
        addOutput(answer);
        return clause;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the IN message
     *
     * @param expression   the expression used to set the body
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type setBody(Expression expression) {
        SetBodyDefinition answer = new SetBodyDefinition(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the OUT message
     *
     * @param expression   the expression used to set the body
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type transform(Expression expression) {
        TransformDefinition answer = new TransformDefinition(expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/message-translator.html">Message Translator EIP:</a>
     * Adds a processor which sets the body on the OUT message
     *
     * @return a expression builder clause to set the body
     */
    public ExpressionClause<ProcessorDefinition<Type>> transform() {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>((Type) this);
        TransformDefinition answer = new TransformDefinition(clause);
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
    public ExpressionClause<ProcessorDefinition<Type>> setHeader(String name) {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>(this);
        SetHeaderDefinition answer = new SetHeaderDefinition(name, clause);
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
    @SuppressWarnings("unchecked")
    public Type setHeader(String name, Expression expression) {
        SetHeaderDefinition answer = new SetHeaderDefinition(name, expression);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     *
     * @param name  the header name
     * @return a expression builder clause to set the header
     */
    public ExpressionClause<ProcessorDefinition<Type>> setOutHeader(String name) {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>(this);
        SetOutHeaderDefinition answer = new SetOutHeaderDefinition(name, clause);
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
    @SuppressWarnings("unchecked")
    public Type setOutHeader(String name, Expression expression) {
        SetOutHeaderDefinition answer = new SetOutHeaderDefinition(name, expression);
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
    @SuppressWarnings("unchecked")
    public Type setProperty(String name, Expression expression) {
        SetPropertyDefinition answer = new SetPropertyDefinition(name, expression);
        addOutput(answer);
        return (Type) this;
    }


    /**
     * Adds a processor which sets the exchange property
     *
     * @param name  the property name
     * @return a expression builder clause to set the property
     */
    public ExpressionClause<ProcessorDefinition<Type>> setProperty(String name) {
        ExpressionClause<ProcessorDefinition<Type>> clause = new ExpressionClause<ProcessorDefinition<Type>>(this);
        SetPropertyDefinition answer = new SetPropertyDefinition(name, clause);
        addOutput(answer);
        return clause;
    }

    /**
     * Adds a processor which removes the header on the IN message
     *
     * @param name  the header name
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type removeHeader(String name) {
        RemoveHeaderDefinition answer = new RemoveHeaderDefinition(name);
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
    @SuppressWarnings("unchecked")
    public Type removeProperty(String name) {
        RemovePropertyDefinition answer = new RemovePropertyDefinition(name);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Converts the IN message body to the specified type
     *
     * @param type the type to convert to
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type convertBodyTo(Class type) {
        addOutput(new ConvertBodyDefinition(type));
        return (Type) this;
    }
    
    /**
     * Converts the IN message body to the specified class type
     *
     * @param typeString the type to convert to as a fully qualified classname
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type convertBodyTo(String typeString) {
        addOutput(new ConvertBodyDefinition(typeString));
        return (Type) this;
    }

    /**
     * Sorts the IN message body using the given comparator.
     * The IN body mut be convertable to {@link List}.
     *
     * @param comparator  the comparator to use for sorting
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type sortBody(Comparator comparator) {
        addOutput(new SortDefinition(body(), comparator));
        return (Type) this;
    }

    /**
     * Sorts the IN message body using a default sorting based on toString representation.
     * The IN body mut be convertable to {@link List}.
     *
     * @return the builder
     */
    public Type sortBody() {
        return sortBody(null);
    }

    /**
     * Sorts the expression using the given comparator
     *
     * @param expression  the expression, must be convertable to {@link List}
     * @param comparator  the comparator to use for sorting
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type sort(Expression expression, Comparator comparator) {
        addOutput(new SortDefinition(expression, comparator));
        return (Type) this;
    }

    /**
     * Sorts the expression using a default sorting based on toString representation. 
     *
     * @param expression  the expression, must be convertable to {@link List}
     * @return the builder
     */
    public Type sort(Expression expression) {
        return sort(expression, null);
    }

    /**
     * Enriches an exchange with additional data obtained from a
     * <code>resourceUri</code>.
     * 
     * @param resourceUri
     *            URI of resource endpoint for obtaining additional data.
     * @param aggregationStrategy
     *            aggregation strategy to aggregate input data and additional
     *            data.
     * @return this processor type
     * @see org.apache.camel.processor.Enricher
     */
    @SuppressWarnings("unchecked")
    public Type enrich(String resourceUri, AggregationStrategy aggregationStrategy) {
        addOutput(new EnrichDefinition(aggregationStrategy, resourceUri));
        return (Type)this;
    }
    
    // DataFormat support
    // -------------------------------------------------------------------------

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Unmarshals the in body using a {@link DataFormat} expression to define
     * the format of the input message and the output will be set on the out message body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorDefinition<Type>> unmarshal() {
        return new DataFormatClause<ProcessorDefinition<Type>>(this, DataFormatClause.Operation.Unmarshal);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormatType  the dataformat
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type unmarshal(DataFormatDefinition dataFormatType) {
        addOutput(new UnmarshalDefinition(dataFormatType));
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormat  the dataformat
     * @return the builder
     */
    public Type unmarshal(DataFormat dataFormat) {
        return unmarshal(new DataFormatDefinition(dataFormat));
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Unmarshals the in body using the specified {@link DataFormat}
     * reference in the {@link org.apache.camel.spi.Registry} and sets
     * the output on the out message body.
     *
     * @param dataTypeRef  reference to a {@link DataFormat} to lookup in the registry
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type unmarshal(String dataTypeRef) {
        addOutput(new UnmarshalDefinition(dataTypeRef));
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Marshals the in body using a {@link DataFormat} expression to define
     * the format of the output which will be added to the out body.
     *
     * @return the expression to create the {@link DataFormat}
     */
    public DataFormatClause<ProcessorDefinition<Type>> marshal() {
        return new DataFormatClause<ProcessorDefinition<Type>>(this, DataFormatClause.Operation.Marshal);
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormatType  the dataformat
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type marshal(DataFormatDefinition dataFormatType) {
        addOutput(new MarshalDefinition(dataFormatType));
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Marshals the in body using the specified {@link DataFormat}
     * and sets the output on the out message body.
     *
     * @param dataFormat  the dataformat
     * @return the builder
     */
    public Type marshal(DataFormat dataFormat) {
        return marshal(new DataFormatDefinition(dataFormat));
    }

    /**
     * <a href="http://camel.apache.org/data-format.html">DataFormat:</a>
     * Marshals the in body the specified {@link DataFormat}
     * reference in the {@link org.apache.camel.spi.Registry} and sets
     * the output on the out message body.
     *
     * @param dataTypeRef  reference to a {@link DataFormat} to lookup in the registry
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type marshal(String dataTypeRef) {
        addOutput(new MarshalDefinition(dataTypeRef));
        return (Type) this;
    }

    // Properties
    // -------------------------------------------------------------------------
    @XmlTransient
    @SuppressWarnings("unchecked")
    public ProcessorDefinition<? extends ProcessorDefinition> getParent() {
        return parent;
    }

    public void setParent(ProcessorDefinition<? extends ProcessorDefinition> parent) {
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
     * @throws Exception can be thrown in case of error
     */
    protected Processor wrapProcessorInInterceptors(RouteContext routeContext, Processor target) throws Exception {
        ObjectHelper.notNull(target, "target", this);

        List<InterceptStrategy> strategies = new ArrayList<InterceptStrategy>();
        CamelContext camelContext = routeContext.getCamelContext();
        strategies.addAll(camelContext.getInterceptStrategies());
        strategies.addAll(routeContext.getInterceptStrategies());
        // TODO: Order the strategies, eg using Comparable so we can have Tracer near the real processor
        for (InterceptStrategy strategy : strategies) {
            if (strategy != null) {
                target = strategy.wrapProcessorInInterceptors(this, target);
            }
        }

        List<AbstractInterceptorDefinition> list = routeContext.getRoute().getInterceptors();
        if (interceptors != null) {
            list.addAll(interceptors);
        }
        // lets reverse the list so we apply the inner interceptors first
        Collections.reverse(list);
        Set<Processor> interceptors = new HashSet<Processor>();
        interceptors.add(target);
        for (AbstractInterceptorDefinition interceptorType : list) {
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
        ObjectHelper.notNull(target, "target", this);
        ErrorHandlerWrappingStrategy strategy = routeContext.getErrorHandlerWrappingStrategy();
        if (strategy != null) {
            return strategy.wrapProcessorInErrorHandler(this, target);
        }
        return getErrorHandlerBuilder().createErrorHandler(routeContext, target);
    }

    protected ErrorHandlerBuilder createErrorHandlerBuilder() {
        if (errorHandlerRef != null) {
            return new ErrorHandlerBuilderRef(errorHandlerRef);
        }

        // return a reference to the default error handler
        return new ErrorHandlerBuilderRef(ErrorHandlerBuilderRef.DEFAULT_ERROR_HANDLER_BUILDER);
    }

    protected void configureChild(ProcessorDefinition output) {
        output.setNodeFactory(getNodeFactory());
        output.setErrorHandlerBuilder(getErrorHandlerBuilder());
    }

    @SuppressWarnings("unchecked")
    public void addOutput(ProcessorDefinition processorType) {
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

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorDefinition> outputs)
        throws Exception {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorDefinition output : outputs) {
            Processor processor = output.createProcessor(routeContext);
            // if the ProceedType/StopType create processor is null we keep on going
            if ((output instanceof ProceedDefinition || output instanceof StopDefinition) && processor == null) {
                continue;
            }
            processor = output.wrapProcessorInInterceptors(routeContext, processor);

            ProcessorDefinition currentProcessor = this;
            if (!(currentProcessor instanceof OnExceptionDefinition || currentProcessor instanceof TryDefinition)) {
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
