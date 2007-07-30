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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.FromBuilder;
import org.apache.camel.builder.InterceptorBuilder;
import org.apache.camel.builder.PolicyBuilder;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.builder.ResequencerBuilder;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.idempotent.MessageIdRepository;
import org.apache.camel.spi.Policy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents an XML &lt;route/&gt; element
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"interceptors", "inputs", "outputs"})
public class RouteType extends ProcessorType implements CamelContextAware {
    public static final String DEFAULT_TRACE_CATEGORY = "org.apache.camel.TRACE";

    private static final transient Log log = LogFactory.getLog(RouteType.class);
    private CamelContext camelContext;
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    private List<FromType> inputs = new ArrayList<FromType>();
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();

    @Override
    public String toString() {
        return "Route[ " + inputs + " -> " + outputs + "]";
    }

    public void addRoutes(CamelContext context) throws Exception {
        setCamelContext(context);

        Collection<Route> routes = new ArrayList<Route>();

        for (FromType fromType : inputs) {
            addRoutes(routes, fromType);
        }

        context.addRoutes(routes);
    }

    public Endpoint resolveEndpoint(String uri) {
        CamelContext context = getCamelContext();
        if (context == null) {
            throw new IllegalArgumentException("No CamelContext has been injected!");
        }
        Endpoint answer = context.getEndpoint(uri);
        if (answer == null) {
            throw new IllegalArgumentException("No Endpoint found for uri: " + uri);
        }
        return answer;
    }

    // Fluent API
    //-----------------------------------------------------------------------
    public RouteType from(String uri) {
        getInputs().add(new FromType(uri));
        return this;
    }

    /**
     * Sends the exchange to the given endpoint URI
     */
    public RouteType to(String uri) {
        getOutputs().add(new ToType(uri));
        return this;
    }

