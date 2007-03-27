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
import java.util.Collection;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.processor.CompositeProcessor;
import org.apache.camel.processor.InterceptorProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;

/**
 * @version $Revision$
 */
public class FromBuilder<E extends Exchange> extends BuilderSupport<E> implements ProcessorFactory<E> {
    private RouteBuilder<E> builder;
    private Endpoint<E> from;
    private List<Processor<E>> processors = new ArrayList<Processor<E>>();
    private List<ProcessorFactory<E>> processFactories = new ArrayList<ProcessorFactory<E>>();

    public FromBuilder(RouteBuilder<E> builder, Endpoint<E> from) {
        super(builder);
        this.builder = builder;
        this.from = from;
    }

    public FromBuilder(FromBuilder<E> parent) {
        super(parent);
        this.builder = parent.getBuilder();
        this.from = parent.getFrom();
    }

    /**
     * Sends the exchange to the given endpoint URI
     */
    @Fluent
    public ProcessorFactory<E> to(@FluentArg("uri") String uri) {
        return to(endpoint(uri));
    }

    /**
     * Sends the exchange to the given endpoint
     */
    @Fluent
    public ProcessorFactory<E> to(@FluentArg("endpoint") Endpoint<E> endpoint) {
        ToBuilder<E> answer = new ToBuilder<E>(this, endpoint);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory<E> to(
    		@FluentArg(value="uri", attribute=false, element=true) 
    		String... uris) {
        return to(endpoints(uris));
    }

    /**
     * Sends the exchange to a list of endpoints using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory<E> to(
    		@FluentArg(value="endpoint", attribute=false, element=true) 
    		Endpoint<E>... endpoints) {
        return to(endpoints(endpoints));
    }

    /**
     * Sends the exchange to a list of endpoint using the {@link MulticastProcessor} pattern
     */
    @Fluent
    public ProcessorFactory<E> to(@FluentArg("endpoints") Collection<Endpoint<E>> endpoints) {
        return addProcessBuilder(new MulticastBuilder<E>(this, endpoints));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory<E> pipeline(@FluentArg("uris") String... uris) {
        return pipeline(endpoints(uris));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory<E> pipeline(@FluentArg("endpoints") Endpoint<E>... endpoints) {
        return pipeline(endpoints(endpoints));
    }

    /**
     * Creates a {@link Pipeline} of the list of endpoints so that the message will get processed by each endpoint in turn
     * and for request/response the output of one endpoint will be the input of the next endpoint
     */
    @Fluent
    public ProcessorFactory<E> pipeline(@FluentArg("endpoints") Collection<Endpoint<E>> endpoints) {
        return addProcessBuilder(new PipelineBuilder<E>(this, endpoints));
    }

    /**
     * Adds the custom processor to this destination
     */
    @Fluent
    public ConstantProcessorBuilder<E> process(@FluentArg("ref") Processor<E> processor) {
        ConstantProcessorBuilder<E> answer = new ConstantProcessorBuilder<E>(processor);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a predicate which is applied and only if it is true then
     * the exchange is forwarded to the destination
     *
     * @return the builder for a predicate
     */
    @Fluent
    public FilterBuilder<E> filter(
    		@FluentArg(value="predicate",element=true) 
    		Predicate<E> predicate) {
        FilterBuilder<E> answer = new FilterBuilder<E>(this, predicate);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a choice of one or more predicates with an otherwise clause
     *
     * @return the builder for a choice expression
     */
    @Fluent(nestedActions=true)
    public ChoiceBuilder<E> choice() {
        ChoiceBuilder<E> answer = new ChoiceBuilder<E>(this);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Creates a dynamic <a href="http://activemq.apache.org/camel/recipient-list.html">Recipient List</a> pattern.
     *
     * @param receipients is the builder of the expression used in the {@link RecipientList} to decide the destinations
     */
    @Fluent
    public RecipientListBuilder<E> recipientList(
    		@FluentArg(value="recipients",element=true) 
    		ValueBuilder<E> receipients) {
        RecipientListBuilder<E> answer = new RecipientListBuilder<E>(this, receipients);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * A builder for the <a href="http://activemq.apache.org/camel/splitter.html">Splitter</a> pattern
     * where an expression is evaluated to iterate through each of the parts of a message and then each part is then send to some endpoint.
     *
     * @param receipients the builder for the value used as the expression on which to split
     * @return the builder
     */
    @Fluent
    public SplitterBuilder<E> splitter(@FluentArg(value="recipients", element=true) ValueBuilder<E> receipients) {
        SplitterBuilder<E> answer = new SplitterBuilder<E>(this, receipients);
        addProcessBuilder(answer);
        return answer;
    }

    /**
     * Installs the given error handler builder
     *
     * @param errorHandlerBuilder the error handler to be used by default for all child routes
     * @return the current builder with the error handler configured
     */
    @Fluent
    public FromBuilder<E> errorHandler(@FluentArg("handler") ErrorHandlerBuilder errorHandlerBuilder) {
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
    public FromBuilder<E> inheritErrorHandler(@FluentArg("condition") boolean condition) {
        setInheritErrorHandler(condition);
        return this;
    }
    
    @Fluent(nestedActions=true)
    public InterceptorBuilder<E> intercept() {
        InterceptorBuilder<E> answer = new InterceptorBuilder<E>(this);
        addProcessBuilder(answer);
        return answer;
    }

    @Fluent
    public InterceptorBuilder<E> intercept(@FluentArg("interceptor") InterceptorProcessor<E> interceptor) {
        InterceptorBuilder<E> answer = new InterceptorBuilder<E>(this);
        answer.add(interceptor);
        addProcessBuilder(answer);
        return answer;
    }


    // Properties
    //-------------------------------------------------------------------------
    public RouteBuilder<E> getBuilder() {
        return builder;
    }

    public Endpoint<E> getFrom() {
        return from;
    }

    public ProcessorFactory<E> addProcessBuilder(ProcessorFactory<E> processFactory) {
        processFactories.add(processFactory);
        return processFactory;
    }

    public void addProcessor(Processor<E> processor) {
        processors.add(processor);
    }

    public Processor<E> createProcessor() throws Exception {
        List<Processor<E>> answer = new ArrayList<Processor<E>>();

        for (ProcessorFactory<E> processFactory : processFactories) {
            Processor<E> processor = makeProcessor(processFactory);
            if (processor == null) {
                throw new IllegalArgumentException("No processor created for processBuilder: " + processFactory);
            }
            answer.add(processor);
        }
        if (answer.size() == 0) {
            return null;
        }
        if (answer.size() == 1) {
            return answer.get(0);
        }
        else {
            return new CompositeProcessor<E>(answer);
        }
    }

    /**
     * Creates the processor and wraps it in any necessary interceptors and error handlers
     */
    protected Processor<E> makeProcessor(ProcessorFactory<E> processFactory) throws Exception {
        Processor<E> processor = processFactory.createProcessor();
        return getErrorHandlerBuilder().createErrorHandler(processor);
    }

    public List<Processor<E>> getProcessors() {
        return processors;
    }

}
