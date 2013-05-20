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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.PerformanceCounter;
import org.apache.camel.management.DelegatePerformanceCounter;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.processor.interceptor.DefaultBacklogTracerEventMessage;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal {@link Processor} that Camel routing engine used during routing for cross cutting functionality such as:
 * <ul>
 *     <li>Keeping track which route currently is being routed</li>
 *     <li>Gather JMX performance statics</li>
 *     <li>Tracing</li>
 *     <li>Execute {@link RoutePolicy}</li>
 * </ul>
 * ... and much more.
 * <p/>
 * This implementation executes this cross cutting functionality as a {@link CamelInternalProcessorTask} task
 * by executing the {@link CamelInternalProcessorTask#before(org.apache.camel.Exchange)} and
 * {@link CamelInternalProcessorTask#after(org.apache.camel.Exchange, Object)} callbacks in correct order during routing.
 * This reduces number of stack frames needed during routing, and reduce the number of lines in stacktraces, as well
 * makes debugging the routing engine easier for end users.
 */
public final class CamelInternalProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CamelInternalProcessor.class);
    private final List<CamelInternalProcessorTask> tasks = new ArrayList<CamelInternalProcessorTask>();

    public CamelInternalProcessor() {
    }

    public CamelInternalProcessor(Processor processor) {
        super(processor);
    }

    public CamelInternalProcessor(AsyncProcessor processor) {
        super(processor);
    }

    public void addTask(CamelInternalProcessorTask task) {
        tasks.add(task);
    }

    public void addFirstTask(CamelInternalProcessorTask task) {
        tasks.add(0, task);
    }

    public <T> T getTask(Class<T> type) {
        for (CamelInternalProcessorTask task : tasks) {
            if (type.isInstance(task)) {
                return type.cast(task);
            }
        }
        return null;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (processor == null) {
            // no processor then we are done
            callback.done(true);
            return true;
        }

        final List<Object> states = new ArrayList<Object>(tasks.size());
        for (CamelInternalProcessorTask task : tasks) {
            try {
                Object state = task.before(exchange);
                states.add(state);
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        // create internal callback which will execute the tasks in reverse order when done
        callback = new InternalCallback(states, exchange, callback);

        if (exchange.isTransacted()) {
            // must be synchronized for transacted exchanges
            LOG.trace("Transacted Exchange must be routed synchronously for exchangeId: {} -> {}", exchange.getExchangeId(), exchange);
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        } else {
            final UnitOfWork uow = exchange.getUnitOfWork();

            // allow unit of work to wrap callback in case it need to do some special work
            // for example the MDCUnitOfWork
            AsyncCallback async = callback;
            if (uow != null) {
                async = uow.beforeProcess(processor, exchange, callback);
            }

            boolean sync = processor.process(exchange, async);

            // execute any after processor work (in current thread, not in the callback)
            if (uow != null) {
                uow.afterProcess(processor, exchange, callback, sync);
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange processed and is continued routed {} for exchangeId: {} -> {}",
                        new Object[]{sync ? "synchronously" : "asynchronously", exchange.getExchangeId(), exchange});
            }
            return sync;
        }
    }

    private final class InternalCallback implements AsyncCallback {

        private final List<Object> states;
        private final Exchange exchange;
        private final AsyncCallback callback;

        private InternalCallback(List<Object> states, Exchange exchange, AsyncCallback callback) {
            this.states = states;
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            // we should call after in reverse order
            try {
                for (int i = tasks.size() - 1; i >= 0; i--) {
                    CamelInternalProcessorTask task = tasks.get(i);
                    Object state = states.get(i);
                    try {
                        task.after(exchange, state);
                    } catch (Exception e) {
                        exchange.setException(e);
                        // allow all tasks to complete even if there was an exception
                    }
                }
            } finally {
                // callback must be called
                callback.done(doneSync);
            }
        }
    }

    public static class InstrumentationTask implements CamelInternalProcessorTask<StopWatch> {

        private PerformanceCounter counter;
        private String type;

        public void setCounter(Object counter) {
            ManagedPerformanceCounter mpc = null;
            if (counter instanceof ManagedPerformanceCounter) {
                mpc = (ManagedPerformanceCounter) counter;
            }

            if (this.counter instanceof DelegatePerformanceCounter) {
                ((DelegatePerformanceCounter) this.counter).setCounter(mpc);
            } else if (mpc != null) {
                this.counter = mpc;
            } else if (counter instanceof PerformanceCounter) {
                this.counter = (PerformanceCounter) counter;
            }
        }

        protected void recordTime(Exchange exchange, long duration) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("{}Recording duration: {} millis for exchange: {}", new Object[]{type != null ? type + ": " : "", duration, exchange});
            }

            if (!exchange.isFailed() && exchange.getException() == null) {
                counter.completedExchange(exchange, duration);
            } else {
                counter.failedExchange(exchange);
            }
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public StopWatch before(Exchange exchange) throws Exception {
            // only record time if stats is enabled
            return (counter != null && counter.isStatisticsEnabled()) ? new StopWatch() : null;
        }

        @Override
        public void after(Exchange exchange, StopWatch watch) throws Exception {
            // record end time
            if (watch != null) {
                recordTime(exchange, watch.stop());
            }
        }
    }

    public static class RouteContextTask implements CamelInternalProcessorTask<UnitOfWork> {

        private final RouteContext routeContext;

        public RouteContextTask(RouteContext routeContext) {
            this.routeContext = routeContext;
        }

        @Override
        public UnitOfWork before(Exchange exchange) throws Exception {
            // push the current route context
            final UnitOfWork unitOfWork = exchange.getUnitOfWork();
            if (unitOfWork != null) {
                unitOfWork.pushRouteContext(routeContext);
            }
            return unitOfWork;
        }

        @Override
        public void after(Exchange exchange, UnitOfWork unitOfWork) throws Exception {
            if (unitOfWork != null) {
                unitOfWork.popRouteContext();
            }
        }
    }

    public static class RouteInflightRepositoryTask implements CamelInternalProcessorTask {

        private final InflightRepository inflightRepository;
        private final String id;

        public RouteInflightRepositoryTask(InflightRepository inflightRepository, String id) {
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
    }

    public static class RoutePolicyTask implements CamelInternalProcessorTask {

        private final List<RoutePolicy> routePolicies;
        private Route route;

        public RoutePolicyTask(List<RoutePolicy> routePolicies) {
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
        protected boolean isRoutePolicyRunAllowed(RoutePolicy policy) {
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
                    LOG.warn("Error occurred during onExchangeBegin on RoutePolicy: " + policy
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
                    LOG.warn("Error occurred during onExchangeDone on RoutePolicy: " + policy
                            + ". This exception will be ignored", e);
                }
            }
        }

        private static boolean isCamelStopping(CamelContext context) {
            if (context instanceof StatefulService) {
                StatefulService ss = (StatefulService) context;
                return ss.isStopping() || ss.isStopped();
            }
            return false;
        }
    }

    public static final class BacklogTracerTask implements CamelInternalProcessorTask {

        private final Queue<DefaultBacklogTracerEventMessage> queue;
        private final BacklogTracer backlogTracer;
        private final ProcessorDefinition<?> processorDefinition;
        private final ProcessorDefinition<?> routeDefinition;
        private final boolean first;

        public BacklogTracerTask(Queue<DefaultBacklogTracerEventMessage> queue, BacklogTracer backlogTracer,
                                 ProcessorDefinition<?> processorDefinition, ProcessorDefinition<?> routeDefinition, boolean first) {
            this.queue = queue;
            this.backlogTracer = backlogTracer;
            this.processorDefinition = processorDefinition;
            this.routeDefinition = routeDefinition;
            this.first = first;
        }

        @Override
        public Object before(Exchange exchange) throws Exception {
            if (backlogTracer.shouldTrace(processorDefinition, exchange)) {
                // ensure there is space on the queue
                int drain = queue.size() - backlogTracer.getBacklogSize();
                // and we need room for ourselves and possible also a first pseudo message as well
                drain += first ? 2 : 1;
                if (drain > 0) {
                    for (int i = 0; i < drain; i++) {
                        queue.poll();
                    }
                }

                Date timestamp = new Date();
                String toNode = processorDefinition.getId();
                String exchangeId = exchange.getExchangeId();
                String messageAsXml = MessageHelper.dumpAsXml(exchange.getIn(), true, 4,
                        backlogTracer.isBodyIncludeStreams(), backlogTracer.isBodyIncludeFiles(), backlogTracer.getBodyMaxChars());

                // if first we should add a pseudo trace message as well, so we have a starting message (eg from the route)
                String routeId = routeDefinition.getId();
                if (first) {
                    Date created = exchange.getProperty(Exchange.CREATED_TIMESTAMP, timestamp, Date.class);
                    DefaultBacklogTracerEventMessage pseudo = new DefaultBacklogTracerEventMessage(backlogTracer.incrementTraceCounter(), created, routeId, null, exchangeId, messageAsXml);
                    queue.add(pseudo);
                }
                DefaultBacklogTracerEventMessage event = new DefaultBacklogTracerEventMessage(backlogTracer.incrementTraceCounter(), timestamp, routeId, toNode, exchangeId, messageAsXml);
                queue.add(event);
            }

            return null;
        }

        @Override
        public void after(Exchange exchange, Object data) throws Exception {
            // noop
        }
    }

}