    /**
     * Sends the exchange to the given endpoint
     */
    public RouteType to(Endpoint endpoint) {
        getOutputs().add(new ToType(endpoint));
        return this;
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    public RouteType to(String... uris) {
        for (String uri : uris) {
            getOutputs().add(new ToType(uri));
        }
        return this;
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    public RouteType to(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            getOutputs().add(new ToType(endpoint));
        }
        return this;
    }

    /**
     * Sends the exchange to a list of endpoint using the {@link MulticastProcessor} pattern
     */
    public RouteType to(Collection<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            getOutputs().add(new ToType(endpoint));
        }
        return this;
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    public RouteType pipeline(String... uris) {
        // TODO pipeline v mulicast
        return to(uris);
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    public RouteType pipeline(Endpoint... endpoints) {
        // TODO pipeline v mulicast
        return to(endpoints);
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    public RouteType pipeline(Collection<Endpoint> endpoints) {
        // TODO pipeline v mulicast
        return to(endpoints);
    }

    /**
     * Creates an {@link IdempotentConsumer} to avoid duplicate messages
     */
    public IdempotentConsumerType idempotentConsumer(Expression messageIdExpression, MessageIdRepository messageIdRepository) {
        IdempotentConsumerType answer = new IdempotentConsumerType(messageIdExpression, messageIdRepository);
        getOutputs().add(answer);
        return answer;
    }

    /**
     * Creates a predicate which is applied and only if it is true then
     * the exchange is forwarded to the destination
     *
     * @return the builder for a predicate
     */
    public FilterType filter(Predicate predicate) {
        FilterType filter = new FilterType(predicate);
        getOutputs().add(filter);
        return filter;
    }

    /**
     * Creates a choice of one or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    public ChoiceType choice() {
        ChoiceType answer = new ChoiceType();
        getOutputs().add(answer);
        return answer;
    }

    /**
     * Creates a dynamic <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a> pattern.
     *
     * @param receipients is the builder of the expression used in the {@link RecipientList} to decide the destinations
     */
    public RouteType recipientList(Expression receipients) {
        RecipientListType answer = new RecipientListType(receipients);
        getOutputs().add(answer);
        return this;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/splitter.html">Splitter</a> pattern
     * where an expression is evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.
     *
     * @param receipients the expression on which to split
     * @return the builder
     */
    public SplitterType splitter(Expression receipients) {
        SplitterType answer = new SplitterType(receipients);
        getOutputs().add(answer);
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
    public ResequencerType resequencer(Expression<Exchange> expression) {
        return resequencer(Collections.<Expression>singletonList(expression));
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/resequencer.html">Resequencer</a> pattern
     * where a list of expressions are evaluated to be able to compare the message exchanges to reorder them. e.g. you
     * may wish to sort by some headers
     *
     * @param expressions the expressions on which to compare messages in order
     * @return the builder
     */
    public ResequencerType resequencer(List<Expression> expressions) {
        ResequencerType answer = new ResequencerType(expressions);
        getOutputs().add(answer);
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
    public ResequencerType resequencer(Expression... expressions) {
        List<Expression> list = new ArrayList<Expression>();
        for (Expression expression : expressions) {
            list.add(expression);
        }
        return resequencer(list);
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a> pattern
     * where a batch of messages are processed (up to a maximum amount or until some timeout is reached)
     * and messages for the same correlation key are combined together using some kind of
     * {@link AggregationStrategy ) (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000 messages/second and you may want to
     * throttle it right down so that multiple messages for the same stock are combined (or just the latest
     * message is used and older prices are discarded). Another idea is to combine line item messages together
     * into a single invoice message.
     *
     * @param correlationExpression the expression used to calculate the correlation key. For a JMS message this could
     *                              be the expression <code>header("JMSDestination")</code> or  <code>header("JMSCorrelationID")</code>
     */
    public AggregatorType aggregator(Expression correlationExpression) {
        AggregatorType answer = new AggregatorType(correlationExpression);
        getOutputs().add(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/aggregator.html">Aggregator</a> pattern
     * where a batch of messages are processed (up to a maximum amount or until some timeout is reached)
     * and messages for the same correlation key are combined together using some kind of
     * {@link AggregationStrategy ) (by default the latest message is used) to compress many message exchanges
     * into a smaller number of exchanges.
     * <p/>
     * A good example of this is stock market data; you may be receiving 30,000 messages/second and you may want to
     * throttle it right down so that multiple messages for the same stock are combined (or just the latest
     * message is used and older prices are discarded). Another idea is to combine line item messages together
     * into a single invoice message.
     *
     * @param correlationExpression the expression used to calculate the correlation key. For a JMS message this could
     *                              be the expression <code>header("JMSDestination")</code> or  <code>header("JMSCorrelationID")</code>
     */
    public AggregatorType aggregator(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        AggregatorType answer = new AggregatorType(correlationExpression, aggregationStrategy);
        getOutputs().add(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will be dispatched on
     *
     * @param processAtExpression an expression to calculate the time at which the messages should be processed
     * @return the builder
     */
    public DelayerType delayer(Expression<Exchange> processAtExpression) {
        return delayer(processAtExpression, 0L);
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will be dispatched on
     *
     * @param processAtExpression an expression to calculate the time at which the messages should be processed
     * @param delay               the delay in milliseconds which is added to the processAtExpression to determine the time the
     *                            message should be processed
     * @return the builder
     */
    public DelayerType delayer(Expression<Exchange> processAtExpression, long delay) {
        DelayerType answer = new DelayerType(processAtExpression, delay);
        getOutputs().add(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where a fixed amount of milliseconds are used to delay processing of a message exchange
     *
     * @param delay the default delay in milliseconds
     * @return the builder
     */
    public DelayerType delayer(long delay) {
        return delayer(null, delay);
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/delayer.html">Delayer</a> pattern
     * where an expression is used to calculate the time which the message will be dispatched on
     *
     * @return the builder
     */
    public ThrottlerType throttler(long maximumRequestCount) {
        ThrottlerType answer = new ThrottlerType(maximumRequestCount);
        getOutputs().add(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    public RouteType errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        setErrorHandlerBuilder(errorHandlerBuilder);
        return this;
    }

    /**
     * Configures whether or not the error handler is inherited by every processing node (or just the top most one)
     *
     * @param condition the falg as to whether error handlers should be inherited or not
     * @return the current builder
     */
    public RouteType inheritErrorHandler(boolean condition) {
        setInheritErrorHandlerFlag(condition);
        return this;
    }


    /**
     * Trace logs the exchange before it goes to the next processing step using the {@link #DEFAULT_TRACE_CATEGORY} logging
     * category.
     *
     * @return
     */
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
    public FromBuilder trace(String category) {
        final Log log = LogFactory.getLog(category);
        return intercept(new DelegateProcessor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                log.trace(exchange);
                processNext(exchange);
            }
        });
    }

    public PolicyBuilder policies() {
        throw new UnsupportedOperationException();
/*
        PolicyBuilder answer = new PolicyBuilder(this);
        addProcessBuilder(answer);
        return answer;
*/
    }

    public FromBuilder policy(Policy policy) {
        throw new UnsupportedOperationException();
/*
        PolicyBuilder answer = new PolicyBuilder(this);
        answer.add(policy);
        addProcessBuilder(answer);
        return answer.target();
*/
    }

    public InterceptorBuilder intercept() {
        throw new UnsupportedOperationException();
/*
        InterceptorBuilder answer = new InterceptorBuilder(this);
        addProcessBuilder(answer);
        return answer;
*/
    }

    public FromBuilder intercept(DelegateProcessor interceptor) {
        throw new UnsupportedOperationException();
/*
        InterceptorBuilder answer = new InterceptorBuilder(this);
        answer.add(interceptor);
        addProcessBuilder(answer);
        return answer.target();
*/
    }

    // Transformers
    //-------------------------------------------------------------------------

    /**
     * Adds the custom processor to this destination which could be a final destination, or could be a transformation in a pipeline
     */
    public RouteType process(Processor processor) {
        ProcessorRef answer = new ProcessorRef(processor);
        getOutputs().add(answer);
        return this;
    }

    /**
     * Adds a processor which sets the body on the IN message
     */
    public RouteType setBody(Expression expression) {
        return process(ProcessorBuilder.setBody(expression));
    }

    /**
     * Adds a processor which sets the body on the OUT message
     */
    public RouteType setOutBody(Expression expression) {
        return process(ProcessorBuilder.setOutBody(expression));
    }

    /**
     * Adds a processor which sets the header on the IN message
     */
    public RouteType setHeader(String name, Expression expression) {
        return process(ProcessorBuilder.setHeader(name, expression));
    }

    /**
     * Adds a processor which sets the header on the OUT message
     */
    public RouteType setOutHeader(String name, Expression expression) {
        return process(ProcessorBuilder.setOutHeader(name, expression));
    }

    /**
     * Adds a processor which sets the exchange property
     */
    public RouteType setProperty(String name, Expression expression) {
        return process(ProcessorBuilder.setProperty(name, expression));
    }

    /**
     * Converts the IN message body to the specified type
     */
    public RouteType convertBodyTo(Class type) {
        return process(ProcessorBuilder.setBody(Builder.body().convertTo(type)));
    }

    /**
     * Converts the OUT message body to the specified type
     */
    public RouteType convertOutBodyTo(Class type) {
        return process(ProcessorBuilder.setOutBody(Builder.outBody().convertTo(type)));
    }

    // Properties
    //-----------------------------------------------------------------------

    @XmlElement(required = false, name = "interceptor")
    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }

    @XmlElementRef
    public List<FromType> getInputs() {
        return inputs;
    }

    public void setInputs(List<FromType> inputs) {
        this.inputs = inputs;
    }

    @XmlElementRef
    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;

        if (outputs != null) {
            for (ProcessorType output : outputs) {
                configureChild(output);
            }
        }
    }

    @XmlTransient
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void addRoutes(Collection<Route> routes, FromType fromType) throws Exception {
        RouteContext routeContext = new RouteContext(this, fromType, routes);
        Endpoint endpoint = routeContext.getEndpoint();

        for (ProcessorType output : outputs) {
            output.addRoutes(routeContext, routes);
        }

        routeContext.commit();
    }

    protected void configureChild(ProcessorType output) {
        List<InterceptorRef> list = output.getInterceptors();
        if (list == null) {
            log.warn("No interceptor collection: " + output);
        }
        else {
            list.addAll(getInterceptors());
        }
    }
}
