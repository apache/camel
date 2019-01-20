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
package org.apache.camel.reifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.Channel;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ClaimCheckDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.HystrixDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.InOnlyDefinition;
import org.apache.camel.model.InOutDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.InterceptFromDefinition;
import org.apache.camel.model.InterceptSendToEndpointDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.ModelChannel;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OnFallbackDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.PolicyDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemoveHeadersDefinition;
import org.apache.camel.model.RemovePropertiesDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.model.ScriptDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetExchangePatternDefinition;
import org.apache.camel.model.SetFaultBodyDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.StopDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.WhenSkipSendToEndpointDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.cloud.ServiceCallDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.interceptor.DefaultChannel;
import org.apache.camel.processor.interceptor.HandleFault;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProcessorReifier<T extends ProcessorDefinition<?>> {

    private static final Map<Class<?>, Function<ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>>> PROCESSORS;
    static {
        Map<Class<?>, Function<ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>>> map = new HashMap<>();
        map.put(AggregateDefinition.class, AggregateReifier::new);
        map.put(BeanDefinition.class, BeanReifier::new);
        map.put(CatchDefinition.class, CatchReifier::new);
        map.put(ChoiceDefinition.class, ChoiceReifier::new);
        map.put(ClaimCheckDefinition.class, ClaimCheckReifier::new);
        map.put(ConvertBodyDefinition.class, ConvertBodyReifier::new);
        map.put(DelayDefinition.class, DelayReifier::new);
        map.put(DynamicRouterDefinition.class, DynamicRouterReifier::new);
        map.put(EnrichDefinition.class, EnrichReifier::new);
        map.put(FilterDefinition.class, FilterReifier::new);
        map.put(FinallyDefinition.class, FinallyReifier::new);
        map.put(HystrixDefinition.class, HystrixReifier::new);
        map.put(IdempotentConsumerDefinition.class, IdempotentConsumerReifier::new);
        map.put(InOnlyDefinition.class, SendReifier::new);
        map.put(InOutDefinition.class, SendReifier::new);
        map.put(InterceptDefinition.class, InterceptReifier::new);
        map.put(InterceptFromDefinition.class, InterceptFromReifier::new);
        map.put(InterceptSendToEndpointDefinition.class, InterceptSendToEndpointReifier::new);
        map.put(LoadBalanceDefinition.class, LoadBalanceReifier::new);
        map.put(LogDefinition.class, LogReifier::new);
        map.put(LoopDefinition.class, LoopReifier::new);
        map.put(MarshalDefinition.class, MarshalReifier::new);
        map.put(MulticastDefinition.class, MulticastReifier::new);
        map.put(OnCompletionDefinition.class, OnCompletionReifier::new);
        map.put(OnExceptionDefinition.class, OnExceptionReifier::new);
        map.put(OnFallbackDefinition.class, OnFallbackReifier::new);
        map.put(OtherwiseDefinition.class, OtherwiseReifier::new);
        map.put(PipelineDefinition.class, PipelineReifier::new);
        map.put(PolicyDefinition.class, PolicyReifier::new);
        map.put(PollEnrichDefinition.class, PollEnrichReifier::new);
        map.put(ProcessDefinition.class, ProcessReifier::new);
        map.put(RecipientListDefinition.class, RecipientListReifier::new);
        map.put(RemoveHeaderDefinition.class, RemoveHeaderReifier::new);
        map.put(RemoveHeadersDefinition.class, RemoveHeadersReifier::new);
        map.put(RemovePropertiesDefinition.class, RemovePropertiesReifier::new);
        map.put(RemovePropertyDefinition.class, RemovePropertyReifier::new);
        map.put(ResequenceDefinition.class, ResequenceReifier::new);
        map.put(RollbackDefinition.class, RollbackReifier::new);
        map.put(RouteDefinition.class, RouteReifier::new);
        map.put(RoutingSlipDefinition.class, RoutingSlipReifier::new);
        map.put(SagaDefinition.class, SagaReifier::new);
        map.put(SamplingDefinition.class, SamplingReifier::new);
        map.put(ScriptDefinition.class, ScriptReifier::new);
        map.put(ServiceCallDefinition.class, ServiceCallReifier::new);
        map.put(SetBodyDefinition.class, SetBodyReifier::new);
        map.put(SetExchangePatternDefinition.class, SetExchangePatternReifier::new);
        map.put(SetFaultBodyDefinition.class, SetFaultBodyReifier::new);
        map.put(SetHeaderDefinition.class, SetHeaderReifier::new);
        map.put(SetPropertyDefinition.class, SetPropertyReifier::new);
        map.put(SortDefinition.class, SortReifier::new);
        map.put(SplitDefinition.class, SplitReifier::new);
        map.put(StopDefinition.class, StopReifier::new);
        map.put(ThreadsDefinition.class, ThreadsReifier::new);
        map.put(ThrottleDefinition.class, ThrottleReifier::new);
        map.put(ThrowExceptionDefinition.class, ThrowExceptionReifier::new);
        map.put(ToDefinition.class, SendReifier::new);
        map.put(ToDynamicDefinition.class, ToDynamicReifier::new);
        map.put(TransactedDefinition.class, TransactedReifier::new);
        map.put(TransformDefinition.class, TransformReifier::new);
        map.put(TryDefinition.class, TryReifier::new);
        map.put(UnmarshalDefinition.class, UnmarshalReifier::new);
        map.put(ValidateDefinition.class, ValidateReifier::new);
        map.put(WireTapDefinition.class, WireTapReifier::new);
        map.put(WhenSkipSendToEndpointDefinition.class, WhenSkipSendToEndpointReifier::new);
        map.put(WhenDefinition.class, WhenReifier::new);
        PROCESSORS = map;
    }
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final T definition;
    
    public ProcessorReifier(T definition) {
        this.definition = definition;
    }

    public static ProcessorReifier<? extends ProcessorDefinition<?>> reifier(ProcessorDefinition<?> definition) {
        Function<ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>> reifier = PROCESSORS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    /**
     * Override this in definition class and implement logic to create the processor
     * based on the definition model.
     */
    public abstract Processor createProcessor(RouteContext routeContext) throws Exception;

    /**
     * Prefer to use {#link #createChildProcessor}.
     */
    protected Processor createOutputsProcessor(RouteContext routeContext) throws Exception {
        Collection<ProcessorDefinition<?>> outputs = definition.getOutputs();
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
    protected Processor createChildProcessor(RouteContext routeContext, boolean mandatory) throws Exception {
        Processor children = null;
        // at first use custom factory
        if (routeContext.getCamelContext().getProcessorFactory() != null) {
            children = routeContext.getCamelContext().getProcessorFactory().createChildProcessor(routeContext, definition, mandatory);
        }
        // fallback to default implementation if factory did not create the child
        if (children == null) {
            children = createOutputsProcessor(routeContext);
        }

        if (children == null && mandatory) {
            throw new IllegalArgumentException("Definition has no children on " + definition);
        }
        return children;
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Channel processor = makeProcessor(routeContext);
        if (processor == null) {
            // no processor to add
            return;
        }

        if (!routeContext.isRouteAdded()) {
            // are we routing to an endpoint interceptor, if so we should not add it as an event driven
            // processor as we use the producer to trigger the interceptor
            boolean endpointInterceptor = processor.getNextProcessor() instanceof InterceptEndpointProcessor;

            // only add regular processors as event driven
            if (endpointInterceptor) {
                log.debug("Endpoint interceptor should not be added as an event driven consumer route: {}", processor);
            } else {
                log.trace("Adding event driven processor: {}", processor);
                routeContext.addEventDrivenProcessor(processor);
            }
        }
    }

    /**
     * Wraps the child processor in whatever necessary interceptors and error handlers
     */
    public Channel wrapProcessor(RouteContext routeContext, Processor processor) throws Exception {
        // dont double wrap
        if (processor instanceof Channel) {
            return (Channel) processor;
        }
        return wrapChannel(routeContext, processor, null);
    }

    protected Channel wrapChannel(RouteContext routeContext, Processor processor, ProcessorDefinition<?> child) throws Exception {
        return wrapChannel(routeContext, processor, child, definition.isInheritErrorHandler());
    }

    protected Channel wrapChannel(RouteContext routeContext, Processor processor, ProcessorDefinition<?> child, Boolean inheritErrorHandler) throws Exception {
        // put a channel in between this and each output to control the route flow logic
        ModelChannel channel = createChannel(routeContext);
        channel.setNextProcessor(processor);

        // add interceptor strategies to the channel must be in this order: camel context, route context, local
        addInterceptStrategies(routeContext, channel, routeContext.getCamelContext().getInterceptStrategies());
        addInterceptStrategies(routeContext, channel, routeContext.getInterceptStrategies());
        addInterceptStrategies(routeContext, channel, definition.getInterceptStrategies());

        // set the child before init the channel
        channel.setChildDefinition(child);
        channel.initChannel(definition, routeContext);

        // set the error handler, must be done after init as we can set the error handler as first in the chain
        if (definition instanceof TryDefinition || definition instanceof CatchDefinition || definition instanceof FinallyDefinition) {
            // do not use error handler for try .. catch .. finally blocks as it will handle errors itself
            log.trace("{} is part of doTry .. doCatch .. doFinally so no error handler is applied", definition);
        } else if (ProcessorDefinitionHelper.isParentOfType(TryDefinition.class, definition, true)
                || ProcessorDefinitionHelper.isParentOfType(CatchDefinition.class, definition, true)
                || ProcessorDefinitionHelper.isParentOfType(FinallyDefinition.class, definition, true)) {
            // do not use error handler for try .. catch .. finally blocks as it will handle errors itself
            // by checking that any of our parent(s) is not a try .. catch or finally type
            log.trace("{} is part of doTry .. doCatch .. doFinally so no error handler is applied", definition);
        } else if (definition instanceof OnExceptionDefinition || ProcessorDefinitionHelper.isParentOfType(OnExceptionDefinition.class, definition, true)) {
            log.trace("{} is part of OnException so no error handler is applied", definition);
            // do not use error handler for onExceptions blocks as it will handle errors itself
        } else if (definition instanceof HystrixDefinition || ProcessorDefinitionHelper.isParentOfType(HystrixDefinition.class, definition, true)) {
            // do not use error handler for hystrix as it offers circuit breaking with fallback for its outputs
            // however if inherit error handler is enabled, we need to wrap an error handler on the hystrix parent
            if (inheritErrorHandler != null && inheritErrorHandler && child == null) {
                // only wrap the parent (not the children of the hystrix)
                wrapChannelInErrorHandler(channel, routeContext, inheritErrorHandler);
            } else {
                log.trace("{} is part of HystrixCircuitBreaker so no error handler is applied", definition);
            }
        } else if (definition instanceof MulticastDefinition) {
            // do not use error handler for multicast as it offers fine grained error handlers for its outputs
            // however if share unit of work is enabled, we need to wrap an error handler on the multicast parent
            MulticastDefinition def = (MulticastDefinition) definition;
            boolean isShareUnitOfWork = def.getShareUnitOfWork() != null && def.getShareUnitOfWork();
            if (isShareUnitOfWork && child == null) {
                // only wrap the parent (not the children of the multicast)
                wrapChannelInErrorHandler(channel, routeContext, inheritErrorHandler);
            } else {
                log.trace("{} is part of multicast which have special error handling so no error handler is applied", definition);
            }
        } else {
            // use error handler by default or if configured to do so
            wrapChannelInErrorHandler(channel, routeContext, inheritErrorHandler);
        }

        // do post init at the end
        channel.postInitChannel(definition, routeContext);
        log.trace("{} wrapped in Channel: {}", definition, channel);

        return channel;
    }

    /**
     * Wraps the given channel in error handler (if error handler is inherited)
     *
     * @param channel             the channel
     * @param routeContext        the route context
     * @param inheritErrorHandler whether to inherit error handler
     * @throws Exception can be thrown if failed to create error handler builder
     */
    private void wrapChannelInErrorHandler(Channel channel, RouteContext routeContext, Boolean inheritErrorHandler) throws Exception {
        if (inheritErrorHandler == null || inheritErrorHandler) {
            log.trace("{} is configured to inheritErrorHandler", definition);
            Processor output = channel.getOutput();
            Processor errorHandler = wrapInErrorHandler(routeContext, output);
            // set error handler on channel
            channel.setErrorHandler(errorHandler);
        } else {
            log.debug("{} is configured to not inheritErrorHandler.", definition);
        }
    }

    /**
     * Wraps the given output in an error handler
     *
     * @param routeContext the route context
     * @param output the output
     * @return the output wrapped with the error handler
     * @throws Exception can be thrown if failed to create error handler builder
     */
    protected Processor wrapInErrorHandler(RouteContext routeContext, Processor output) throws Exception {
        ErrorHandlerFactory builder = ((RouteDefinition) routeContext.getRoute()).getErrorHandlerBuilder();
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
            if (!routeContext.isHandleFault() && strategy instanceof HandleFault) {
                // handle fault is disabled so we should not add it
                continue;
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
        return Pipeline.newInstance(routeContext.getCamelContext(), list);
    }

    /**
     * Creates a new instance of the {@link Channel}.
     */
    protected ModelChannel createChannel(RouteContext routeContext) throws Exception {
        return new DefaultChannel();
    }

    protected Processor createOutputsProcessor(RouteContext routeContext, Collection<ProcessorDefinition<?>> outputs) throws Exception {
        // We will save list of actions to restore the outputs back to the original state.
        Runnable propertyPlaceholdersChangeReverter = ProcessorDefinitionHelper.createPropertyPlaceholdersChangeReverter();
        try {
            return createOutputsProcessorImpl(routeContext, outputs);
        } finally {
            propertyPlaceholdersChangeReverter.run();
        }
    }

    protected Processor createOutputsProcessorImpl(RouteContext routeContext, Collection<ProcessorDefinition<?>> outputs) throws Exception {
        List<Processor> list = new ArrayList<>();
        for (ProcessorDefinition<?> output : outputs) {

            // allow any custom logic before we create the processor
            reifier(output).preCreateProcessor();

            // resolve properties before we create the processor
            ProcessorDefinitionHelper.resolvePropertyPlaceholders(routeContext.getCamelContext(), output);

            // resolve constant fields (eg Exchange.FILE_NAME)
            ProcessorDefinitionHelper.resolveKnownConstantFields(output);

            // also resolve properties and constant fields on embedded expressions
            ProcessorDefinition<?> me = (ProcessorDefinition<?>) output;
            if (me instanceof ExpressionNode) {
                ExpressionNode exp = (ExpressionNode) me;
                ExpressionDefinition expressionDefinition = exp.getExpression();
                if (expressionDefinition != null) {
                    // resolve properties before we create the processor
                    ProcessorDefinitionHelper.resolvePropertyPlaceholders(routeContext.getCamelContext(), expressionDefinition);

                    // resolve constant fields (eg Exchange.FILE_NAME)
                    ProcessorDefinitionHelper.resolveKnownConstantFields(expressionDefinition);
                }
            }

            Processor processor = createProcessor(routeContext, output);

            // inject id
            if (processor instanceof IdAware) {
                String id = output.idOrCreate(routeContext.getCamelContext().getNodeIdFactory());
                ((IdAware) processor).setId(id);
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

    protected Processor createProcessor(RouteContext routeContext, ProcessorDefinition<?> output) throws Exception {
        Processor processor = null;
        // at first use custom factory
        if (routeContext.getCamelContext().getProcessorFactory() != null) {
            processor = routeContext.getCamelContext().getProcessorFactory().createProcessor(routeContext, output);
        }
        // fallback to default implementation if factory did not create the processor
        if (processor == null) {
            processor = reifier(output).createProcessor(routeContext);
        }
        return processor;
    }

    /**
     * Creates the processor and wraps it in any necessary interceptors and error handlers
     */
    protected Channel makeProcessor(RouteContext routeContext) throws Exception {
        // We will save list of actions to restore the definition back to the original state.
        Runnable propertyPlaceholdersChangeReverter = ProcessorDefinitionHelper.createPropertyPlaceholdersChangeReverter();
        try {
            return makeProcessorImpl(routeContext);
        } finally {
            // Lets restore
            propertyPlaceholdersChangeReverter.run();
        }
    }

    private Channel makeProcessorImpl(RouteContext routeContext) throws Exception {
        Processor processor = null;

        // allow any custom logic before we create the processor
        preCreateProcessor();

        // resolve properties before we create the processor
        ProcessorDefinitionHelper.resolvePropertyPlaceholders(routeContext.getCamelContext(), definition);

        // resolve constant fields (eg Exchange.FILE_NAME)
        ProcessorDefinitionHelper.resolveKnownConstantFields(definition);

        // also resolve properties and constant fields on embedded expressions
        ProcessorDefinition<?> me = (ProcessorDefinition<?>) definition;
        if (me instanceof ExpressionNode) {
            ExpressionNode exp = (ExpressionNode) me;
            ExpressionDefinition expressionDefinition = exp.getExpression();
            if (expressionDefinition != null) {
                // resolve properties before we create the processor
                ProcessorDefinitionHelper.resolvePropertyPlaceholders(routeContext.getCamelContext(), expressionDefinition);

                // resolve constant fields (eg Exchange.FILE_NAME)
                ProcessorDefinitionHelper.resolveKnownConstantFields(expressionDefinition);
            }
        }

        // at first use custom factory
        if (routeContext.getCamelContext().getProcessorFactory() != null) {
            processor = routeContext.getCamelContext().getProcessorFactory().createProcessor(routeContext, definition);
        }
        // fallback to default implementation if factory did not create the processor
        if (processor == null) {
            processor = createProcessor(routeContext);
        }

        // inject id
        if (processor instanceof IdAware) {
            String id = definition.idOrCreate(routeContext.getCamelContext().getNodeIdFactory());
            ((IdAware) processor).setId(id);
        }

        if (processor == null) {
            // no processor to make
            return null;
        }
        return wrapProcessor(routeContext, processor);
    }

    /**
     * Strategy to execute any custom logic before the {@link Processor} is created.
     */
    protected void preCreateProcessor() {
        definition.preCreateProcessor();
    }

    /**
     * Strategy for children to do any custom configuration
     *
     * @param output the child to be added as output to this
     */
    public void configureChild(ProcessorDefinition<?> output) {
        // noop
    }


}
