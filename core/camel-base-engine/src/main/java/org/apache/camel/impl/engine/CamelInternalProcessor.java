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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.StreamCache;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.impl.debugger.DefaultBacklogTracerEventMessage;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.InternalProcessor;
import org.apache.camel.spi.ManagementInterceptStrategy.InstrumentationProcessor;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.PooledObjectFactory;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.SynchronizationRouteAware;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 * <li>Execute {@link UnitOfWork}</li>
 * <li>Keeping track which route currently is being routed</li>
 * <li>Execute {@link RoutePolicy}</li>
 * <li>Gather JMX performance statics</li>
 * <li>Tracing</li>
 * <li>Debugging</li>
 * <li>Message History</li>
 * <li>Stream Caching</li>
 * <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 * <p/>
 * This implementation executes this cross cutting functionality as a {@link CamelInternalProcessorAdvice} advice
 * (before and after advice) by executing the {@link CamelInternalProcessorAdvice#before(org.apache.camel.Exchange)} and
 * {@link CamelInternalProcessorAdvice#after(org.apache.camel.Exchange, Object)} callbacks in correct order during
 * routing. This reduces number of stack frames needed during routing, and reduce the number of lines in stacktraces, as
 * well makes debugging the routing engine easier for end users.
 * <p/>
 * <b>Debugging tips:</b> Camel end users whom want to debug their Camel applications with the Camel source code, then
 * make sure to read the source code of this class about the debugging tips, which you can find in the
 * {@link #process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method.
 * <p/>
 * The added advices can implement {@link Ordered} to control in which order the advices are executed.
 */
public class CamelInternalProcessor extends DelegateAsyncProcessor implements InternalProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelInternalProcessor.class);

    private static final Object[] EMPTY_STATES = new Object[0];

    final CamelContext camelContext;
    private final ReactiveExecutor reactiveExecutor;
    private final ShutdownStrategy shutdownStrategy;
    private final List<CamelInternalProcessorAdvice<?>> advices = new ArrayList<>();
    private byte statefulAdvices;
    private Object[] emptyStatefulStates;
    private PooledObjectFactory<CamelInternalTask> taskFactory;

    public CamelInternalProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.getCamelContextExtension().getReactiveExecutor();
        this.shutdownStrategy = camelContext.getShutdownStrategy();
    }

    public CamelInternalProcessor(CamelContext camelContext, Processor processor) {
        super(processor);
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.getCamelContextExtension().getReactiveExecutor();
        this.shutdownStrategy = camelContext.getShutdownStrategy();
    }

    private CamelInternalProcessor(Logger log) {
        // used for eager loading
        camelContext = null;
        reactiveExecutor = null;
        shutdownStrategy = null;
        log.trace("Loaded {}", AsyncAfterTask.class.getSimpleName());
    }

    @Override
    protected void doBuild() throws Exception {
        boolean pooled = camelContext.getCamelContextExtension().getExchangeFactory().isPooled();

        // only create pooled task factory
        if (pooled) {
            taskFactory = new CamelInternalPooledTaskFactory();
            int capacity = camelContext.getCamelContextExtension().getExchangeFactory().getCapacity();
            taskFactory.setCapacity(capacity);
            LOG.trace("Using TaskFactory: {}", taskFactory);

            // create empty array we can use for reset
            emptyStatefulStates = new Object[statefulAdvices];
        }

        ServiceHelper.buildService(taskFactory, processor);
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        ServiceHelper.stopAndShutdownServices(taskFactory, processor);
    }

    @Override
    public void addAdvice(CamelInternalProcessorAdvice<?> advice) {
        advices.add(advice);
        // ensure advices are sorted so they are in the order we want
        advices.sort(OrderedComparator.get());

        if (advice.hasState()) {
            statefulAdvices++;
        }
    }

    @Override
    public <T> T getAdvice(Class<T> type) {
        for (CamelInternalProcessorAdvice<?> task : advices) {
            Object advice = unwrap(task);
            if (type.isInstance(advice)) {
                return type.cast(advice);
            }
        }
        return null;
    }

    @Override
    public void addRoutePolicyAdvice(List<RoutePolicy> routePolicyList) {
        addAdvice(new CamelInternalProcessor.RoutePolicyAdvice(routePolicyList));
    }

    @Override
    public void addRouteInflightRepositoryAdvice(InflightRepository inflightRepository, String routeId) {
        addAdvice(new CamelInternalProcessor.RouteInflightRepositoryAdvice(camelContext.getInflightRepository(), routeId));
    }

    @Override
    public void addRouteLifecycleAdvice() {
        addAdvice(new CamelInternalProcessor.RouteLifecycleAdvice());
    }

    @Override
    public void addManagementInterceptStrategy(InstrumentationProcessor processor) {
        addAdvice(CamelInternalProcessor.wrap(processor));
    }

    @Override
    public void setRouteOnAdvices(Route route) {
        RoutePolicyAdvice task = getAdvice(RoutePolicyAdvice.class);
        if (task != null) {
            task.setRoute(route);
        }
        RouteLifecycleAdvice task2 = getAdvice(RouteLifecycleAdvice.class);
        if (task2 != null) {
            task2.setRoute(route);
        }
    }

    /**
     * Callback task to process the advices after processing.
     */
    private final class AsyncAfterTask implements CamelInternalTask {

        private final Object[] states;
        private Exchange exchange;
        private AsyncCallback originalCallback;

        private AsyncAfterTask(Object[] states) {
            this.states = states;
        }

        @Override
        public void prepare(Exchange exchange, AsyncCallback originalCallback) {
            this.exchange = exchange;
            this.originalCallback = originalCallback;
        }

        @Override
        public Object[] getStates() {
            return states;
        }

        @Override
        public void reset() {
            // reset array by copying over from empty which is a very fast JVM optimized operation
            System.arraycopy(emptyStatefulStates, 0, states, 0, statefulAdvices);
            this.exchange = null;
            this.originalCallback = null;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                AdviceIterator.runAfterTasks(advices, states, exchange);
            } finally {
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ START +++
                // ----------------------------------------------------------
                // callback must be called
                if (originalCallback != null) {
                    reactiveExecutor.schedule(originalCallback);
                }
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ END +++
                // ----------------------------------------------------------

                // task is done so reset
                if (taskFactory != null) {
                    taskFactory.release(this);
                }
            }
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback originalCallback) {
        // ----------------------------------------------------------
        // CAMEL END USER - READ ME FOR DEBUGGING TIPS
        // ----------------------------------------------------------
        // If you want to debug the Camel routing engine, then there is a lot of internal functionality
        // the routing engine executes during routing messages. You can skip debugging this internal
        // functionality and instead debug where the routing engine continues routing to the next node
        // in the routes. The CamelInternalProcessor is a vital part of the routing engine, as its
        // being used in between the nodes. As an end user you can just debug the code in this class
        // in between the:
        //   CAMEL END USER - DEBUG ME HERE +++ START +++
        //   CAMEL END USER - DEBUG ME HERE +++ END +++
        // you can see in the code below.
        // ----------------------------------------------------------

        if (processor == null || exchange.isRouteStop()) {
            // no processor or we should not continue then we are done
            originalCallback.done(true);
            return true;
        }

        if (shutdownStrategy.isForceShutdown()) {
            String msg = "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: "
                         + exchange;
            LOG.debug(msg);
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException(msg));
            }
            // force shutdown so we should not continue
            originalCallback.done(true);
            return true;
        }

        Object[] states;

        // create internal callback which will execute the advices in reverse order when done
        CamelInternalTask afterTask = taskFactory != null ? taskFactory.acquire() : null;
        if (afterTask == null) {
            states = statefulAdvices > 0 ? new Object[statefulAdvices] : EMPTY_STATES;
            afterTask = new AsyncAfterTask(states);
        } else {
            states = afterTask.getStates();
        }
        afterTask.prepare(exchange, originalCallback);

        // optimise to use object array for states, and only for the number of advices that keep state
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0, j = 0; i < advices.size(); i++) {
            CamelInternalProcessorAdvice<?> task = advices.get(i);
            try {
                Object state = task.before(exchange);
                if (task.hasState()) {
                    states[j++] = state;
                }
            } catch (Exception e) {
                // error in before so break out
                exchange.setException(e);
                try {
                    originalCallback.done(true);
                } finally {
                    // task is done so reset
                    if (taskFactory != null) {
                        taskFactory.release(afterTask);
                    }
                }
                return true;
            }
        }

        if (exchange.isTransacted()) {
            // must be synchronized for transacted exchanges
            if (LOG.isTraceEnabled()) {
                LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(),
                        exchange);
            }
            try {
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ START +++
                // ----------------------------------------------------------
                processor.process(exchange);
                // ----------------------------------------------------------
                // CAMEL END USER - DEBUG ME HERE +++ END +++
                // ----------------------------------------------------------
            } catch (Exception e) {
                exchange.setException(e);
            } finally {
                // processing is done
                afterTask.done(true);
            }
            // we are done synchronously - must return true
            return true;
        } else {
            final UnitOfWork uow = exchange.getUnitOfWork();

            // optimize to only do before uow processing if really needed
            AsyncCallback async = afterTask;
            boolean beforeAndAfter = uow != null && uow.isBeforeAfterProcess();
            if (beforeAndAfter) {
                async = uow.beforeProcess(processor, exchange, async);
            }

            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ START +++
            // ----------------------------------------------------------
            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchange for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            }
            boolean sync = processor.process(exchange, async);
            if (!sync) {
                EventHelper.notifyExchangeAsyncProcessingStartedEvent(camelContext, exchange);
            }

            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ END +++
            // ----------------------------------------------------------

            // CAMEL-18255: move uow.afterProcess handling to the callback

            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange processed and is continued routed {} for exchangeId: {} -> {}",
                        sync ? "synchronously" : "asynchronously",
                        exchange.getExchangeId(), exchange);
            }
            return sync;
        }
    }

    @Override
    public String toString() {
        return processor != null ? processor.toString() : super.toString();
    }

    /**
     * Advice to invoke callbacks for before and after routing.
     */
    public static class RouteLifecycleAdvice implements CamelInternalProcessorAdvice<Object> {

        private Route route;

        public void setRoute(Route route) {
            this.route = route;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            UnitOfWork uow = exchange.getUnitOfWork();
            if (uow != null) {
                uow.beforeRoute(exchange, route);
            }
            return null;
        }

        @Override
        public void after(Exchange exchange, Object object) throws Exception {
            UnitOfWork uow = exchange.getUnitOfWork();
            if (uow != null) {
                uow.afterRoute(exchange, route);
            }
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    /**
     * Advice to keep the {@link InflightRepository} up to date.
     */
    public static class RouteInflightRepositoryAdvice implements CamelInternalProcessorAdvice<Object> {

        private final InflightRepository inflightRepository;
        private final String id;

        public RouteInflightRepositoryAdvice(InflightRepository inflightRepository, String id) {
            this.inflightRepository = inflightRepository;
            this.id = id;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            inflightRepository.add(exchange, id);
            return null;
        }

        @Override
        public void after(Exchange exchange, Object state) throws Exception {
            inflightRepository.remove(exchange, id);
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    /**
     * Advice to execute any {@link RoutePolicy} a route may have been configured with.
     */
    public static class RoutePolicyAdvice implements CamelInternalProcessorAdvice<Object> {

        private final Logger log = LoggerFactory.getLogger(getClass());
        private final List<RoutePolicy> routePolicies;
        private Route route;

        public RoutePolicyAdvice(List<RoutePolicy> routePolicies) {
            this.routePolicies = routePolicies;
        }

        public void setRoute(Route route) {
            this.route = route;
        }

        /**
         * Strategy to determine if this policy is allowed to run
         *
         * @param  policy the policy
         * @return        <tt>true</tt> to run
         */
        boolean isRoutePolicyRunAllowed(RoutePolicy policy) {
            if (policy instanceof StatefulService ss) {
                return ss.isRunAllowed();
            }
            return true;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            // invoke begin
            for (RoutePolicy policy : routePolicies) {
                try {
                    if (isRoutePolicyRunAllowed(policy)) {
                        policy.onExchangeBegin(route, exchange);
                    }
                } catch (Exception e) {
                    log.warn("Error occurred during onExchangeBegin on RoutePolicy: {}. This exception will be ignored", policy,
                            e);
                }
            }
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            // do not invoke it if Camel is stopping as we don't want
            // the policy to start a consumer during Camel is stopping
            if (isCamelStopping(exchange.getContext())) {
                return;
            }

            for (RoutePolicy policy : routePolicies) {
                try {
                    if (isRoutePolicyRunAllowed(policy)) {
                        policy.onExchangeDone(route, exchange);
                    }
                } catch (Exception e) {
                    log.warn("Error occurred during onExchangeDone on RoutePolicy: {}. This exception will be ignored",
                            policy, e);
                }
            }
        }

        private static boolean isCamelStopping(CamelContext context) {
            if (context != null) {
                return context.isStopping() || context.isStopped();
            }
            return false;
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    /**
     * Advice to execute the {@link BacklogTracer} if enabled.
     */
    public static final class BacklogTracerAdvice
            implements CamelInternalProcessorAdvice<DefaultBacklogTracerEventMessage>, Ordered {

        private final BacklogTraceAdviceEventNotifier notifier;
        private final CamelContext camelContext;
        private final BacklogTracer backlogTracer;
        private final NamedNode processorDefinition;
        private final NamedRoute routeDefinition;
        private final boolean first;
        private final boolean rest;
        private final boolean template;
        private final boolean skip;

        public BacklogTracerAdvice(CamelContext camelContext, BacklogTracer backlogTracer, NamedNode processorDefinition,
                                   NamedRoute routeDefinition, boolean first) {
            this.camelContext = camelContext;
            this.backlogTracer = backlogTracer;
            this.processorDefinition = processorDefinition;
            this.routeDefinition = routeDefinition;
            this.first = first;

            if (routeDefinition != null) {
                this.rest = routeDefinition.isCreatedFromRest();
                this.template = routeDefinition.isCreatedFromTemplate();
            } else {
                this.rest = false;
                this.template = false;
            }
            // optimize whether to skip this route or not
            if (this.rest && !backlogTracer.isTraceRests()) {
                this.skip = true;
            } else if (this.template && !backlogTracer.isTraceTemplates()) {
                this.skip = true;
            } else {
                this.skip = false;
            }
            this.notifier = getOrCreateEventNotifier(camelContext);
        }

        private BacklogTraceAdviceEventNotifier getOrCreateEventNotifier(CamelContext camelContext) {
            // use a single instance of this event notifier
            for (EventNotifier en : camelContext.getManagementStrategy().getEventNotifiers()) {
                if (en instanceof BacklogTraceAdviceEventNotifier) {
                    return (BacklogTraceAdviceEventNotifier) en;
                }
            }
            BacklogTraceAdviceEventNotifier answer = new BacklogTraceAdviceEventNotifier();
            camelContext.getManagementStrategy().addEventNotifier(answer);
            return answer;
        }

        @Override
        public DefaultBacklogTracerEventMessage before(Exchange exchange) throws Exception {
            if (!skip && backlogTracer.shouldTrace(processorDefinition, exchange)) {

                // to capture if the exchange was sent to an endpoint during this event
                notifier.before(exchange);

                long timestamp = System.currentTimeMillis();
                String toNode = processorDefinition.getId();
                String exchangeId = exchange.getExchangeId();
                boolean includeExchangeProperties = backlogTracer.isIncludeExchangeProperties();
                String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), includeExchangeProperties, true, 4,
                        true, backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(),
                        backlogTracer.getBodyMaxChars());
                String messageAsJSon = MessageHelper.dumpAsJSon(exchange.getIn(), includeExchangeProperties, true, 4,
                        true, backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(),
                        backlogTracer.getBodyMaxChars(), true);

                // if first we should add a pseudo trace message as well, so we have a starting message (eg from the route)
                String routeId = routeDefinition != null ? routeDefinition.getRouteId() : null;
                if (first) {
                    // use route as pseudo source when first
                    String source = LoggerHelper.getLineNumberLoggerName(routeDefinition);
                    long created = exchange.getCreated();
                    DefaultBacklogTracerEventMessage pseudoFirst = new DefaultBacklogTracerEventMessage(
                            true, false, backlogTracer.incrementTraceCounter(), created, source, routeId, null, exchangeId,
                            rest, template, messageAsXml, messageAsJSon);
                    backlogTracer.traceEvent(pseudoFirst);
                    exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                        @Override
                        public void onDone(Exchange exchange) {
                            // create pseudo last
                            String routeId = routeDefinition != null ? routeDefinition.getRouteId() : null;
                            String exchangeId = exchange.getExchangeId();
                            boolean includeExchangeProperties = backlogTracer.isIncludeExchangeProperties();
                            long created = exchange.getCreated();
                            String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), includeExchangeProperties, true, 4,
                                    true, backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(),
                                    backlogTracer.getBodyMaxChars());
                            String messageAsJSon
                                    = MessageHelper.dumpAsJSon(exchange.getIn(), includeExchangeProperties, true, 4,
                                            true, backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(),
                                            backlogTracer.getBodyMaxChars(), true);
                            DefaultBacklogTracerEventMessage pseudoLast = new DefaultBacklogTracerEventMessage(
                                    false, true, backlogTracer.incrementTraceCounter(), created, source, routeId, null,
                                    exchangeId, rest, template, messageAsXml, messageAsJSon);
                            backlogTracer.traceEvent(pseudoLast);
                            doneProcessing(exchange, pseudoLast);
                            doneProcessing(exchange, pseudoFirst);
                            // to not be confused then lets store duration on first/last as (first = 0, last = total time to process)
                            pseudoLast.setElapsed(pseudoFirst.getElapsed());
                            pseudoFirst.setElapsed(0);
                        }
                    });
                }
                String source = LoggerHelper.getLineNumberLoggerName(processorDefinition);
                DefaultBacklogTracerEventMessage event = new DefaultBacklogTracerEventMessage(
                        false, false, backlogTracer.incrementTraceCounter(), timestamp, source, routeId, toNode, exchangeId,
                        rest, template, messageAsXml, messageAsJSon);
                backlogTracer.traceEvent(event);

                return event;
            }

            return null;
        }

        @Override
        public void after(Exchange exchange, DefaultBacklogTracerEventMessage data) throws Exception {
            if (data != null) {
                doneProcessing(exchange, data);
            }
        }

        private void doneProcessing(Exchange exchange, DefaultBacklogTracerEventMessage data) {
            data.doneProcessing();

            String uri = null;
            Endpoint endpoint = notifier.after(exchange);
            if (endpoint != null) {
                uri = endpoint.getEndpointUri();
            } else if ((data.isFirst() || data.isLast()) && data.getToNode() == null && routeDefinition != null) {
                // pseudo first/last event (the from in the route)
                Route route = camelContext.getRoute(routeDefinition.getRouteId());
                if (route != null && route.getConsumer() != null) {
                    // get the actual resolved uri
                    uri = route.getConsumer().getEndpoint().getEndpointUri();
                } else {
                    uri = routeDefinition.getEndpointUrl();
                }
            }
            if (uri != null) {
                data.setEndpointUri(uri);
            }

            if (!data.isFirst()) {
                // we want to capture if there was an exception
                Throwable e = exchange.getException();
                if (e != null) {
                    String xml = MessageHelper.dumpExceptionAsXML(e, 4);
                    data.setExceptionAsXml(xml);
                    String json = MessageHelper.dumpExceptionAsJSon(e, 4, true);
                    data.setExceptionAsJSon(json);
                }
            }
        }

        @Override
        public boolean hasState() {
            return true;
        }

        @Override
        public int getOrder() {
            // we want tracer just before calling the processor
            return Ordered.LOWEST - 1;
        }
    }

    /**
     * Advice to execute the {@link BacklogDebugger} if enabled.
     */
    public static final class BacklogDebuggerAdvice implements CamelInternalProcessorAdvice<StopWatch>, Ordered {

        private final BacklogDebugger backlogDebugger;
        private final Processor target;
        private final NamedNode definition;

        public BacklogDebuggerAdvice(BacklogDebugger backlogDebugger, Processor target, NamedNode definition) {
            this.backlogDebugger = backlogDebugger;
            this.target = target;
            this.definition = definition;
        }

        @Override
        public StopWatch before(Exchange exchange) throws Exception {
            return backlogDebugger.beforeProcess(exchange, target, definition);
        }

        @Override
        public void after(Exchange exchange, StopWatch stopWatch) throws Exception {
            if (stopWatch != null) {
                backlogDebugger.afterProcess(exchange, target, definition, stopWatch.taken());
            }
        }

        @Override
        public int getOrder() {
            // we want debugger just before calling the processor
            return Ordered.LOWEST;
        }
    }

    /**
     * Advice to execute when using custom debugger.
     */
    public static final class DebuggerAdvice implements CamelInternalProcessorAdvice<StopWatch>, Ordered {

        private final Debugger debugger;
        private final Processor target;
        private final NamedNode definition;

        public DebuggerAdvice(Debugger debugger, Processor target, NamedNode definition) {
            this.debugger = debugger;
            this.target = target;
            this.definition = definition;
        }

        @Override
        public StopWatch before(Exchange exchange) throws Exception {
            debugger.beforeProcess(exchange, target, definition);
            return new StopWatch();
        }

        @Override
        public void after(Exchange exchange, StopWatch stopWatch) throws Exception {
            debugger.afterProcess(exchange, target, definition, stopWatch.taken());
        }

        @Override
        public int getOrder() {
            // we want debugger just before calling the processor
            return Ordered.LOWEST;
        }
    }

    /**
     * Advice to inject new {@link UnitOfWork} to the {@link Exchange} if needed, and as well to ensure the
     * {@link UnitOfWork} is done and stopped.
     */
    public static class UnitOfWorkProcessorAdvice implements CamelInternalProcessorAdvice<UnitOfWork> {

        private final Route route;
        private String routeId;
        private final UnitOfWorkFactory uowFactory;

        public UnitOfWorkProcessorAdvice(Route route, CamelContext camelContext) {
            this.route = route;
            if (route != null) {
                this.routeId = route.getRouteId();
            }
            this.uowFactory = PluginHelper.getUnitOfWorkFactory(camelContext);
            // optimize uow factory to initialize it early and once per advice
            this.uowFactory.afterPropertiesConfigured(camelContext);
        }

        @Override
        public UnitOfWork before(Exchange exchange) throws Exception {
            // if the exchange doesn't have from route id set, then set it if it originated
            // from this unit of work
            if (route != null && exchange.getFromRouteId() == null) {
                if (routeId == null) {
                    this.routeId = route.getRouteId();
                }
                exchange.getExchangeExtension().setFromRouteId(routeId);
            }

            // only return UnitOfWork if we created a new as then its us that handle the lifecycle to done the created UoW
            UnitOfWork created = null;
            UnitOfWork uow = exchange.getUnitOfWork();

            if (uow == null) {
                // If there is no existing UoW, then we should start one and
                // terminate it once processing is completed for the exchange.
                created = createUnitOfWork(exchange);
                exchange.getExchangeExtension().setUnitOfWork(created);
                uow = created;
            } else {
                // reuse existing exchange
                if (uow.onPrepare(exchange)) {
                    // need to re-attach uow
                    exchange.getExchangeExtension().setUnitOfWork(uow);
                    // we are prepared for reuse and can regard it as-if we created the unit of work
                    // so the after method knows that this is the outer bounds and should done the unit of work
                    created = uow;
                }
            }

            // for any exchange we should push/pop route context so we can keep track of which route we are routing
            if (route != null) {
                uow.pushRoute(route);
            }

            return created;
        }

        @Override
        public void after(Exchange exchange, UnitOfWork uow) throws Exception {
            UnitOfWork existing = exchange.getUnitOfWork();

            // execute done on uow if we created it, and the consumer is not doing it
            if (uow != null) {
                UnitOfWorkHelper.doneUow(uow, exchange);
            }

            // after UoW is done lets pop the route context which must be done on every existing UoW
            if (route != null && existing != null) {
                existing.popRoute();
            }
        }

        protected UnitOfWork createUnitOfWork(Exchange exchange) {
            if (uowFactory != null) {
                return uowFactory.createUnitOfWork(exchange);
            } else {
                return PluginHelper.getUnitOfWorkFactory(exchange.getContext()).createUnitOfWork(exchange);
            }
        }

    }

    /**
     * Advice when an EIP uses the <tt>shareUnitOfWork</tt> functionality.
     */
    public static class ChildUnitOfWorkProcessorAdvice extends UnitOfWorkProcessorAdvice {

        private final UnitOfWork parent;

        public ChildUnitOfWorkProcessorAdvice(Route route, CamelContext camelContext, UnitOfWork parent) {
            super(route, camelContext);
            this.parent = parent;
        }

        @Override
        protected UnitOfWork createUnitOfWork(Exchange exchange) {
            // let the parent create a child unit of work to be used
            return parent.createChildUnitOfWork(exchange);
        }

    }

    /**
     * Advice when Message History has been enabled.
     */
    @SuppressWarnings("unchecked")
    public static class MessageHistoryAdvice implements CamelInternalProcessorAdvice<MessageHistory> {

        private final MessageHistoryFactory factory;
        private final NamedNode definition;
        private final String routeId;

        public MessageHistoryAdvice(MessageHistoryFactory factory, NamedNode definition) {
            this.factory = factory;
            this.definition = definition;
            this.routeId = CamelContextHelper.getRouteId(definition);
        }

        @Override
        public MessageHistory before(Exchange exchange) throws Exception {
            // we may be routing outside a route in an onException or interceptor and if so then grab
            // route id from the exchange UoW state
            String targetRouteId = this.routeId;
            if (targetRouteId == null) {
                targetRouteId = ExchangeHelper.getRouteId(exchange);
            }

            MessageHistory history = factory.newMessageHistory(targetRouteId, definition, System.currentTimeMillis(), exchange);
            if (history != null) {
                List<MessageHistory> list = exchange.getProperty(ExchangePropertyKey.MESSAGE_HISTORY, List.class);
                if (list == null) {
                    // use thread-safe list as message history may be accessed concurrently
                    list = new CopyOnWriteArrayList<>();
                    exchange.setProperty(ExchangePropertyKey.MESSAGE_HISTORY, list);
                }
                list.add(history);
            }
            return history;
        }

        @Override
        public void after(Exchange exchange, MessageHistory history) throws Exception {
            if (history != null) {
                history.nodeProcessingDone();
            }
        }
    }

    /**
     * Advice that stores the node id and label of the processor that is processing the exchange.
     */
    public static class NodeHistoryAdvice implements CamelInternalProcessorAdvice<String> {

        private final String id;
        private final String label;
        private final String source;

        public NodeHistoryAdvice(NamedNode definition) {
            this.id = definition.getId();
            this.label = definition.getLabel();
            this.source = LoggerHelper.getLineNumberLoggerName(definition);
        }

        @Override
        public String before(Exchange exchange) throws Exception {
            exchange.getExchangeExtension().setHistoryNodeId(id);
            exchange.getExchangeExtension().setHistoryNodeLabel(label);
            exchange.getExchangeExtension().setHistoryNodeSource(source);
            return null;
        }

        @Override
        public void after(Exchange exchange, String data) throws Exception {
            exchange.getExchangeExtension().setHistoryNodeId(null);
            exchange.getExchangeExtension().setHistoryNodeLabel(null);
            exchange.getExchangeExtension().setHistoryNodeSource(null);
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    /**
     * Advice for {@link org.apache.camel.spi.StreamCachingStrategy}
     */
    public static class StreamCachingAdvice implements CamelInternalProcessorAdvice<StreamCache>, Ordered {

        private final StreamCachingStrategy strategy;

        public StreamCachingAdvice(StreamCachingStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public StreamCache before(Exchange exchange) throws Exception {
            return StreamCachingHelper.convertToStreamCache(strategy, exchange, exchange.getIn());
        }

        @Override
        public void after(Exchange exchange, StreamCache sc) throws Exception {
            // reset cached streams so they can be read again
            MessageHelper.resetStreamCache(exchange.getMessage());
        }

        @Override
        public int getOrder() {
            // we want stream caching first
            return Ordered.HIGHEST;
        }
    }

    /**
     * Advice for delaying
     */
    public static class DelayerAdvice implements CamelInternalProcessorAdvice<Object> {

        private final Logger log = LoggerFactory.getLogger(getClass());
        private final long delay;

        public DelayerAdvice(long delay) {
            this.delay = delay;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            try {
                log.trace("Sleeping for: {} millis", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                log.debug("Sleep interrupted");
                Thread.currentThread().interrupt();
                throw e;
            }
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            // noop
        }

        @Override
        public boolean hasState() {
            return false;
        }
    }

    /**
     * Advice for tracing
     */
    public static class TracingAdvice implements CamelInternalProcessorAdvice<Object> {

        private final Tracer tracer;
        private final NamedNode processorDefinition;
        private final NamedRoute routeDefinition;
        private final Synchronization tracingAfterRoute;
        private final boolean rest;
        private final boolean template;
        private final boolean skip;

        public TracingAdvice(Tracer tracer, NamedNode processorDefinition, NamedRoute routeDefinition, boolean first) {
            this.tracer = tracer;
            this.processorDefinition = processorDefinition;
            this.routeDefinition = routeDefinition;
            this.tracingAfterRoute
                    = routeDefinition != null
                            ? new TracingAfterRoute(tracer, routeDefinition.getRouteId(), routeDefinition) : null;

            if (routeDefinition != null) {
                this.rest = routeDefinition.isCreatedFromRest();
                this.template = routeDefinition.isCreatedFromTemplate();
            } else {
                this.rest = false;
                this.template = false;
            }
            // optimize whether to skip this route or not
            if (this.rest && !tracer.isTraceRests()) {
                this.skip = true;
            } else if (this.template && !tracer.isTraceTemplates()) {
                this.skip = true;
            } else {
                this.skip = false;
            }
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            if (!skip && tracer.isEnabled()) {
                if (tracingAfterRoute != null) {
                    // add before route and after route tracing but only once per route, so check if there is already an existing
                    boolean contains = exchange.getUnitOfWork().containsSynchronization(tracingAfterRoute);
                    if (!contains) {
                        tracer.traceBeforeRoute(routeDefinition, exchange);
                        exchange.getExchangeExtension().addOnCompletion(tracingAfterRoute);
                    }
                }
                tracer.traceBeforeNode(processorDefinition, exchange);
            }
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            if (!skip && tracer.isEnabled()) {
                tracer.traceAfterNode(processorDefinition, exchange);
            }
        }

        @Override
        public boolean hasState() {
            return false;
        }

        private static final class TracingAfterRoute extends SynchronizationAdapter {

            private final Tracer tracer;
            private final String routeId;
            private final NamedRoute node;

            private TracingAfterRoute(Tracer tracer, String routeId, NamedRoute node) {
                this.tracer = tracer;
                this.routeId = routeId;
                this.node = node;
            }

            @Override
            public SynchronizationRouteAware getRouteSynchronization() {
                return new SynchronizationRouteAware() {
                    @Override
                    public void onBeforeRoute(Route route, Exchange exchange) {
                        // NO-OP
                    }

                    @Override
                    public void onAfterRoute(Route route, Exchange exchange) {
                        if (routeId.equals(route.getId())) {
                            tracer.traceAfterRoute(node, exchange);
                        }
                    }
                };
            }

            @Override
            public boolean equals(Object o) {
                // only match equals on route id so we can check this from containsSynchronization
                // to avoid adding multiple times for the same route id
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                TracingAfterRoute that = (TracingAfterRoute) o;
                return routeId.equals(that.routeId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(routeId);
            }
        }
    }

    /**
     * Wrap an InstrumentationProcessor into a CamelInternalProcessorAdvice
     */
    public static <T> CamelInternalProcessorAdvice<T> wrap(InstrumentationProcessor<T> instrumentationProcessor) {
        if (instrumentationProcessor instanceof CamelInternalProcessor) {
            return (CamelInternalProcessorAdvice<T>) instrumentationProcessor;
        } else {
            return new CamelInternalProcessorAdviceWrapper<>(instrumentationProcessor);
        }
    }

    public static Object unwrap(CamelInternalProcessorAdvice<?> advice) {
        if (advice instanceof CamelInternalProcessorAdviceWrapper<?> wrapped) {
            return wrapped.unwrap();
        } else {
            return advice;
        }
    }

    record CamelInternalProcessorAdviceWrapper<T>(
            InstrumentationProcessor<T> instrumentationProcessor) implements CamelInternalProcessorAdvice<T>, Ordered {

        InstrumentationProcessor<T> unwrap() {
            return instrumentationProcessor;
        }

        @Override
        public int getOrder() {
            return instrumentationProcessor.getOrder();
        }

        @Override
        public T before(Exchange exchange) throws Exception {
            return instrumentationProcessor.before(exchange);
        }

        @Override
        public void after(Exchange exchange, T data) throws Exception {
            instrumentationProcessor.after(exchange, data);
        }
    }

    /**
     * Event notifier for {@link BacklogTracerAdvice} to capture {@link Exchange} sent to endpoints during tracing.
     */
    private static final class BacklogTraceAdviceEventNotifier extends SimpleEventNotifierSupport {

        private final Object dummy = new Object();

        private final ConcurrentMap<Exchange, Object> uris = new ConcurrentHashMap<>();

        public BacklogTraceAdviceEventNotifier() {
            // only capture sending events
            setIgnoreExchangeEvents(false);
            setIgnoreExchangeSendingEvents(false);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            if (event instanceof CamelEvent.ExchangeSendingEvent ess) {
                Exchange e = ess.getExchange();
                if (uris.containsKey(e)) {
                    uris.put(e, ess.getEndpoint());
                }
            }
        }

        public void before(Exchange exchange) {
            uris.put(exchange, dummy);
        }

        public Endpoint after(Exchange exchange) {
            Object o = uris.remove(exchange);
            if (o == dummy) {
                return null;
            }
            return (Endpoint) o;
        }

    }
}
