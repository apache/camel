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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.MessageHistory;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.Ordered;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.StreamCache;
import org.apache.camel.processor.interceptor.BacklogDebugger;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.DefaultBacklogTracerEventMessage;
import org.apache.camel.spi.CamelInternalProcessorAdvice;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.ManagementInterceptStrategy.InstrumentationProcessor;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 *     <li>Execute {@link UnitOfWork}</li>
 *     <li>Keeping track which route currently is being routed</li>
 *     <li>Execute {@link RoutePolicy}</li>
 *     <li>Gather JMX performance statics</li>
 *     <li>Tracing</li>
 *     <li>Debugging</li>
 *     <li>Message History</li>
 *     <li>Stream Caching</li>
 *     <li>{@link Transformer}</li>
 * </ul>
 * ... and more.
 * <p/>
 * This implementation executes this cross cutting functionality as a {@link CamelInternalProcessorAdvice} advice (before and after advice)
 * by executing the {@link CamelInternalProcessorAdvice#before(org.apache.camel.Exchange)} and
 * {@link CamelInternalProcessorAdvice#after(org.apache.camel.Exchange, Object)} callbacks in correct order during routing.
 * This reduces number of stack frames needed during routing, and reduce the number of lines in stacktraces, as well
 * makes debugging the routing engine easier for end users.
 * <p/>
 * <b>Debugging tips:</b> Camel end users whom want to debug their Camel applications with the Camel source code, then make sure to
 * read the source code of this class about the debugging tips, which you can find in the
 * {@link #process(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)} method.
 * <p/>
 * The added advices can implement {@link Ordered} to control in which order the advices are executed.
 */
public class CamelInternalProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelInternalProcessor.class);

    private static final Object[] EMPTY_STATES = new Object[0];

    private final CamelContext camelContext;
    private final ReactiveExecutor reactiveExecutor;
    private final ShutdownStrategy shutdownStrategy;
    private final List<CamelInternalProcessorAdvice<?>> advices = new ArrayList<>();
    private byte statefulAdvices;

    public CamelInternalProcessor(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.adapt(ExtendedCamelContext.class).getReactiveExecutor();
        this.shutdownStrategy = camelContext.getShutdownStrategy();
    }

    public CamelInternalProcessor(CamelContext camelContext, Processor processor) {
        super(processor);
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.adapt(ExtendedCamelContext.class).getReactiveExecutor();
        this.shutdownStrategy = camelContext.getShutdownStrategy();
    }

    /**
     * Adds an {@link CamelInternalProcessorAdvice} advice to the list of advices to execute by this internal processor.
     *
     * @param advice  the advice to add
     */
    public void addAdvice(CamelInternalProcessorAdvice<?> advice) {
        advices.add(advice);
        // ensure advices are sorted so they are in the order we want
        advices.sort(OrderedComparator.get());

        if (advice.hasState()) {
            statefulAdvices++;
        }
    }

    /**
     * Gets the advice with the given type.
     *
     * @param type  the type of the advice
     * @return the advice if exists, or <tt>null</tt> if no advices has been added with the given type.
     */
    public <T> T getAdvice(Class<T> type) {
        for (CamelInternalProcessorAdvice task : advices) {
            Object advice = unwrap(task);
            if (type.isInstance(advice)) {
                return type.cast(advice);
            }
        }
        return null;
    }

    /**
     * Callback task to process the advices after processing.
     */
    private final class AsyncAfterTask implements AsyncCallback {

        private final Object[] states;
        private final Exchange exchange;
        private final AsyncCallback originalCallback;

        private AsyncAfterTask(Object[] states, Exchange exchange, AsyncCallback originalCallback) {
            this.states = states;
            this.exchange = exchange;
            this.originalCallback = originalCallback;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                for (int i = advices.size() - 1, j = states.length - 1; i >= 0; i--) {
                    CamelInternalProcessorAdvice task = advices.get(i);
                    Object state = null;
                    if (task.hasState()) {
                        state = states[j--];
                    }
                    try {
                        task.after(exchange, state);
                    } catch (Throwable e) {
                        exchange.setException(e);
                        // allow all advices to complete even if there was an exception
                    }
                }
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
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
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

        boolean forceShutdown = shutdownStrategy.forceShutdown(this);
        if (forceShutdown) {
            String msg = "Run not allowed as ShutdownStrategy is forcing shutting down, will reject executing exchange: " + exchange;
            LOG.debug(msg);
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException(msg));
            }
            // force shutdown so we should not continue
            originalCallback.done(true);
            return true;

        }

        // optimise to use object array for states, and only for the number of advices that keep state
        final Object[] states = statefulAdvices > 0 ? new Object[statefulAdvices] : EMPTY_STATES;
        // optimise for loop using index access to avoid creating iterator object
        for (int i = 0, j = 0; i < advices.size(); i++) {
            CamelInternalProcessorAdvice task = advices.get(i);
            try {
                Object state = task.before(exchange);
                if (task.hasState()) {
                    states[j++] = state;
                }
            } catch (Throwable e) {
                exchange.setException(e);
                originalCallback.done(true);
                return true;
            }
        }

        // create internal callback which will execute the advices in reverse order when done
        AsyncCallback callback = new AsyncAfterTask(states, exchange, originalCallback);

        if (exchange.isTransacted()) {
            // must be synchronized for transacted exchanges
            if (LOG.isTraceEnabled()) {
                LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            }
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ START +++
            // ----------------------------------------------------------
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ END +++
            // ----------------------------------------------------------
            callback.done(true);
            return true;
        } else {
            final UnitOfWork uow = exchange.getUnitOfWork();

            // do uow before processing and if a value is returned the the uow wants to be processed after
            // was well in the same thread
            AsyncCallback async = callback;
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
            processor.process(exchange, async);
            // ----------------------------------------------------------
            // CAMEL END USER - DEBUG ME HERE +++ END +++
            // ----------------------------------------------------------

            // optimize to only do after uow processing if really needed
            if (beforeAndAfter) {
                reactiveExecutor.schedule(() -> {
                    // execute any after processor work (in current thread, not in the callback)
                    uow.afterProcess(processor, exchange, callback, false);
                });
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange processed and is continued routed asynchronously for exchangeId: {} -> {}",
                        exchange.getExchangeId(), exchange);
            }
            // must return false
            return false;
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
    public static class RouteInflightRepositoryAdvice implements CamelInternalProcessorAdvice {

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
    public static class RoutePolicyAdvice implements CamelInternalProcessorAdvice {

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
         * @param policy the policy
         * @return <tt>true</tt> to run
         */
        boolean isRoutePolicyRunAllowed(RoutePolicy policy) {
            if (policy instanceof StatefulService) {
                StatefulService ss = (StatefulService) policy;
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
                    log.warn("Error occurred during onExchangeBegin on RoutePolicy: " + policy
                            + ". This exception will be ignored", e);
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
                    log.warn("Error occurred during onExchangeDone on RoutePolicy: " + policy
                            + ". This exception will be ignored", e);
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
    public static final class BacklogTracerAdvice implements CamelInternalProcessorAdvice, Ordered {

        private final BacklogTracer backlogTracer;
        private final NamedNode processorDefinition;
        private final NamedRoute routeDefinition;
        private final boolean first;

        public BacklogTracerAdvice(BacklogTracer backlogTracer, NamedNode processorDefinition,
                                   NamedRoute routeDefinition, boolean first) {
            this.backlogTracer = backlogTracer;
            this.processorDefinition = processorDefinition;
            this.routeDefinition = routeDefinition;
            this.first = first;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            if (backlogTracer.shouldTrace(processorDefinition, exchange)) {
                long timestamp = System.currentTimeMillis();
                String toNode = processorDefinition.getId();
                String exchangeId = exchange.getExchangeId();
                String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 4,
                        backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(), backlogTracer.getBodyMaxChars());

                // if first we should add a pseudo trace message as well, so we have a starting message (eg from the route)
                String routeId = routeDefinition != null ? routeDefinition.getRouteId() : null;
                if (first) {
                    long created = exchange.getCreated();
                    DefaultBacklogTracerEventMessage pseudo = new DefaultBacklogTracerEventMessage(backlogTracer.incrementTraceCounter(), created, routeId, null, exchangeId, messageAsXml);
                    backlogTracer.traceEvent(pseudo);
                }
                DefaultBacklogTracerEventMessage event = new DefaultBacklogTracerEventMessage(backlogTracer.incrementTraceCounter(), timestamp, routeId, toNode, exchangeId, messageAsXml);
                backlogTracer.traceEvent(event);
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

        @Override
        public int getOrder() {
            // we want tracer just before calling the processor
            return Ordered.LOWEST - 1;
        }
    }

    /**
     * Advice to execute the {@link org.apache.camel.processor.interceptor.BacklogDebugger} if enabled.
     */
    public static final class BacklogDebuggerAdvice implements CamelInternalProcessorAdvice<StopWatch>, Ordered {

        private final BacklogDebugger backlogDebugger;
        private final Processor target;
        private final NamedNode definition;
        private final String nodeId;

        public BacklogDebuggerAdvice(BacklogDebugger backlogDebugger, Processor target, NamedNode definition) {
            this.backlogDebugger = backlogDebugger;
            this.target = target;
            this.definition = definition;
            this.nodeId = definition.getId();
        }

        @Override
        public StopWatch before(Exchange exchange) throws Exception {
            if (backlogDebugger.isEnabled() && (backlogDebugger.hasBreakpoint(nodeId) || backlogDebugger.isSingleStepMode())) {
                StopWatch watch = new StopWatch();
                backlogDebugger.beforeProcess(exchange, target, definition);
                return watch;
            } else {
                return null;
            }
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
     * Advice to inject new {@link UnitOfWork} to the {@link Exchange} if needed, and as well to ensure
     * the {@link UnitOfWork} is done and stopped.
     */
    public static class UnitOfWorkProcessorAdvice implements CamelInternalProcessorAdvice<UnitOfWork> {

        private final Route route;
        private String routeId;
        private UnitOfWorkFactory uowFactory;

        public UnitOfWorkProcessorAdvice(Route route, CamelContext camelContext) {
            this.route = route;
            if (route != null) {
                this.routeId = route.getRouteId();
            }
            this.uowFactory = camelContext.adapt(ExtendedCamelContext.class).getUnitOfWorkFactory();
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
                ExtendedExchange ee = (ExtendedExchange) exchange;
                ee.setFromRouteId(routeId);
            }

            // only return UnitOfWork if we created a new as then its us that handle the lifecycle to done the created UoW
            UnitOfWork created = null;

            if (exchange.getUnitOfWork() == null) {
                // If there is no existing UoW, then we should start one and
                // terminate it once processing is completed for the exchange.
                created = createUnitOfWork(exchange);
                ExtendedExchange ee = (ExtendedExchange) exchange;
                ee.setUnitOfWork(created);
                created.start();
            }

            // for any exchange we should push/pop route context so we can keep track of which route we are routing
            if (route != null) {
                UnitOfWork existing = exchange.getUnitOfWork();
                if (existing != null) {
                    existing.pushRoute(route);
                }
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
                return exchange.getContext().adapt(ExtendedCamelContext.class).getUnitOfWorkFactory().createUnitOfWork(exchange);
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
                List<MessageHistory> list = exchange.getProperty(Exchange.MESSAGE_HISTORY, List.class);
                if (list == null) {
                    list = new LinkedList<>();
                    exchange.setProperty(Exchange.MESSAGE_HISTORY, list);
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
    public static class NodeHistoryAdvice implements CamelInternalProcessorAdvice {

        private final String id;
        private final String label;

        public NodeHistoryAdvice(NamedNode definition) {
            this.id = definition.getId();
            this.label = definition.getLabel();
        }

        @Override
        public String before(Exchange exchange) throws Exception {
            ExtendedExchange ee = (ExtendedExchange) exchange;
            ee.setHistoryNodeId(id);
            ee.setHistoryNodeLabel(label);
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            ExtendedExchange ee = (ExtendedExchange) exchange;
            ee.setHistoryNodeId(null);
            ee.setHistoryNodeLabel(null);
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
            // check if body is already cached
            Object body = exchange.getIn().getBody();
            if (body == null) {
                return null;
            } else if (body instanceof StreamCache) {
                StreamCache sc = (StreamCache) body;
                // reset so the cache is ready to be used before processing
                sc.reset();
                return sc;
            }
            // cache the body and if we could do that replace it as the new body
            StreamCache sc = strategy.cache(exchange);
            if (sc != null) {
                exchange.getIn().setBody(sc);
            }
            return sc;
        }

        @Override
        public void after(Exchange exchange, StreamCache sc) throws Exception {
            Object body = exchange.getMessage().getBody();
            if (body instanceof StreamCache) {
                // reset so the cache is ready to be reused after processing
                ((StreamCache) body).reset();
            }
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
    public static class DelayerAdvice implements CamelInternalProcessorAdvice {

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
    public static class TracingAdvice implements CamelInternalProcessorAdvice {

        private final Tracer tracer;
        private final NamedNode processorDefinition;
        private final NamedRoute routeDefinition;
        private final Synchronization tracingAfterRoute;
        private boolean added;

        public TracingAdvice(Tracer tracer, NamedNode processorDefinition, NamedRoute routeDefinition, boolean first) {
            this.tracer = tracer;
            this.processorDefinition = processorDefinition;
            this.routeDefinition = routeDefinition;
            this.tracingAfterRoute = routeDefinition != null ? new TracingAfterRoute(tracer, routeDefinition.getRouteId()) : null;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            if (!added && tracingAfterRoute != null) {
                // add before route and after route tracing but only once per route, so check if there is already an existing
                boolean contains = exchange.getUnitOfWork().containsSynchronization(tracingAfterRoute);
                if (!contains) {
                    added = true;
                    tracer.traceBeforeRoute(routeDefinition, exchange);
                    exchange.adapt(ExtendedExchange.class).addOnCompletion(tracingAfterRoute);
                }
            }

            tracer.traceBeforeNode(processorDefinition, exchange);
            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            tracer.traceAfterNode(processorDefinition, exchange);
        }

        @Override
        public boolean hasState() {
            return false;
        }

        private static final class TracingAfterRoute extends SynchronizationAdapter {

            private final Tracer tracer;
            private final String routeId;

            private TracingAfterRoute(Tracer tracer, String routeId) {
                this.tracer = tracer;
                this.routeId = routeId;
            }

            @Override
            public void onAfterRoute(Route route, Exchange exchange) {
                if (routeId.equals(route.getId())) {
                    tracer.traceAfterRoute(route, exchange);
                }
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
        if (advice instanceof CamelInternalProcessorAdviceWrapper) {
            return ((CamelInternalProcessorAdviceWrapper) advice).unwrap();
        } else {
            return advice;
        }
    }

    static class CamelInternalProcessorAdviceWrapper<T> implements CamelInternalProcessorAdvice<T>, Ordered {

        final InstrumentationProcessor<T> instrumentationProcessor;

        public CamelInternalProcessorAdviceWrapper(InstrumentationProcessor<T> instrumentationProcessor) {
            this.instrumentationProcessor = instrumentationProcessor;
        }

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

}
