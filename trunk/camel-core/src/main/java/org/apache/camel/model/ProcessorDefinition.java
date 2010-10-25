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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Channel;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.DataFormatClause;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.ProcessorBuilder;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.processor.DefaultChannel;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.interceptor.Delayer;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.processor.interceptor.StreamCaching;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.builder.Builder.body;

/**
 * Base class for processor types that most XML types extend.
 *
 * @version $Revision$
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public abstract class ProcessorDefinition<Type extends ProcessorDefinition<Type>> extends OptionalIdentifiedDefinition implements Block {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected ErrorHandlerBuilder errorHandlerBuilder;
    protected String errorHandlerRef;
    protected Boolean inheritErrorHandler;
    private NodeFactory nodeFactory;
    private final LinkedList<Block> blocks = new LinkedList<Block>();
    private ProcessorDefinition<?> parent;
    private final List<InterceptStrategy> interceptStrategies = new ArrayList<InterceptStrategy>();

    // else to use an optional attribute in JAXB2
    public abstract List<ProcessorDefinition> getOutputs();

    /**
     * Whether this model is abstract or not.
     * <p/>
     * An abstract model is something that is used for configuring cross cutting concerns such as
     * error handling, transaction policies, interceptors etc.
     * <p/>
     * Regular definitions is what is part of the route, such as ToDefinition, WireTapDefinition and the likes.
     * <p/>
     * Will by default return <tt>false</tt> to indicate regular definition, so all the abstract definitions
     * must override this method and return <tt>true</tt> instead.
     * <p/>
     * This information is used in camel-spring to let Camel work a bit on the model provided by JAXB from the
     * Spring XML file. This is needed to handle those cross cutting concerns properly. The Java DSL does not
     * have this issue as it can work this out directly using the fluent builder methods.
     *
     * @return <tt>true</tt> for abstract, otherwise <tt>false</tt> for regular.
     */
    public boolean isAbstract() {
        return false;
    }

    /**
     * Override this in definition class and implement logic to create the processor
     * based on the definition model.
     */
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        throw new UnsupportedOperationException("Not implemented yet for class: " + getClass().getName());
    }

    /**
     * Prefer to use {#link #createChildProcessor}.
     */
    public Processor createOutputsProcessor(RouteContext routeContext) throws Exception {
        Collection<ProcessorDefinition> outputs = getOutputs();
        return createOutputsProcessor(routeContext, outputs);
    }

    /**
     * Creates the child processor (outputs) from the current definition
     *
     * @param routeContext   the route context
     * @param mandatory      whether or not children is mandatory (ie the definition should have outputs)
     * @return the created children, or <tt>null</tt> if definition had no output
     * @throws Exception is thrown if error creating the child or if it was mandatory and there was no output defined on definition
     */
    public Processor createChildProcessor(RouteContext routeContext, boolean mandatory) throws Exception {
        Processor children = null;
        // at first use custom factory
        if (routeContext.getCamelContext().getProcessorFactory() != null) {
            children = routeContext.getCamelContext().getProcessorFactory().createChildProcessor(routeContext, this, mandatory);
        }
        // fallback to default implementation if factory did not create the child
        if (children == null) {
            children = routeContext.createProcessor(this);
        }

        if (children == null && mandatory) {
            throw new IllegalArgumentException("Definition has no children on " + this);
        }
        return children;
    }

    public void addOutput(ProcessorDefinition output) {
        output.setParent(this);
        configureChild(output);
        if (blocks.isEmpty()) {
            getOutputs().add(output);
        } else {
            Block block = blocks.getLast();
            block.addOutput(output);
        }
    }

    public void clearOutput() {
        getOutputs().clear();
        blocks.clear();
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Processor processor = makeProcessor(routeContext);
        if (processor == null) {
            // no processor to add
            return;
        }

        if (!routeContext.isRouteAdded()) {
            boolean endpointInterceptor = false;

            // are we routing to an endpoint interceptor, if so we should not add it as an event driven
            // processor as we use the producer to trigger the interceptor
            if (processor instanceof Channel) {
                Channel channel = (Channel) processor;
                Processor next = channel.getNextProcessor();
                if (next instanceof InterceptEndpointProcessor) {
                    endpointInterceptor = true;
                }
            }

            // only add regular processors as event driven
            if (endpointInterceptor) {
                if (log.isDebugEnabled()) {
                    log.debug("Endpoint interceptor should not be added as an event driven consumer route: " + processor);
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Adding event driven processor: " + processor);
                }
                routeContext.addEventDrivenProcessor(processor);
            }

        }
    }

    /**
     * Wraps the child processor in whatever necessary interceptors and error handlers
     */
    public Processor wrapProcessor(RouteContext routeContext, Processor processor) throws Exception {
        // dont double wrap
        if (processor instanceof Channel) {
            return processor;
        }
        return wrapChannel(routeContext, processor, null);
    }

    protected Processor wrapChannel(RouteContext routeContext, Processor processor, ProcessorDefinition child) throws Exception {
        // put a channel in between this and each output to control the route flow logic
        Channel channel = createChannel(routeContext);
        channel.setNextProcessor(processor);

        // add interceptor strategies to the channel must be in this order: camel context, route context, local
        addInterceptStrategies(routeContext, channel, routeContext.getCamelContext().getInterceptStrategies());
        addInterceptStrategies(routeContext, channel, routeContext.getInterceptStrategies());
        addInterceptStrategies(routeContext, channel, this.getInterceptStrategies());

        // must do this ugly cast to avoid compiler error on AIX/HP-UX
        ProcessorDefinition defn = (ProcessorDefinition) this;

        // set the child before init the channel
        channel.setChildDefinition(child);
        channel.initChannel(defn, routeContext);

        // set the error handler, must be done after init as we can set the error handler as first in the chain
        if (defn instanceof TryDefinition || defn instanceof CatchDefinition || defn instanceof FinallyDefinition) {
            // do not use error handler for try .. catch .. finally blocks as it will handle errors itself
        } else if (ProcessorDefinitionHelper.isParentOfType(TryDefinition.class, defn, true)
                || ProcessorDefinitionHelper.isParentOfType(CatchDefinition.class, defn, true)
                || ProcessorDefinitionHelper.isParentOfType(FinallyDefinition.class, defn, true)) {
            // do not use error handler for try .. catch .. finally blocks as it will handle errors itself
            // by checking that any of our parent(s) is not a try .. catch or finally type
        } else if (defn instanceof MulticastDefinition || defn instanceof RecipientListDefinition) {
            // do not use error handler for multicast or recipient list based as it offers fine grained error handlers for its outputs
        } else {
            // use error handler by default or if configured to do so
            if (isInheritErrorHandler() == null || isInheritErrorHandler()) {
                if (log.isTraceEnabled()) {
                    log.trace(defn + " is configured to inheritErrorHandler");
                }
                // only add error handler if we are configured to do so
                // regular definition so add the error handler
                Processor output = channel.getOutput();
                Processor errorHandler = wrapInErrorHandler(routeContext, getErrorHandlerBuilder(), output);
                // set error handler on channel
                channel.setErrorHandler(errorHandler);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(defn + " is configured to not inheritErrorHandler.");
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace(defn + " wrapped in Channel: " + channel);
        }
        return channel;
    }

    /**
     * Wraps the given output in an error handler
     *
     * @param routeContext the route context
     * @param output the output
     * @return the output wrapped with the error handler
     * @throws Exception can be thrown if failed to create error handler builder
     */
    protected Processor wrapInErrorHandler(RouteContext routeContext, ErrorHandlerBuilder builder, Processor output) throws Exception {
        // create error handler
        Processor errorHandler = builder.createErrorHandler(routeContext, output);

        // invoke lifecycles so we can manage this error handler builder
        for (LifecycleStrategy strategy : routeContext.getCamelContext().getLifecycleStrategies()) {
            strategy.onErrorHandlerAdd(routeContext, errorHandler, builder);
        }

        return errorHandler;
    }

    /**
     * Adds the given list of interceptors to the channel.
     *
     * @param routeContext  the route context
     * @param channel       the channel to add strategies
     * @param strategies    list of strategies to add.
     */
    protected void addInterceptStrategies(RouteContext routeContext, Channel channel, List<InterceptStrategy> strategies) {
        for (InterceptStrategy strategy : strategies) {
            if (!routeContext.isStreamCaching() && strategy instanceof StreamCaching) {
                // stream cache is disabled so we should not add it
                continue;
            }
            if (!routeContext.isHandleFault() && strategy instanceof HandleFault) {
                // handle fault is disabled so we should not add it
                continue;
            }
            if (strategy instanceof Delayer) {
                if (routeContext.getDelayer() == null || routeContext.getDelayer() <= 0) {
                    // delayer is disabled so we should not add it
                    continue;
                } else {
                    // replace existing delayer as delayer have individual configuration
                    Iterator<InterceptStrategy> it = channel.getInterceptStrategies().iterator();
                    while (it.hasNext()) {
                        InterceptStrategy existing = it.next();
                        if (existing instanceof Delayer) {
                            it.remove();
                        }
                    }
                    // add the new correct delayer
                    channel.addInterceptStrategy(strategy);
                    continue;
                }
            }

            // add strategy
            channel.addInterceptStrategy(strategy);
        }
    }

    /**
     * Creates a new instance of some kind of composite processor which defaults
     * to using a {@link Pipeline} but derived classes could change the behaviour
     */
    protected Processor createCompositeProcessor(RouteContext routeContext, List<Processor> list) throws Exception {
        return new Pipeline(routeContext.getCamelContext(), list);
    }

    /**
     * Creates a new instance of the {@link Channel}.
     */
    protected Channel createChannel(RouteContext routeContext) throws Exception {
        return new DefaultChannel();
    }

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorDefinition> outputs) throws Exception {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorDefinition<?> output : outputs) {
            Processor processor = null;
            // at first use custom factory
            if (routeContext.getCamelContext().getProcessorFactory() != null) {
                processor = routeContext.getCamelContext().getProcessorFactory().createProcessor(routeContext, output);
            }
            // fallback to default implementation if factory did not create the processor
            if (processor == null) {
                processor = output.createProcessor(routeContext);
            }

            if (output instanceof Channel && processor == null) {
                continue;
            }

            Processor channel = wrapChannel(routeContext, processor, output);
            list.add(channel);
        }

        // if more than one output wrap than in a composite processor else just keep it as is
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

    /**
     * Creates the processor and wraps it in any necessary interceptors and error handlers
     */
    protected Processor makeProcessor(RouteContext routeContext) throws Exception {
        Processor processor = null;

        // resolve properties before we create the processor
        resolvePropertyPlaceholders(routeContext);

        // at first use custom factory
        if (routeContext.getCamelContext().getProcessorFactory() != null) {
            processor = routeContext.getCamelContext().getProcessorFactory().createProcessor(routeContext, this);
        }
        // fallback to default implementation if factory did not create the processor
        if (processor == null) {
            processor = createProcessor(routeContext);
        }

        if (processor == null) {
            // no processor to make
            return null;
        }
        return wrapProcessor(routeContext, processor);
    }

    /**
     * Inspects this processor definition and resolves any property placeholders from its properties.
     * <p/>
     * This implementation will check all the getter/setter pairs on this instance and for all the values
     * (which is a String type) will be property placeholder resolved.
     *
     * @param routeContext the route context
     * @throws Exception is thrown if property placeholders was used and there was an error resolving them
     * @see org.apache.camel.CamelContext#resolvePropertyPlaceholders(String)
     * @see org.apache.camel.component.properties.PropertiesComponent
     */
    protected void resolvePropertyPlaceholders(RouteContext routeContext) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Resolving property placeholders for: " + this);
        }

        // find all String getter/setter
        Map<Object, Object> properties = new HashMap<Object, Object>();
        IntrospectionSupport.getProperties(this, properties, null);

        if (!properties.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("There are " + properties.size() + " properties on: " + this);
            }

            // lookup and resolve properties for String based properties
            for (Map.Entry entry : properties.entrySet()) {
                // the name is always a String
                String name = (String) entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String) {
                    // we can only resolve String typed values
                    String text = (String) value;
                    text = routeContext.getCamelContext().resolvePropertyPlaceholders(text);
                    if (text != value) {
                        // invoke setter as the text has changed
                        IntrospectionSupport.setProperty(this, name, text);
                        if (log.isDebugEnabled()) {
                            log.debug("Changed property [" + name + "] from: " + value + " to: " + text);
                        }
                    }
                }
            }
        }
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
     * Sets the id of this node
     *
     * @param id  the id
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type id(String id) {
        if (getOutputs().isEmpty()) {
            // set id on this
            setId(id);
        } else {
            // set it on last output as this is what the user means to do
            getOutputs().get(getOutputs().size() - 1).setId(id);
        }

        return (Type) this;
    }

    /**
     * Set the route id for this route
     *
     * @param id  the route id
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type routeId(String id) {
        ProcessorDefinition def = this;

        RouteDefinition route = ProcessorDefinitionHelper.getRoute(def);
        if (route != null) {
            route.setId(id);
        }

        return (Type) this;
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
     * @param aggregationStrategy the strategy used to aggregate responses for every part
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
     * Creates a {@link Pipeline} so that the message
     * will get processed by each endpoint in turn and for request/response the
     * output of one endpoint will be the input of the next endpoint
     *
     * @return the builder
     */
    public PipelineDefinition pipeline() {
        PipelineDefinition answer = new PipelineDefinition();
        addOutput(answer);
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
     * @param poolSize the core pool size
     * @return the builder
     */
    public ThreadsDefinition threads(int poolSize) {
        ThreadsDefinition answer = threads();
        answer.setPoolSize(poolSize);
        return answer;
    }

    /**
     * Continues processing the {@link org.apache.camel.Exchange} using asynchronous routing engine.
     *
     * @param poolSize    the core pool size
     * @param maxPoolSize the maximum pool size
     * @return the builder
     */
    public ThreadsDefinition threads(int poolSize, int maxPoolSize) {
        ThreadsDefinition answer = threads();
        answer.setPoolSize(poolSize);
        answer.setMaxPoolSize(maxPoolSize);
        return answer;
    }

    /**
     * Wraps the sub route using AOP allowing you to do before and after work (AOP around).
     *
     * @return the builder
     * @deprecated (to be removed in the future)
     */
    @Deprecated
    public AOPDefinition aop() {
        AOPDefinition answer = new AOPDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * Ends the current block
     *
     * @return the builder
     */
    public ProcessorDefinition end() {
        // must do this ugly cast to avoid compiler error on AIX/HP-UX
        ProcessorDefinition defn = (ProcessorDefinition) this;
        
        // when using doTry .. doCatch .. doFinally we should always
        // end the try definition to avoid having to use 2 x end() in the route
        // this is counter intuitive for end users
        if (defn instanceof TryDefinition) {
            popBlock();
        }

        if (blocks.isEmpty()) {
            if (parent == null) {
                return this; 
            }
            return parent;
        }
        popBlock();
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
    public IdempotentConsumerDefinition idempotentConsumer(Expression messageIdExpression, IdempotentRepository<?> idempotentRepository) {
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
    public ExpressionClause<IdempotentConsumerDefinition> idempotentConsumer(IdempotentRepository<?> idempotentRepository) {
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
     * Creates a validation expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination.
     * Otherwise a {@link org.apache.camel.processor.validation.PredicateValidationException} is thrown.
     *
     * @param expression  the expression
     * @return the builder
     */
    public ValidateDefinition validate(Expression expression) {
        ValidateDefinition answer = new ValidateDefinition();
        answer.setExpression(new ExpressionDefinition(expression));
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a validation expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination.
     * Otherwise a {@link org.apache.camel.processor.validation.PredicateValidationException} is thrown.
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public ValidateDefinition validate(Predicate predicate) {
        ValidateDefinition answer = new ValidateDefinition();
        answer.setExpression(new ExpressionDefinition(predicate));
        addOutput(answer);
        return answer;
    }

    /**
     * Creates a validation expression which only if it is <tt>true</tt> then the
     * exchange is forwarded to the destination.
     * Otherwise a {@link org.apache.camel.processor.validation.PredicateValidationException} is thrown.
     *
     * @return the builder
     */
    public ExpressionClause<ValidateDefinition> validate() {
        ValidateDefinition answer = new ValidateDefinition();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
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
     * <a href="http://camel.apache.org/load-balancer.html">Load Balancer EIP:</a>
     * Creates a loadbalance
     *
     * @param loadBalancer a custom load balancer to use
     * @return  the builder
     */
    public LoadBalanceDefinition loadBalance(LoadBalancer loadBalancer) {
        LoadBalanceDefinition answer = new LoadBalanceDefinition();
        addOutput(answer);
        return answer.loadBalance(loadBalancer);
    }

    /**
     * Creates a log message to be logged at INFO level.
     *
     * @param message the log message, (you can use {@link org.apache.camel.language.simple.SimpleLanguage} syntax)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type log(String message) {
        LogDefinition answer = new LogDefinition(message);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a log message to be logged at the given level.
     *
     * @param loggingLevel the logging level to use
     * @param message the log message, (you can use {@link org.apache.camel.language.simple.SimpleLanguage} syntax)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type log(LoggingLevel loggingLevel, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Creates a log message to be logged at the given level and name.
     *
     * @param loggingLevel the logging level to use
     * @param logName the log name to use
     * @param message the log message, (you can use {@link org.apache.camel.language.simple.SimpleLanguage} syntax)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type log(LoggingLevel loggingLevel, String logName, String message) {
        LogDefinition answer = new LogDefinition(message);
        answer.setLoggingLevel(loggingLevel);
        answer.setLogName(logName);
        addOutput(answer);
        return (Type) this;
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
    public TryDefinition doTry() {
        TryDefinition answer = new TryDefinition();
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients.
     * <p/>
     * Will use comma as default delimiter.
     *
     * @param recipients expression to decide the destinations
     * @return the builder
     */
    public RecipientListDefinition<Type> recipientList(Expression recipients) {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<Type>(recipients);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @param recipients expression to decide the destinations
     * @param delimiter  a custom delimiter to use
     * @return the builder
     */
    public RecipientListDefinition<Type> recipientList(Expression recipients, String delimiter) {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<Type>(recipients);
        answer.setDelimiter(delimiter);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/recipient-list.html">Recipient List EIP:</a>
     * Creates a dynamic recipient list allowing you to route messages to a number of dynamically specified recipients
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    public ExpressionClause<RecipientListDefinition<Type>> recipientList() {
        RecipientListDefinition<Type> answer = new RecipientListDefinition<Type>();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @param uriDelimiter  is the delimiter that will be used to split up
     *                      the list of URIs in the routing slip.
     * @return the builder
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
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
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
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @param uriDelimiter  is the delimiter that will be used to split up
     *                      the list of URIs in the routing slip.
     * @param ignoreInvalidEndpoints if this parameter is true, routingSlip will ignore the endpoints which
     *                               cannot be resolved or a producer cannot be created or started 
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type routingSlip(String header, String uriDelimiter, boolean ignoreInvalidEndpoints) {
        RoutingSlipDefinition answer = new RoutingSlipDefinition(header, uriDelimiter);
        answer.setIgnoreInvalidEndpoints(ignoreInvalidEndpoints);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param header  is the header that the {@link org.apache.camel.processor.RoutingSlip RoutingSlip}
     *                class will look in for the list of URIs to route the message to.
     * @param ignoreInvalidEndpoints if this parameter is true, routingSlip will ignore the endpoints which
     *                               cannot be resolved or a producer cannot be created or started 
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type routingSlip(String header, boolean ignoreInvalidEndpoints) {
        RoutingSlipDefinition answer = new RoutingSlipDefinition(header);
        answer.setIgnoreInvalidEndpoints(ignoreInvalidEndpoints);
        addOutput(answer);
        return (Type) this;
    }
    
    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param expression  to decide the destinations
     * @param uriDelimiter  is the delimiter that will be used to split up
     *                      the list of URIs in the routing slip.
     * 
     * @return the builder
     */
    public RoutingSlipDefinition<Type> routingSlip(Expression expression, String uriDelimiter) {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<Type>(expression, uriDelimiter);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @param expression  to decide the destinations
     * 
     * @return the builder
     */
    public RoutingSlipDefinition<Type> routingSlip(Expression expression) {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<Type>(expression);
        addOutput(answer);
        return answer;
    }
    
    /**
     * <a href="http://camel.apache.org/routing-slip.html">Routing Slip EIP:</a>
     * Creates a routing slip allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * The list of URIs will be split based on the default delimiter {@link RoutingSlipDefinition#DEFAULT_DELIMITER}
     * <p/>
     * The route slip will be evaluated <i>once</i>, use {@link #dynamicRouter()} if you need even more dynamic routing.
     *
     * @return the expression clause to configure the expression to decide the destinations
     */
    public ExpressionClause<RoutingSlipDefinition<Type>> routingSlip() {
        RoutingSlipDefinition<Type> answer = new RoutingSlipDefinition<Type>();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router EIP:</a>
     * Creates a dynamic router allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * <br/><b>Important:</b> The expression will be invoked repeatedly until it returns <tt>null</tt>, so be sure it does that,
     * otherwise it will be invoked endlessly.
     *
     * @param expression  to decide the destinations, which will be invoked repeatedly
     *                    until it evaluates <tt>null</tt> to indicate no more destinations.
     * @return the builder
     */
    public DynamicRouterDefinition<Type> dynamicRouter(Expression expression) {
        DynamicRouterDefinition<Type> answer = new DynamicRouterDefinition<Type>(expression);
        addOutput(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/dynamic-router.html">Dynamic Router EIP:</a>
     * Creates a dynamic router allowing you to route a message consecutively through a series of processing
     * steps where the sequence of steps is not known at design time and can vary for each message.
     * <p/>
     * <br/><b>Important:</b> The expression will be invoked repeatedly until it returns <tt>null</tt>, so be sure it does that,
     * otherwise it will be invoked endlessly.
     *
     * @return the expression clause to configure the expression to decide the destinations,
     * which will be invoked repeatedly until it evaluates <tt>null</tt> to indicate no more destinations.
     */
    public ExpressionClause<DynamicRouterDefinition<Type>> dynamicRouter() {
        DynamicRouterDefinition<Type> answer = new DynamicRouterDefinition<Type>();
        addOutput(answer);
        return ExpressionClause.createAndSetExpression(answer);
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a>
     * Creates a sampling throttler allowing you to extract a sample of
     * exchanges from the traffic on a route. It is configured with a sampling
     * period, during which only a single exchange is allowed to pass through.
     * All other exchanges will be stopped.
     * <p/>
     * Default period is one second.
     *
     * @return the builder
     */
    public SamplingDefinition sample() {
        return sample(1, TimeUnit.SECONDS);
    }

    /**
     * <a href="http://camel.apache.org/sampling.html">Sampling Throttler</a>
     * Creates a sampling throttler allowing you to extract a sample of exchanges
     * from the traffic through a route. It is configured with a sampling period
     * during which only a single exchange is allowed to pass through.
     * All other exchanges will be stopped.
     *
     * @param samplePeriod this is the sample interval, only one exchange is
     *            allowed through in this interval
     * @param unit this is the units for the samplePeriod e.g. Seconds
     * @return the builder
     */
    public SamplingDefinition sample(long samplePeriod, TimeUnit unit) {
        SamplingDefinition answer = new SamplingDefinition(samplePeriod, unit);
        addOutput(answer);
        return answer;
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
    public ResequenceDefinition resequence(Expression... expressions) {
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
     * @param delay  an expression to calculate the delay time in millis
     * @return the builder
     */
    public DelayDefinition delay(Expression delay) {
        DelayDefinition answer = new DelayDefinition(delay);
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
     * @param delay  the delay in millis
     * @return the builder
     */
    public DelayDefinition delay(long delay) {
        return delay(ExpressionBuilder.constantExpression(delay));
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
     * Sets the exception on the {@link org.apache.camel.Exchange}
     *
     * @param exception the exception to throw
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type throwException(Exception exception) {
        ThrowExceptionDefinition answer = new ThrowExceptionDefinition();
        answer.setException(exception);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Marks the exchange for rollback only.
     * <p/>
     * Does <b>not</b> set any exception as opposed to {@link #rollback()} methods.
     *
     * @return the builder
     * @see #rollback()
     * @see #rollback(String)
     * @see #markRollbackOnlyLast()
     */
    @SuppressWarnings("unchecked")
    public Type markRollbackOnly() {
        RollbackDefinition answer = new RollbackDefinition();
        answer.setMarkRollbackOnly(true);
        addOutput(answer);
        return (Type) this;
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
     * @see #rollback()
     * @see #rollback(String)
     * @see #markRollbackOnly()
     */
    @SuppressWarnings("unchecked")
    public Type markRollbackOnlyLast() {
        RollbackDefinition answer = new RollbackDefinition();
        answer.setMarkRollbackOnlyLast(true);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Marks the exchange for rollback only and sets an exception with a default message.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange
     * and mark it for rollback.
     *
     * @return the builder
     * @see #markRollbackOnly()
     */
    public Type rollback() {
        return rollback(null);
    }

    /**
     * Marks the exchange for rollback and sets an exception with the provided message.
     * <p/>
     * This is done by setting a {@link org.apache.camel.RollbackExchangeException} on the Exchange
     * and mark it for rollback.
     *
     * @param message an optional message used for logging purpose why the rollback was triggered
     * @return the builder
     * @see #markRollbackOnly()
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
     * @param uri  the destination
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
     * Sends messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param      executorService a custom {@link ExecutorService} to use as thread pool
     *             for sending tapped exchanges
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, ExecutorService executorService) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setExecutorService(executorService);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends messages to all its child outputs; so that each processor and
     * destination gets a copy of the original message to avoid the processors
     * interfering with each other using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param      executorServiceRef reference to lookup a custom {@link ExecutorService}
     *             to use as thread pool for sending tapped exchanges
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, String executorServiceRef) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setExecutorServiceRef(executorServiceRef);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     * <p/>
     * Will use a copy of the original Exchange which is passed in as argument
     * to the given expression
     *
     * @param uri  the destination
     * @param body expression that creates the body to send
     * @return the builder
     */
    public Type wireTap(String uri, Expression body) {
        return wireTap(uri, true, body);
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param copy whether or not use a copy of the original exchange or a new empty exchange
     * @param body expression that creates the body to send
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, boolean copy, Expression body) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setCopy(copy);
        answer.setNewExchangeExpression(body);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     * <p/>
     * Will use a copy of the original Exchange which is passed in as argument
     * to the given processor
     *
     * @param uri  the destination
     * @param processor  processor preparing the new exchange to send
     * @return the builder
     */
    public Type wireTap(String uri, Processor processor) {
        return wireTap(uri, true, processor);
    }

    /**
     * <a href="http://camel.apache.org/wiretap.html">WireTap EIP:</a>
     * Sends a new {@link org.apache.camel.Exchange} to the destination
     * using {@link ExchangePattern#InOnly}.
     *
     * @param uri  the destination
     * @param copy whether or not use a copy of the original exchange or a new empty exchange
     * @param processor  processor preparing the new exchange to send
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type wireTap(String uri, boolean copy, Processor processor) {
        WireTapDefinition answer = new WireTapDefinition();
        answer.setUri(uri);
        answer.setCopy(copy);
        answer.setNewExchangeProcessor(processor);
        addOutput(answer);
        return (Type) this;
    }

    /**
     * Pushes the given block on the stack as current block
     * @param block  the block
     */
    void pushBlock(Block block) {
        blocks.add(block);
    }

    /**
     * Pops the block off the stack as current block
     * @return the block
     */
    Block popBlock() {
        return blocks.isEmpty() ? null : blocks.removeLast();
    }

    /**
     * Stops continue routing the current {@link org.apache.camel.Exchange} and marks it as completed.
     *
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type stop() {
        StopDefinition stop = new StopDefinition();
        addOutput(stop);
        return (Type) this;
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
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
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a>
     * for catching certain exceptions and handling them.
     *
     * @param exceptions list of exceptions to catch
     * @return the exception builder to configure
     */
    public OnExceptionDefinition onException(Class... exceptions) {
        OnExceptionDefinition answer = new OnExceptionDefinition(Arrays.asList(exceptions));
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
        ExpressionClause<ProcessorDefinition<Type>> clause = 
            new ExpressionClause<ProcessorDefinition<Type>>((ProcessorDefinition<Type>) this);
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
     * Adds a processor which removes the headers on the IN message
     *
     * @param pattern  a pattern to match header names to be removed
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type removeHeaders(String pattern) {
        RemoveHeadersDefinition answer = new RemoveHeadersDefinition(pattern);
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
     * Converts the IN message body to the specified type
     *
     * @param type the type to convert to
     * @param charset the charset to use by type converters (not all converters support specifc charset)
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type convertBodyTo(Class type, String charset) {
        addOutput(new ConvertBodyDefinition(type, charset));
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
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>.
     * 
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @param aggregationStrategy   aggregation strategy to aggregate input data and additional data.
     * @return the builder
     * @see org.apache.camel.processor.Enricher
     */
    @SuppressWarnings("unchecked")
    public Type enrich(String resourceUri, AggregationStrategy aggregationStrategy) {
        addOutput(new EnrichDefinition(aggregationStrategy, resourceUri));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>.
     * <p/>
     * The difference between this and {@link #pollEnrich(String)} is that this uses a producer
     * to obatin the additional data, where as pollEnrich uses a polling consumer.
     *
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @return the builder
     * @see org.apache.camel.processor.Enricher
     */
    @SuppressWarnings("unchecked")
    public Type enrich(String resourceUri) {
        addOutput(new EnrichDefinition(resourceUri));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>.
     * <p/>
     * The difference between this and {@link #pollEnrich(String)} is that this uses a producer
     * to obatin the additional data, where as pollEnrich uses a polling consumer.
     *
     * @param resourceRef            Reference of resource endpoint for obtaining additional data.
     * @param aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @return the builder
     * @see org.apache.camel.processor.Enricher
     */
    @SuppressWarnings("unchecked")
    public Type enrichRef(String resourceRef, String aggregationStrategyRef) {
        EnrichDefinition enrich = new EnrichDefinition();
        enrich.setResourceRef(resourceRef);
        enrich.setAggregationStrategyRef(aggregationStrategyRef);
        addOutput(enrich);
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>
     * using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer
     * to obtain the additional data, where as enrich uses a producer.
     * <p/>
     * This method will block until data is avialable, use the method with timeout if you do not
     * want to risk waiting a long time before data is available from the resourceUri.
     *
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @return the builder
     * @see org.apache.camel.processor.PollEnricher
     */
    @SuppressWarnings("unchecked")
    public Type pollEnrich(String resourceUri) {
        addOutput(new PollEnrichDefinition(null, resourceUri, 0));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>
     * using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer
     * to obtain the additional data, where as enrich uses a producer.
     * <p/>
     * This method will block until data is avialable, use the method with timeout if you do not
     * want to risk waiting a long time before data is available from the resourceUri.
     *
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @param aggregationStrategy   aggregation strategy to aggregate input data and additional data.
     * @return the builder
     * @see org.apache.camel.processor.PollEnricher
     */
    @SuppressWarnings("unchecked")
    public Type pollEnrich(String resourceUri, AggregationStrategy aggregationStrategy) {
        addOutput(new PollEnrichDefinition(aggregationStrategy, resourceUri, 0));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>
     * using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer
     * to obtain the additional data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}.
     * If timeout is negative, we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt>
     * otherwise we use <tt>receive(timeout)</tt>.
     *
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @param timeout               timeout in millis to wait at most for data to be available.
     * @param aggregationStrategy   aggregation strategy to aggregate input data and additional data.
     * @return the builder
     * @see org.apache.camel.processor.PollEnricher
     */
    @SuppressWarnings("unchecked")
    public Type pollEnrich(String resourceUri, long timeout, AggregationStrategy aggregationStrategy) {
        addOutput(new PollEnrichDefinition(aggregationStrategy, resourceUri, timeout));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>
     * using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer
     * to obatin the additional data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}.
     * If timeout is negative, we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt>
     * otherwise we use <tt>receive(timeout)</tt>.
     *
     * @param resourceUri           URI of resource endpoint for obtaining additional data.
     * @param timeout               timeout in millis to wait at most for data to be available.
     * @return the builder
     * @see org.apache.camel.processor.PollEnricher
     */
    @SuppressWarnings("unchecked")
    public Type pollEnrich(String resourceUri, long timeout) {
        addOutput(new PollEnrichDefinition(null, resourceUri, timeout));
        return (Type) this;
    }

    /**
     * The <a href="http://camel.apache.org/content-enricher.html">Content Enricher EIP</a>
     * enriches an exchange with additional data obtained from a <code>resourceUri</code>
     * using a {@link org.apache.camel.PollingConsumer} to poll the endpoint.
     * <p/>
     * The difference between this and {@link #enrich(String)} is that this uses a consumer
     * to obatin the additional data, where as enrich uses a producer.
     * <p/>
     * The timeout controls which operation to use on {@link org.apache.camel.PollingConsumer}.
     * If timeout is negative, we use <tt>receive</tt>. If timeout is 0 then we use <tt>receiveNoWait</tt>
     * otherwise we use <tt>receive(timeout)</tt>.
     *
     * @param resourceRef            Reference of resource endpoint for obtaining additional data.
     * @param timeout                timeout in millis to wait at most for data to be available.
     * @param aggregationStrategyRef Reference of aggregation strategy to aggregate input data and additional data.
     * @return the builder
     * @see org.apache.camel.processor.PollEnricher
     */
    @SuppressWarnings("unchecked")
    public Type pollEnrichRef(String resourceRef, long timeout, String aggregationStrategyRef) {
        PollEnrichDefinition pollEnrich = new PollEnrichDefinition();
        pollEnrich.setResourceRef(resourceRef);
        pollEnrich.setTimeout(timeout);
        pollEnrich.setAggregationStrategyRef(aggregationStrategyRef);
        addOutput(pollEnrich);
        return (Type) this;
    }

    /**
     * Adds a onComplection {@link org.apache.camel.spi.Synchronization} hook that invoke this route as
     * a callback when the {@link org.apache.camel.Exchange} has finished being processed.
     * The hook invoke callbacks for either onComplete or onFailure.
     * <p/>
     * Will by default always trigger when the {@link org.apache.camel.Exchange} is complete
     * (either with success or failed).
     * <br/>
     * You can limit the callback to either onComplete or onFailure but invoking the nested
     * builder method.
     * <p/>
     * For onFailure the caused exception is stored as a property on the {@link org.apache.camel.Exchange}
     * with the key {@link org.apache.camel.Exchange#EXCEPTION_CAUGHT}.
     *
     * @return the builder
     */
    public OnCompletionDefinition onCompletion() {
        OnCompletionDefinition answer = new OnCompletionDefinition();
        // we must remove all existing on completion definition (as they are global)
        // and thus we are the only one as route scoped should override any global scoped
        answer.removeAllOnCompletionDefinition(this);
        popBlock();
        addOutput(answer);
        pushBlock(answer);
        return answer;
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

    /**
     * Sets whether or not to inherit the configured error handler.
     * <br/>
     * The default value is <tt>true</tt>.
     * <p/>
     * You can use this to disable using the inherited error handler for a given
     * DSL such as a load balancer where you want to use a custom error handler strategy.
     *
     * @param inheritErrorHandler whether to not to inherit the error handler for this node
     * @return the builder
     */
    @SuppressWarnings("unchecked")
    public Type inheritErrorHandler(boolean inheritErrorHandler) {
        // set on last output
        int size = getOutputs().size();
        if (size == 0) {
            // if no outputs then configure this DSL
            setInheritErrorHandler(inheritErrorHandler);
        } else {
            // configure on last output as its the intended
            ProcessorDefinition output = getOutputs().get(size - 1);
            if (output != null) {
                output.setInheritErrorHandler(inheritErrorHandler);
            }
        }
        return (Type) this;
    }

    // Properties
    // -------------------------------------------------------------------------
    @XmlTransient
    public ProcessorDefinition getParent() {
        return parent;
    }

    public void setParent(ProcessorDefinition parent) {
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

    @XmlTransient
    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptStrategies;
    }

    public void addInterceptStrategy(InterceptStrategy strategy) {
        this.interceptStrategies.add(strategy);
    }

    public Boolean isInheritErrorHandler() {
        return inheritErrorHandler;
    }

    @XmlAttribute
    public void setInheritErrorHandler(Boolean inheritErrorHandler) {
        this.inheritErrorHandler = inheritErrorHandler;
    }

    /**
     * Returns a label to describe this node such as the expression if some kind of expression node
     */
    public String getLabel() {
        return "";
    }

}
