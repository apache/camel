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
package org.apache.camel.reifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.ClaimCheckDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExecutorServiceAwareDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FinallyDefinition;
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
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OnFallbackDefinition;
import org.apache.camel.model.OptionalIdentifiedDefinition;
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
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.model.ScriptDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetExchangePatternDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.StepDefinition;
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
import org.apache.camel.processor.InterceptEndpointProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.channel.DefaultChannel;
import org.apache.camel.reifier.errorhandler.ErrorHandlerReifier;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ReifierStrategy;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ProcessorReifier<T extends ProcessorDefinition<?>> extends AbstractReifier {

    private static final Map<Class<?>, BiFunction<Route, ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>>> PROCESSORS;
    static {
        // NOTE: if adding a new class then update the initial capacity of the
        // HashMap
        Map<Class<?>, BiFunction<Route, ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>>> map = new HashMap<>(65);
        map.put(AggregateDefinition.class, AggregateReifier::new);
        map.put(BeanDefinition.class, BeanReifier::new);
        map.put(CatchDefinition.class, CatchReifier::new);
        map.put(ChoiceDefinition.class, ChoiceReifier::new);
        map.put(CircuitBreakerDefinition.class, CircuitBreakerReifier::new);
        map.put(ClaimCheckDefinition.class, ClaimCheckReifier::new);
        map.put(ConvertBodyDefinition.class, ConvertBodyReifier::new);
        map.put(DelayDefinition.class, DelayReifier::new);
        map.put(DynamicRouterDefinition.class, DynamicRouterReifier::new);
        map.put(EnrichDefinition.class, EnrichReifier::new);
        map.put(FilterDefinition.class, FilterReifier::new);
        map.put(FinallyDefinition.class, FinallyReifier::new);
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
        map.put(RoutingSlipDefinition.class, RoutingSlipReifier::new);
        map.put(SagaDefinition.class, SagaReifier::new);
        map.put(SamplingDefinition.class, SamplingReifier::new);
        map.put(ScriptDefinition.class, ScriptReifier::new);
        map.put(ServiceCallDefinition.class, ServiceCallReifier::new);
        map.put(SetBodyDefinition.class, SetBodyReifier::new);
        map.put(SetExchangePatternDefinition.class, SetExchangePatternReifier::new);
        map.put(SetHeaderDefinition.class, SetHeaderReifier::new);
        map.put(SetPropertyDefinition.class, SetPropertyReifier::new);
        map.put(SortDefinition.class, SortReifier::new);
        map.put(SplitDefinition.class, SplitReifier::new);
        map.put(StepDefinition.class, StepReifier::new);
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
        ReifierStrategy.addReifierClearer(ProcessorReifier::clearReifiers);
    }
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final T definition;

    public ProcessorReifier(Route route, T definition) {
        super(route);
        this.definition = definition;
    }

    public ProcessorReifier(CamelContext camelContext, T definition) {
        super(camelContext);
        this.definition = definition;
    }

    public static void registerReifier(Class<?> processorClass, BiFunction<Route, ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>> creator) {
        PROCESSORS.put(processorClass, creator);
    }

    public static void clearReifiers() {
        PROCESSORS.clear();
    }

    public static ProcessorReifier<? extends ProcessorDefinition<?>> reifier(Route route, ProcessorDefinition<?> definition) {
        BiFunction<Route, ProcessorDefinition<?>, ProcessorReifier<? extends ProcessorDefinition<?>>> reifier = PROCESSORS.get(definition.getClass());
        if (reifier != null) {
            return reifier.apply(route, definition);
        }
        throw new IllegalStateException("Unsupported definition: " + definition);
    }

    /**
     * Determines whether a new thread pool will be created or not.
     * <p/>
     * This is used to know if a new thread pool will be created, and therefore
     * is not shared by others, and therefore exclusive to the definition.
     *
     * @param definition the node definition which may leverage executor
     *            service.
     * @param useDefault whether to fallback and use a default thread pool, if
     *            no explicit configured
     * @return <tt>true</tt> if a new thread pool will be created,
     *         <tt>false</tt> if not
     * @see #getConfiguredExecutorService(String, ExecutorServiceAwareDefinition, boolean)
     */
    public boolean willCreateNewThreadPool(ExecutorServiceAwareDefinition<?> definition, boolean useDefault) {
        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", camelContext);

        if (definition.getExecutorService() != null) {
            // no there is a custom thread pool configured
            return false;
        } else if (definition.getExecutorServiceRef() != null) {
            ExecutorService answer = lookup(definition.getExecutorServiceRef(), ExecutorService.class);
            // if no existing thread pool, then we will have to create a new
            // thread pool
            return answer == null;
        } else if (useDefault) {
            return true;
        }

        return false;
    }

    /**
     * Will lookup and get the configured
     * {@link ExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the definition if any explicit configured executor service.</li>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile
     * ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper
     * method to ensure they support configured executor services in the same
     * coherent way.
     *
     * @param name name which is appended to the thread name, when the
     *            {@link ExecutorService} is created based
     *            on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition the node definition which may leverage executor
     *            service.
     * @param useDefault whether to fallback and use a default thread pool, if
     *            no explicit configured
     * @return the configured executor service, or <tt>null</tt> if none was
     *         configured.
     * @throws IllegalArgumentException is thrown if lookup of executor service
     *             in {@link org.apache.camel.spi.Registry} was not found
     */
    public ExecutorService getConfiguredExecutorService(String name, ExecutorServiceAwareDefinition<?> definition, boolean useDefault)
        throws IllegalArgumentException {
        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", camelContext);

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            return definition.getExecutorService();
        } else if (definition.getExecutorServiceRef() != null) {
            // lookup in registry first and use existing thread pool if exists
            ExecutorService answer = lookupExecutorServiceRef(name, definition, parseString(definition.getExecutorServiceRef()));
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef()
                                                   + " not found in registry (as an ExecutorService instance) or as a thread pool profile.");
            }
            return answer;
        } else if (useDefault) {
            return manager.newDefaultThreadPool(definition, name);
        }

        return null;
    }

    /**
     * Will lookup and get the configured
     * {@link java.util.concurrent.ScheduledExecutorService} from the given
     * definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the definition if any explicit configured executor service.</li>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile
     * ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper
     * method to ensure they support configured executor services in the same
     * coherent way.
     *
     * @param name name which is appended to the thread name, when the
     *            {@link ExecutorService} is created based
     *            on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition the node definition which may leverage executor
     *            service.
     * @param useDefault whether to fallback and use a default thread pool, if
     *            no explicit configured
     * @return the configured executor service, or <tt>null</tt> if none was
     *         configured.
     * @throws IllegalArgumentException is thrown if the found instance is not a
     *             ScheduledExecutorService type, or lookup of executor service
     *             in {@link org.apache.camel.spi.Registry} was not found
     */
    public ScheduledExecutorService getConfiguredScheduledExecutorService(String name, ExecutorServiceAwareDefinition<?> definition,
                                                                                 boolean useDefault)
        throws IllegalArgumentException {
        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", camelContext);

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            ExecutorService executorService = definition.getExecutorService();
            if (executorService instanceof ScheduledExecutorService) {
                return (ScheduledExecutorService)executorService;
            }
            throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " is not an ScheduledExecutorService instance");
        } else if (definition.getExecutorServiceRef() != null) {
            ScheduledExecutorService answer = lookupScheduledExecutorServiceRef(name, definition, definition.getExecutorServiceRef());
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef()
                                                   + " not found in registry (as an ScheduledExecutorService instance) or as a thread pool profile.");
            }
            return answer;
        } else if (useDefault) {
            return manager.newDefaultScheduledThreadPool(definition, name);
        }

        return null;
    }

    /**
     * Will lookup in {@link org.apache.camel.spi.Registry} for a
     * {@link ScheduledExecutorService} registered with the given
     * <tt>executorServiceRef</tt> name.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile
     * ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param name name which is appended to the thread name, when the
     *            {@link ExecutorService} is created based
     *            on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param source the source to use the thread pool
     * @param executorServiceRef reference name of the thread pool
     * @return the executor service, or <tt>null</tt> if none was found.
     */
    public ScheduledExecutorService lookupScheduledExecutorServiceRef(String name, Object source, String executorServiceRef) {

        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", camelContext);
        ObjectHelper.notNull(executorServiceRef, "executorServiceRef");

        // lookup in registry first and use existing thread pool if exists
        ScheduledExecutorService answer = lookup(executorServiceRef, ScheduledExecutorService.class);
        if (answer == null) {
            // then create a thread pool assuming the ref is a thread pool
            // profile id
            answer = manager.newScheduledThreadPool(source, name, executorServiceRef);
        }
        return answer;
    }

    /**
     * Will lookup in {@link org.apache.camel.spi.Registry} for a
     * {@link ExecutorService} registered with the given
     * <tt>executorServiceRef</tt> name.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     * <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     * <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile
     * ThreadPoolProfile(s)}.</li>
     * <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     *
     * @param name name which is appended to the thread name, when the
     *            {@link ExecutorService} is created based
     *            on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param source the source to use the thread pool
     * @param executorServiceRef reference name of the thread pool
     * @return the executor service, or <tt>null</tt> if none was found.
     */
    public ExecutorService lookupExecutorServiceRef(String name, Object source, String executorServiceRef) {

        ExecutorServiceManager manager = camelContext.getExecutorServiceManager();
        ObjectHelper.notNull(manager, "ExecutorServiceManager", camelContext);
        ObjectHelper.notNull(executorServiceRef, "executorServiceRef");

        // lookup in registry first and use existing thread pool if exists
        ExecutorService answer = lookup(executorServiceRef, ExecutorService.class);
        if (answer == null) {
            // then create a thread pool assuming the ref is a thread pool
            // profile id
            answer = manager.newThreadPool(source, name, executorServiceRef);
        }
        return answer;
    }

    /**
     * Is there any outputs in the given list.
     * <p/>
     * Is used for check if the route output has any real outputs (non
     * abstracts)
     *
     * @param outputs the outputs
     * @param excludeAbstract whether or not to exclude abstract outputs (e.g.
     *            skip onException etc.)
     * @return <tt>true</tt> if has outputs, otherwise <tt>false</tt> is
     *         returned
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean hasOutputs(List<ProcessorDefinition<?>> outputs, boolean excludeAbstract) {
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }
        if (!excludeAbstract) {
            return true;
        }
        for (ProcessorDefinition output : outputs) {
            if (output.isWrappingEntireOutput()) {
                // special for those as they wrap entire output, so we should
                // just check its output
                return hasOutputs(output.getOutputs(), excludeAbstract);
            }
            if (!output.isAbstract()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Override this in definition class and implement logic to create the
     * processor based on the definition model.
     */
    public abstract Processor createProcessor() throws Exception;

    /**
     * Prefer to use {#link #createChildProcessor}.
     */
    protected Processor createOutputsProcessor() throws Exception {
        Collection<ProcessorDefinition<?>> outputs = definition.getOutputs();
        return createOutputsProcessor(outputs);
    }

    /**
     * Creates the child processor (outputs) from the current definition
     *
     * @param mandatory whether or not children is mandatory (ie the definition
     *            should have outputs)
     * @return the created children, or <tt>null</tt> if definition had no
     *         output
     * @throws Exception is thrown if error creating the child or if it was
     *             mandatory and there was no output defined on definition
     */
    protected Processor createChildProcessor(boolean mandatory) throws Exception {
        Processor children = null;
        // at first use custom factory
        if (camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory() != null) {
            children = camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory().createChildProcessor(route, definition, mandatory);
        }
        // fallback to default implementation if factory did not create the
        // child
        if (children == null) {
            children = createOutputsProcessor();
        }

        if (children == null && mandatory) {
            throw new IllegalArgumentException("Definition has no children on " + definition);
        }
        return children;
    }

    public void addRoutes() throws Exception {
        Channel processor = makeProcessor();
        if (processor == null) {
            // no processor to add
            return;
        }

        // are we routing to an endpoint interceptor, if so we should not
        // add it as an event driven
        // processor as we use the producer to trigger the interceptor
        boolean endpointInterceptor = processor.getNextProcessor() instanceof InterceptEndpointProcessor;

        // only add regular processors as event driven
        if (endpointInterceptor) {
            log.debug("Endpoint interceptor should not be added as an event driven consumer route: {}", processor);
        } else {
            log.trace("Adding event driven processor: {}", processor);
            route.getEventDrivenProcessors().add(processor);
        }
    }

    /**
     * Wraps the child processor in whatever necessary interceptors and error
     * handlers
     */
    public Channel wrapProcessor(Processor processor) throws Exception {
        // don't double wrap
        if (processor instanceof Channel) {
            return (Channel)processor;
        }
        return wrapChannel(processor, null);
    }

    protected Channel wrapChannel(Processor processor, ProcessorDefinition<?> child) throws Exception {
        return wrapChannel(processor, child, definition.isInheritErrorHandler());
    }

    protected Channel wrapChannel(Processor processor, ProcessorDefinition<?> child, Boolean inheritErrorHandler) throws Exception {
        // put a channel in between this and each output to control the route
        // flow logic
        DefaultChannel channel = new DefaultChannel(camelContext);

        // add interceptor strategies to the channel must be in this order:
        // camel context, route context, local
        List<InterceptStrategy> interceptors = new ArrayList<>();
        interceptors.addAll(camelContext.adapt(ExtendedCamelContext.class).getInterceptStrategies());
        interceptors.addAll(route.getInterceptStrategies());
        interceptors.addAll(definition.getInterceptStrategies());

        // force the creation of an id
        RouteDefinitionHelper.forceAssignIds(camelContext, definition);

        // fix parent/child relationship. This will be the case of the routes
        // has been
        // defined using XML DSL or end user may have manually assembled a route
        // from the model.
        // Background note: parent/child relationship is assembled on-the-fly
        // when using Java DSL (fluent builders)
        // where as when using XML DSL (JAXB) then it fixed after, but if people
        // are using custom interceptors
        // then we need to fix the parent/child relationship beforehand, and
        // thus we can do it here
        // ideally we need the design time route -> runtime route to be a
        // 2-phase pass (scheduled work for Camel 3.0)
        if (child != null && definition != child) {
            child.setParent(definition);
        }

        // set the child before init the channel
        RouteDefinition route = ProcessorDefinitionHelper.getRoute(definition);
        boolean first = false;
        if (route != null && !route.getOutputs().isEmpty()) {
            first = route.getOutputs().get(0) == definition;
        }
        // initialize the channel
        channel.initChannel(this.route, definition, child, interceptors, processor, route, first);

        boolean wrap = false;
        // set the error handler, must be done after init as we can set the
        // error handler as first in the chain
        if (definition instanceof TryDefinition || definition instanceof CatchDefinition || definition instanceof FinallyDefinition) {
            // do not use error handler for try .. catch .. finally blocks as it
            // will handle errors itself
            log.trace("{} is part of doTry .. doCatch .. doFinally so no error handler is applied", definition);
        } else if (ProcessorDefinitionHelper.isParentOfType(TryDefinition.class, definition, true)
                   || ProcessorDefinitionHelper.isParentOfType(CatchDefinition.class, definition, true)
                   || ProcessorDefinitionHelper.isParentOfType(FinallyDefinition.class, definition, true)) {
            // do not use error handler for try .. catch .. finally blocks as it
            // will handle errors itself
            // by checking that any of our parent(s) is not a try .. catch or
            // finally type
            log.trace("{} is part of doTry .. doCatch .. doFinally so no error handler is applied", definition);
        } else if (definition instanceof OnExceptionDefinition || ProcessorDefinitionHelper.isParentOfType(OnExceptionDefinition.class, definition, true)) {
            log.trace("{} is part of OnException so no error handler is applied", definition);
            // do not use error handler for onExceptions blocks as it will
            // handle errors itself
        } else if (definition instanceof CircuitBreakerDefinition || ProcessorDefinitionHelper.isParentOfType(CircuitBreakerDefinition.class, definition, true)) {
            // do not use error handler for circuit breaker
            // however if inherit error handler is enabled, we need to wrap an error handler on the parent
            if (inheritErrorHandler != null && inheritErrorHandler && child == null) {
                // only wrap the parent (not the children of the circuit breaker)
                wrap = true;
            } else {
                log.trace("{} is part of CircuitBreaker so no error handler is applied", definition);
            }
        } else if (definition instanceof MulticastDefinition) {
            // do not use error handler for multicast as it offers fine grained
            // error handlers for its outputs
            // however if share unit of work is enabled, we need to wrap an
            // error handler on the multicast parent
            MulticastDefinition def = (MulticastDefinition)definition;
            boolean isShareUnitOfWork = parseBoolean(def.getShareUnitOfWork(), false);
            if (isShareUnitOfWork && child == null) {
                // only wrap the parent (not the children of the multicast)
                wrap = true;
            } else {
                log.trace("{} is part of multicast which have special error handling so no error handler is applied", definition);
            }
        } else {
            // use error handler by default or if configured to do so
            wrap = true;
        }
        if (wrap) {
            wrapChannelInErrorHandler(channel, inheritErrorHandler);
        }

        // do post init at the end
        channel.postInitChannel();
        log.trace("{} wrapped in Channel: {}", definition, channel);

        return channel;
    }

    /**
     * Wraps the given channel in error handler (if error handler is inherited)
     *
     * @param channel the channel
     * @param inheritErrorHandler whether to inherit error handler
     * @throws Exception can be thrown if failed to create error handler builder
     */
    private void wrapChannelInErrorHandler(DefaultChannel channel, Boolean inheritErrorHandler) throws Exception {
        if (inheritErrorHandler == null || inheritErrorHandler) {
            log.trace("{} is configured to inheritErrorHandler", definition);
            Processor output = channel.getOutput();
            Processor errorHandler = wrapInErrorHandler(output, true);
            // set error handler on channel
            channel.setErrorHandler(errorHandler);
        } else {
            log.debug("{} is configured to not inheritErrorHandler.", definition);
        }
    }

    /**
     * Wraps the given output in an error handler
     *
     * @param output the output
     * @param longLived if the processor is longLived or not
     * @return the output wrapped with the error handler
     * @throws Exception can be thrown if failed to create error handler builder
     */
    protected Processor wrapInErrorHandler(Processor output, boolean longLived) throws Exception {
        ErrorHandlerFactory builder = route.getErrorHandlerFactory();

        // create error handler
        Processor errorHandler = ErrorHandlerReifier.reifier(route, builder).createErrorHandler(output);

        if (longLived) {
            // invoke lifecycles so we can manage this error handler builder
            for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
                strategy.onErrorHandlerAdd(route, errorHandler, builder);
            }
        }

        return errorHandler;
    }

    /**
     * Creates a new instance of some kind of composite processor which defaults
     * to using a {@link Pipeline} but derived classes could change the
     * behaviour
     */
    protected Processor createCompositeProcessor(List<Processor> list) throws Exception {
        return Pipeline.newInstance(camelContext, list);
    }

    protected Processor createOutputsProcessor(Collection<ProcessorDefinition<?>> outputs) throws Exception {
        List<Processor> list = new ArrayList<>();
        for (ProcessorDefinition<?> output : outputs) {

            // allow any custom logic before we create the processor
            reifier(route, output).preCreateProcessor();

            Processor processor = createProcessor(output);

            // inject id
            if (processor instanceof IdAware) {
                String id = getId(output);
                ((IdAware)processor).setId(id);
            }
            if (processor instanceof RouteIdAware) {
                ((RouteIdAware)processor).setRouteId(route.getRouteId());
            }

            if (output instanceof Channel && processor == null) {
                continue;
            }

            Processor channel = wrapChannel(processor, output);
            list.add(channel);
        }

        // if more than one output wrap than in a composite processor else just
        // keep it as is
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

    protected Processor createProcessor(ProcessorDefinition<?> output) throws Exception {
        Processor processor = null;
        // at first use custom factory
        if (camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory() != null) {
            processor = camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory().createProcessor(route, output);
        }
        // fallback to default implementation if factory did not create the
        // processor
        if (processor == null) {
            processor = reifier(route, output).createProcessor();
        }
        return processor;
    }

    /**
     * Creates the processor and wraps it in any necessary interceptors and
     * error handlers
     */
    protected Channel makeProcessor() throws Exception {
        Processor processor = null;

        // allow any custom logic before we create the processor
        preCreateProcessor();

        // at first use custom factory
        if (camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory() != null) {
            processor = camelContext.adapt(ExtendedCamelContext.class).getProcessorFactory().createProcessor(route, definition);
        }
        // fallback to default implementation if factory did not create the
        // processor
        if (processor == null) {
            processor = createProcessor();
        }

        // inject id
        if (processor instanceof IdAware) {
            String id = getId(definition);
            ((IdAware)processor).setId(id);
        }
        if (processor instanceof RouteIdAware) {
            ((RouteIdAware)processor).setRouteId(route.getRouteId());
        }

        if (processor == null) {
            // no processor to make
            return null;
        }
        return wrapProcessor(processor);
    }

    /**
     * Strategy to execute any custom logic before the {@link Processor} is
     * created.
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

    protected String getId(OptionalIdentifiedDefinition<?> def) {
        return def.idOrCreate(camelContext.adapt(ExtendedCamelContext.class).getNodeIdFactory());
    }

}
