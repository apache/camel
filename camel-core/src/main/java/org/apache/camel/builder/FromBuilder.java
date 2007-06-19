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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.processor.CompositeProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MessageIdRepository;
import org.apache.camel.spi.Policy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @version $Revision$
 */
public class FromBuilder extends BuilderSupport implements ProcessorFactory {
    public static final String DEFAULT_TRACE_CATEGORY = "org.apache.camel.TRACE";
    private RouteBuilder builder;
    private Endpoint from;
    private List<Processor> processors = new ArrayList<Processor>();
    private List<ProcessorFactory> processFactories = new ArrayList<ProcessorFactory>();
    private FromBuilder routeBuilder;

    public FromBuilder(RouteBuilder builder, Endpoint from) {
        super(builder);
        this.builder = builder;
        this.from = from;
    }

    public FromBuilder(FromBuilder parent) {
        super(parent);
        this.builder = parent.getBuilder();
        this.from = parent.getFrom();
    }

    /**
     * Sends the exchange to the given endpoint URI
     */
    @Fluent
    public ProcessorFactory to(@FluentArg("uri")String uri) {
        return to(endpoint(uri));
    }

    /**
     * Sends the exchange to the given endpoint
     */
    @Fluent
    public ProcessorFactory to(@FluentArg("ref")Endpoint endpoint) {
        ToBuilder answer = new ToBuilder(this, endpoint);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory to(String... uris) {
        return to(endpoints(uris));
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory to(
            @FluentArg(value = "endpoint", attribute = false, element = true)
            Endpoint... endpoints) {
        return to(endpoints(endpoints));
    }

    /**
     * Sends the exchange to a list of endpoint using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory to(@FluentArg(value = "endpoint", attribute = false, element = true)
    Collection<Endpoint> endpoints) {
        return addProcessBuilder(new MulticastBuilder(this, endpoints));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory pipeline(@FluentArg("uris")String... uris) {
        return pipeline(endpoints(uris));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory pipeline(@FluentArg("endpoints")Endpoint... endpoints) {
        return pipeline(endpoints(endpoints));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory pipeline(@FluentArg("endpoints")Collection<Endpoint> endpoints) {
        return addProcessBuilder(new PipelineBuilder(this, endpoints));
    }

    /**
     * Creates an {@link IdempotentConsumer} to avoid duplicate messages
     */
    @Fluent
    public IdempotentConsumerBuilder idempotentConsumer(
            @FluentArg("messageIdExpression")Expression messageIdExpression,
            @FluentArg("MessageIdRepository")MessageIdRepository messageIdRepository) {
        return (IdempotentConsumerBuilder) addProcessBuilder(new IdempotentConsumerBuilder(this, messageIdExpression, messageIdRepository));
    }

    /**
     * Creates a predicate which is applied and only if it is true then
     * the exchange is forwarded to the destination
     *
     * @return the builder for a predicate
     */
    @Fluent
    public FilterBuilder filter(
            @FluentArg(value = "predicate", element = true)
            Predicate predicate) {
        FilterBuilder answer = new FilterBuilder(this, predicate);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a choice of one or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    @Fluent(nestedActions = true)
    public ChoiceBuilder choice() {
        ChoiceBuilder answer = new ChoiceBuilder(this);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a dynamic <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a> pattern.
     *
     * @param receipients is the builder of the expression used in the {@link RecipientList} to decide the destinations
     */
    @Fluent
    public RecipientListBuilder recipientList(
            @FluentArg(value = "recipients", element = true)
            Expression receipients) {
        RecipientListBuilder answer = new RecipientListBuilder(this, receipients);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/splitter.html">Splitter</a> pattern
     * where an expression is evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.
     *
     * @param receipients the expression on which to split
     * @return the builder
     */
    @Fluent
    public SplitterBuilder splitter(@FluentArg(value = "recipients", element = true)Expression receipients) {
        SplitterBuilder answer = new SplitterBuilder(this, receipients);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a> pattern
     * where an expression is evaluated to be able to compare the message exchanges to reorder them. e.g. you
     * may wish to sort by some header
     *
     * @param expression the expression on which to compare messages in order
     * @return the builder
     */
    public ResequencerBuilder resequencer(Expression<Exchange> expression) {
        return resequencer(Collections.<Expression<Exchange>>singletonList(expression));
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a> pattern
     * where a list of expressions are evaluated to be able to compare the message exchanges to reorder them. e.g. you
     * may wish to sort by some headers
     *
     * @param expressions the expressions on which to compare messages in order
     * @return the builder
     */
    @Fluent
    public ResequencerBuilder resequencer(@FluentArg(value = "expressions")List<Expression<Exchange>> expressions) {
        ResequencerBuilder answer = new ResequencerBuilder(this, expressions);
        setRouteBuilder(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a> pattern
     * where a list of expressions are evaluated to be able to compare the message exchanges to reorder them. e.g. you
     * may wish to sort by some headers
     *
     * @param expressions the expressions on which to compare messages in order
     * @return the builder
     */
    @Fluent
    public ResequencerBuilder resequencer(Expression<Exchange>... expressions) {
        List<Expression<Exchange>> list = new ArrayList<Expression<Exchange>>();
        for (Expression<Exchange> expression : expressions) {
            list.add(expression);
        }
        return resequencer(list);
    }

    /**
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    @Fluent
    public FromBuilder errorHandler(@FluentArg("handler")ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the error handler is inherited by every processing node (or just the top most one)
     *
     * @param condition the falg as to whether error handlers should be inherited or not
     * @return the current builder
     */
    @Fluent
    public FromBuilder inheritErrorHandler(@FluentArg("condition")boolean condition) {
        setInheritErrorHandler(condition);
        return this;
    }

    @Fluent(nestedActions = true)
    public InterceptorBuilder intercept() {
        InterceptorBuilder answer = new InterceptorBuilder(this);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Trace logs the exchange before it goes to the next processing step using the {@link #DEFAULT_TRACE_CATEGORY} logging
     * category.
     *
     * @return
     */
    @Fluent
    public FromBuilder trace() {
        return trace(DEFAULT_TRACE_CATEGORY);
    }

    /**
     * Trace logs the exchange before it goes to the next processing step using the specified logging
     * category.
     *
     * @param category the logging category trace messages will sent to.
     * @return
     */
    @Fluent
    public FromBuilder trace(@FluentArg("category")String category) {
        final Log log = LogFactory.getLog(category);
        return intercept(new DelegateProcessor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                log.trace(exchange);
                processNext(exchange);
            }
        });
    }

    @Fluent
    public FromBuilder intercept(@FluentArg("interceptor")DelegateProcessor interceptor) {
        InterceptorBuilder answer = new InterceptorBuilder(this);
        answer.add(interceptor);
        addProcessBuilder(answer);
        return answer.target();
    }

    @Fluent(nestedActions = true)
    public PolicyBuilder policies() {
        PolicyBuilder answer = new PolicyBuilder(this);
        addProcessBuilder(answer);
        return answer;
    }

    @Fluent
    public FromBuilder policy(@FluentArg("policy")Policy policy) {
        PolicyBuilder answer = new PolicyBuilder(this);
        answer.add(policy);
        addProcessBuilder(answer);
        return answer.target();
    }

    // Transformers
    //-------------------------------------------------------------------------

    /**
     * Adds the custom processor to this destination which could be a final destination, or could be a transformation in a pipeline
     */
    @Fluent
    public FromBuilder process(@FluentArg("ref")Processor processor) {
        addProcessorBuilder(processor);
        return this;
    }

    /**
     * Adds a processor which sets the body on the IN message
     */
    @Fluent
    public FromBuilder setBody(Expression expression) {
        addProcessorBuilder(ProcessorBuilder.setBody(expression));
        return this;
    }

    /**
     * Adds a processor which sets the body on the OUT message
     */
    @Fluent
    public FromBuilder setOutBody(Expression expression) {
        addProcessorBuilder(ProcessorBuilder.setOutBody(expression));
        return this;
    }

    /**
     * Adds a processor which sets the header on the IN message
     */
    @Fluent
    public FromBuilder setHeader(String name, Expression expression) {
        addProcessorBuilder(ProcessorBuilder.setHeader(name, expression));
        return this;
    }

    /**
     * Adds a processor which sets the header on the OUT message
     */
    @Fluent
    public FromBuilder setOutHeader(String name, Expression expression) {
        addProcessorBuilder(ProcessorBuilder.setOutHeader(name, expression));
        return this;
    }

    /**
     * Adds a processor which sets the exchange property
     */
    @Fluent
    public FromBuilder setProperty(String name, Expression expression) {
        addProcessorBuilder(ProcessorBuilder.setProperty(name, expression));
        return this;
    }

    /**
     * Converts the IN message body to the specified type
     */
    @Fluent
    public FromBuilder convertBodyTo(Class type) {
        addProcessorBuilder(ProcessorBuilder.setBody(Builder.body().convertTo(type)));
        return this;
    }

    /**
     * Converts the OUT message body to the specified type
     */
    @Fluent
    public FromBuilder convertOutBodyTo(Class type) {
        addProcessorBuilder(ProcessorBuilder.setOutBody(Builder.outBody().convertTo(type)));
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------
    public RouteBuilder getBuilder() {
        return builder;
    }

    public Endpoint getFrom() {
        return from;
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public ProcessorFactory addProcessBuilder(ProcessorFactory processFactory) {
        processFactories.add(processFactory);
        return processFactory;
    }

    protected void addProcessorBuilder(Processor processor) {
        addProcessBuilder(new ConstantProcessorBuilder(processor));
    }

    public void addProcessor(Processor processor) {
        processors.add(processor);
    }

    public Route createRoute() throws Exception {
        if (routeBuilder != null) {
            return routeBuilder.createRoute();
        }
        Processor processor = createProcessor();
        if (processor == null) {
            throw new IllegalArgumentException("No processor created for: " + this);
        }
        return new EventDrivenConsumerRoute(getFrom(), processor);
    }

    public Processor createProcessor() throws Exception {
        List<Processor> answer = new ArrayList<Processor>();

        for (ProcessorFactory processFactory : processFactories) {
            Processor processor = makeProcessor(processFactory);
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for processBuilder: " + processFactory);
            }
            answer.add(processor);
        }
        if (answer.size() == 0) {
            return null;
        }
        Processor processor = null;
        if (answer.size() == 1) {
            processor = answer.get(0);
        }
        else {
            processor = new CompositeProcessor(answer);
        }
        return processor;
    }

    /**
     * Creates the processor and wraps it in any necessary interceptors and error handlers
     */
    protected Processor makeProcessor(ProcessorFactory processFactory) throws Exception {
        Processor processor = processFactory.createProcessor();
        processor = wrapProcessor(processor);
        return wrapInErrorHandler(processor);
    }

    /**
     * A strategy method to allow newly created processors to be wrapped in an error handler. This feature
     * could be disabled for child builders such as {@link IdempotentConsumerBuilder} which will rely on the
     * {@link FromBuilder} to perform the error handling to avoid doubly-wrapped processors with 2 nested error handlers
     */
    protected Processor wrapInErrorHandler(Processor processor) throws Exception {
        return getErrorHandlerBuilder().createErrorHandler(processor);
    }

    /**
     * A strategy method which allows derived classes to wrap the child processor in some kind of interceptor such as
     * a filter for the {@link IdempotentConsumerBuilder}.
     *
     * @param processor the processor which can be wrapped
     * @return the original processor or a new wrapped interceptor
     */
    protected Processor wrapProcessor(Processor processor) {
        return processor;
    }

    protected FromBuilder getRouteBuilder() {
        return routeBuilder;
    }

    protected void setRouteBuilder(FromBuilder routeBuilder) {
        this.routeBuilder = routeBuilder;
    }
}
